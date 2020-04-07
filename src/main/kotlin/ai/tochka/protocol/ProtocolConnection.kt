package ai.tochka.protocol

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.isKotlinClass
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.SocketException
import java.nio.charset.Charset
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.*
import kotlin.concurrent.thread
import kotlin.math.min
import kotlin.system.exitProcess

val PROTOCOL_SIGNATURE: UUID = UUID.fromString("fea6b958-dafb-4f5c-b620-fe0aafbd47e2")

@Suppress("unused")
object CommandType {
    const val PROTOCOL_COMPATIBLE = "173cbbeb-1d81-4e01-bf3c-5d06f9c878c3"
    const val UNKNOWN = "4aef29d6-5b1a-4323-8655-ef0d4f1bb79d"
    const val ERROR = "b18b98cc-b026-4bfe-8e33-e7afebfbe78b"
    const val WEB_AUTHORIZATION = "7bd4b1c1-3f8f-4bcf-b941-5a0f1e7b449f"
}

enum class MessageType(val value: Int) {
    UNKNOWN(0),
    COMMAND(1),
    ANSWER(2),
    EVENT(3),
}

enum class MessagePriority(val value: Int) {
    HIGH(0),
    NORMAL(1),
    LOW(2),
}

enum class MessageStatus(val value: Int) {
    UNKNOWN(0),
    SUCCESS(1),
    FAILED(2),
    ERROR(3),
}

data class MessageError(
    val group: Int,
    val code: String,
    val description: String
)

data class Error(
    val commandId: String,
    val messageId: String,
    val code: String,
    val description: String
)

data class WebAuthorizationCommand(
    val password: String
)

data class Message(
    val id: String? = null,
    val type: MessageType,
    val command: String,
    val content: Any? = null,
    val status: MessageStatus = MessageStatus.SUCCESS,
    val priority: MessagePriority = MessagePriority.NORMAL,
    val tags: List<Long>? = null
)

private data class AnswerHandler(
    val command: String,
    val handler: CompletableFuture<Message>
)

data class MessageWrapper<T>(
    val id: String,
    val command: String,
    val flags: Long,
    val content: T?,

    @JsonDeserialize(using = UnsignedLongListDeserializer::class)
    @JsonSerialize(using = UnsignedLongListSerializer::class)
    val tags: List<Long>?
)

data class ContentType(
    val messageType: MessageType,
    val commandType: String
)

typealias CommandHandlerCallback = (message: Message) -> Unit

interface AuthHolder {
    val auth: Long
}

interface ProtocolConnection : AuthHolder, Closeable {
    companion object {
        @JvmStatic
        fun create(
            properties: ProtocolProperties,
            socketFactory: ProtocolSocketFactory,
            taskExecutor: Executor,
            objectMapper: ObjectMapper
        ): ProtocolConnection {
            return ProtocolConnectionImpl(
                properties,
                socketFactory,
                taskExecutor,
                objectMapper
            )
        }
    }

    fun start(): CompletableFuture<Void>

    fun registerContentType(type: MessageType, command: String, clazz: Class<*>)
    fun registerCommandHandler(command: String, handler: CommandHandlerCallback)
    fun sendMessage(message: Message): String
    fun waitForAnswer(command: String, id: String): Message
}

private class ProtocolConnectionImpl(
    private val properties: ProtocolProperties,
    private val socketFactory: ProtocolSocketFactory,
    private val taskExecutor: Executor,
    objectMapper: ObjectMapper
) : ProtocolConnection {
    private val enumSerializerModule = SimpleModule()

    private val objectMapper = objectMapper.copy()
        .registerKotlinModule()

    private val startFuture = CompletableFuture<Void>()

    private val sync = Any()
    private val logger = LoggerFactory.getLogger(ProtocolConnectionImpl::class.java)
    private lateinit var receiveThread: Thread

    @Volatile
    private var connection: Connection? = null

    @Volatile
    private var running = true

    private val answerHandlers = ConcurrentHashMap<String, AnswerHandler>()

    private val commandHandlers = ConcurrentHashMap<String, CommandHandlerCallback>().also {
        it[CommandType.ERROR] = this::errorHandler
    }

    private val contentTypes = ConcurrentHashMap(mapOf(
        ContentType(MessageType.COMMAND, CommandType.PROTOCOL_COMPATIBLE) to Any::class.java,
        ContentType(MessageType.COMMAND, CommandType.ERROR) to Error::class.java
    ))

    override var auth: Long = 0
        private set

    private data class Connection(
        val socket: ProtocolSocket,
        val input: DataInputStream,
        val output: DataOutputStream
    )

    override fun start(): CompletableFuture<Void> {
        objectMapper.registerModule(SimpleModule().apply {
            addDeserializer(OffsetDateTime::class.java, UnixOffsetDateTimeDeserializer())
            addSerializer(OffsetDateTime::class.java, UnixOffsetDateTimeSerializer())
        })
        objectMapper.registerModule(enumSerializerModule)
        receiveThread = startReceiveThread()
        return startFuture
    }

    override fun close() {
        logger.info("Stopping connection thread...")
        running = false
        closeConnection()
    }

    override fun registerContentType(type: MessageType, command: String, clazz: Class<*>) {
        val contentType = ContentType(type, command)
        if (contentTypes.containsKey(contentType)) throw RuntimeException("Content type '$contentType' already registered")
        contentTypes[contentType] = clazz
        registerOrdinalEnumFields(clazz)
        logger.info("Registered content type $contentType = [${clazz.name}]")
    }

    override fun registerCommandHandler(command: String, handler: CommandHandlerCallback) {
        if (commandHandlers.containsKey(command)) throw RuntimeException("Command handler for '$command' already registered")
        commandHandlers[command] = handler
        logger.info("Registered command handler for '$command'")
    }

    override fun waitForAnswer(command: String, id: String): Message {
        val future = CompletableFuture<Message>()
        answerHandlers[id] = AnswerHandler(command, future)
        try {
            return future.get(properties.readTimeout, TimeUnit.MILLISECONDS)
        } catch (ex: ExecutionException) {
            throw ex.cause ?: ex
        } finally {
            answerHandlers.remove(id)
        }
    }

    override fun sendMessage(message: Message): String {
        var flags = 0
        // 0 byte
        flags = flags or (message.type.value shl 0)
        flags = flags or (message.status.value shl 3)
        flags = flags or (message.priority.value shl 6)
        // 1 byte
        if (message.tags == null) {
            flags = flags or (1 shl 11) // tagIsEmpty
        }
        flags = flags or (1 shl 12) // maxTimeLifeIsEmpty
        if (message.content == null) {
            flags = flags or (1 shl 13) // contentIsEmpty
        }
        // 2 byte - empty
        // 3 byte
        flags = flags or (1 shl 24) // reserved, must be 1
        flags = flags or (1 shl 31) // flags2IsEmpty

        val id = message.id ?: UUID.randomUUID().toString()
        val commandStr = objectMapper.writeValueAsString(MessageWrapper(
            id = id,
            command = message.command,
            flags = flags.toLong() and 0xFFFFFFFF,
            content = message.content,
            tags = message.tags
        ))
        send(commandStr)
        return id
    }

    @Suppress("UNCHECKED_CAST")
    private fun registerOrdinalEnumFields(clazz: Class<*>) {
        for (field in clazz.declaredFields) {
            val type = field.type as Class<Any>
            if (type.isEnum) {
                enumSerializerModule.addSerializer(type, OrdinalEnumSerializer(type))
                enumSerializerModule.addDeserializer(type, OrdinalEnumDeserializer(type))
                logger.info("Registered ordinal enum serializer for type [${type.name}]")
            }
            if (type.isKotlinClass() && type.kotlin.isData) {
                registerOrdinalEnumFields(type)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun receiveMessage(): Pair<Message, Throwable?> {
        val str = receive()
        val message = objectMapper.readValue(str, MessageWrapper::class.java) as MessageWrapper<Any?>
        val flags = message.flags.toInt()
        val type = messageTypeOf((flags shr 0) and 0x07)
        val status = messageStatusOf((flags shr 3) and 0x07)
        val priority = messagePriorityOf((flags shr 6) and 0x03)

        if (type == MessageType.UNKNOWN) throw IllegalStateException("Unknown message received")

        val contentType =  ContentType(type, message.command)
        var contentClazz = contentTypes[contentType]
        if (contentClazz == null) {
            logger.warn("Unknown content type '$contentType'")
            contentClazz = Any::class.java
        }

        val (content, error) = if (type == MessageType.ANSWER) {
            when (status) {
                MessageStatus.UNKNOWN -> throw IllegalStateException("Unknown message status")
                MessageStatus.SUCCESS -> {
                    // todo не конвертировать в строку лишний раз
                    parseContent(message, contentClazz)
                }
                MessageStatus.FAILED, MessageStatus.ERROR -> {
                    val content = objectMapper.readValue(objectMapper.writeValueAsString(message.content), MessageError::class.java)
                    Pair(null, ProtocolAnswerException(content.group, content.code, content.description))
                }
            }
        } else {
            // todo не конвертировать в строку лишний раз
            parseContent(message, contentClazz)
        }

        return Pair(
            Message(
                message.id,
                type,
                message.command,
                content,
                MessageStatus.SUCCESS,
                priority,
                message.tags
            ),
            error
        )
    }

    private fun parseContent(message: MessageWrapper<Any?>, contentClazz: Class<out Any>?): Pair<Any?, Throwable?> {
        return try {
            Pair(objectMapper.readValue(objectMapper.writeValueAsString(message.content), contentClazz), null)
        } catch (ex: Throwable) {
            Pair(null, ex)
        }
    }

    private fun send(message: String) = synchronized(sync) {
        val output = connection?.output ?: throw SocketException("Connection not available")
        logger.info("Sending message: $message")
        val messageBytes = message.toByteArray()
        output.writeInt(messageBytes.size)
        output.write(messageBytes)
        output.flush()
    }

    private fun receive(): String {
        val input = connection?.input ?: throw SocketException("Connection not available")
        val len = input.readInt()
        val bytes = ByteArray(len)
        var offset = 0
        while (offset < len) {
            offset += input.read(bytes, offset, len - offset)
        }
        val message = bytes.toString(Charset.defaultCharset())
        logger.info("Received message: $message")
        return message
    }

    private fun receiveMessage(
        command: String,
        expectedId: String? = null
    ): Message {
        val (answerMessage, error) = receiveMessage()
        if (error != null) throw error

        if (answerMessage.command != command) {
            throw IllegalStateException("Received message with unexpected command field" +
                "($command != ${answerMessage.command}, id = $expectedId")
        }

        if (expectedId != null && answerMessage.id != expectedId) {
            throw IllegalStateException("Received message with unexpected id field ($expectedId != ${answerMessage.id})")
        }

        return answerMessage
    }

    private fun validateSignature() {
        logger.info("Validating signature...")
        val (_, input, output) = connection ?: throw SocketException("Connection not available")

        output.writeLong(PROTOCOL_SIGNATURE.mostSignificantBits)
        output.writeLong(PROTOCOL_SIGNATURE.leastSignificantBits)
        output.flush()

        val mostBits = input.readLong()
        val leastBits = input.readLong()
        val receivedSignature = UUID(mostBits, leastBits)

        if (receivedSignature != PROTOCOL_SIGNATURE) {
            throw RuntimeException("Protocol signatures didn't match ($PROTOCOL_SIGNATURE != $receivedSignature)")
        }
    }

    private fun sendProtocolCompatibility() {
        logger.info("Sending ProtocolCompatible message...")
        sendMessage(Message(
            type = MessageType.COMMAND,
            command = CommandType.PROTOCOL_COMPATIBLE
        ))
        receiveMessage(
            command = CommandType.PROTOCOL_COMPATIBLE
        )
    }

    private fun sendAuthorization() {
        val password = properties.password
        if (password == null) {
            logger.info("Password not set, skipping authorization")
            return
        }

        logger.info("Sending WebAuthorization message...")
        sendMessage(Message(
            type = MessageType.COMMAND,
            command = CommandType.WEB_AUTHORIZATION,
            content = WebAuthorizationCommand(
                password = password
            )
        ))
        val message = receiveMessage(
            command = CommandType.WEB_AUTHORIZATION
        )
        auth = message.tags?.get(0) ?: throw RuntimeException("Failed service authentication")
    }

    private fun messageTypeOf(value: Int): MessageType {
        return when (value) {
            MessageType.UNKNOWN.value -> MessageType.UNKNOWN
            MessageType.COMMAND.value -> MessageType.COMMAND
            MessageType.ANSWER.value -> MessageType.ANSWER
            MessageType.EVENT.value -> MessageType.EVENT
            else -> throw IllegalStateException("Unknown message type $value")
        }
    }

    private fun messageStatusOf(value: Int): MessageStatus {
        return when (value) {
            MessageStatus.UNKNOWN.value -> MessageStatus.UNKNOWN
            MessageStatus.SUCCESS.value -> MessageStatus.SUCCESS
            MessageStatus.FAILED.value -> MessageStatus.FAILED
            MessageStatus.ERROR.value -> MessageStatus.ERROR
            else -> throw IllegalStateException("Unknown message status $value")
        }
    }

    private fun messagePriorityOf(value: Int): MessagePriority {
        return when (value) {
            MessagePriority.HIGH.value -> MessagePriority.HIGH
            MessagePriority.NORMAL.value -> MessagePriority.NORMAL
            MessagePriority.LOW.value -> MessagePriority.LOW
            else -> throw IllegalStateException("Unknown message priority $value")
        }
    }

    private fun startReceiveThread() = thread(name = "tochka-protocol-thread") {
        logger.info("Connection thread started")
        var backoff = properties.initialConnectTimeout

        while (running) {
            try {
                connect()
                connection?.socket?.soTimeout = properties.readTimeout.toInt()
                validateSignature()
                sendProtocolCompatibility()
                sendAuthorization()
                logger.info("Protocol initialization completed")

                backoff = properties.initialConnectTimeout
                connection?.socket?.soTimeout = 0
                startFuture.complete(null)
                connectionLoop()
            } catch (ex: IOException) {
                logger.error("Connection failed, cause = $ex, backoff = $backoff")
                Thread.sleep(backoff)
                backoff = min(properties.maxConnectTimeout, backoff * 2)
            } catch (ex: Throwable) {
                logger.error("Fatal error in connection thread", ex)
                running = false
                exitProcess(1)
            } finally {
                closeConnection()
            }
        }

        logger.info("Connection thread exiting")
    }

    private fun closeConnection() = synchronized(sync) {
        try {
            connection?.socket?.close()
        } catch (ex: Throwable) {
            logger.error("Error while closing connection", ex)
        } finally {
            connection = null
        }
    }

    private fun connectionLoop() {
        while (true) {
            val (message, error) = receiveMessage()
            if (message.type == MessageType.ANSWER) {
                handleAnswer(message, error)
            } else if (message.type == MessageType.COMMAND || message.type == MessageType.EVENT) {
                handleCommand(message, error)
            } else {
                logger.error("Cannot handle message with type ${message.type}", error)
            }
        }
    }

    private fun connect() {
        val socket = socketFactory.connect()
        val input = DataInputStream(socket.inputStream)
        val output = DataOutputStream(socket.outputStream)

        logger.info("Connected")

        this.connection = Connection(socket, input, output)
    }

    private fun handleAnswer(message: Message, error: Throwable?) {
        try {
            val handler = answerHandlers[message.id] ?: return
            if (error != null) {
                handler.handler.completeExceptionally(error)
            } else {
                handler.handler.complete(message)
            }
        } catch (ex: Throwable) {
            logger.error("Error in answer handler", ex)
        }
    }

    private fun handleCommand(message: Message, error: Throwable?) {
        if (error != null) {
            logger.error("Error receiving message", error)
            return
        }

        taskExecutor.execute {
            try {
                val handler = commandHandlers[message.command]
                if (handler != null) {
                    handler(message)
                } else {
                    logger.warn("Unknown command type '${message.type}'")
                }
            } catch (ex: Throwable) {
                logger.error("Error in command handler", ex)
            }
        }
    }

    private fun errorHandler(message: Message) {
        try {
            val error = message.content as Error
            val handler = answerHandlers[error.messageId] ?: return
            // todo better exception
            handler.handler.completeExceptionally(RuntimeException(error.description))
        } catch (ex: Throwable) {
            logger.error("Error in answer handler", ex)
        }
    }
}