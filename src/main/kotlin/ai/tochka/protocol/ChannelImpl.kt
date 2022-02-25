/*
 * MIT License
 *
 * Copyright (c) 2020 Alexander Shilov (ashlanderr) <aleksandr.schilow2012@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ai.tochka.protocol

import org.slf4j.LoggerFactory
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.net.SocketException
import java.nio.charset.Charset
import java.time.Duration
import java.util.*
import java.util.concurrent.*

internal class ChannelImpl(
    val socket: Socket,
    private val readTimeout: Duration,
    private val registry: MessageRegistry,
    private val executor: Executor
) : Channel {
    private data class AnswerHandler(
        val command: String,
        val handler: CompletableFuture<Message>
    )

    private val logger = LoggerFactory.getLogger(ChannelImpl::class.java)

    private val sync = Any()

    private val input = DataInputStream(socket.getInputStream())
    private val output = DataOutputStream(socket.getOutputStream())

    private val answerHandlers = ConcurrentHashMap<String, AnswerHandler>()

    private val commandHandlers = ConcurrentHashMap<String, CommandHandlerCallback>().also {
        it[CommandId.ERROR] = this::errorHandler
        it[CommandId.CLOSE_CONNECTION] = this::closeConnectionHandler
    }

    private val disconnectEmitter = EventEmitter<Socket>(logger, executor)

    private val serviceFactory = ServiceFactory(this, registry)
    private val listener = CommandListener(this, registry)

    override fun <T : Any> service(clazz: Class<T>): T {
        return serviceFactory.get(clazz)
    }

    override fun <T : Any> handler(handler: T, clazz: Class<T>) {
        listener.register(handler, clazz)
    }

    override fun onDisconnect(block: (Socket) -> Unit): Disposable {
        return disconnectEmitter.add(block)
    }

    override fun sendMessage(message: Message) {
        val commandStr = registry.serialize(message)
        send(commandStr)
    }

    override fun waitForAnswer(command: String, id: String): Message {
        val future =
            CompletableFuture<Message>()
        answerHandlers[id] = AnswerHandler(command, future)
        try {
            return future.get(readTimeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (ex: ExecutionException) {
            throw ex.cause ?: ex
        } finally {
            answerHandlers.remove(id)
        }
    }

    override fun registerHandler(command: String, handler: CommandHandlerCallback) {
        if (commandHandlers.containsKey(command))
            throw RuntimeException("Command handler for '$command' already registered")
        commandHandlers[command] = handler
        logger.info("Registered command handler for '$command'")
    }

    fun init() {
        socket.soTimeout = readTimeout.toMillis().toInt()
        validateSignature()
        sendProtocolCompatibility()
        logger.info("Protocol initialization completed")
    }

    fun run() {
        try {
            socket.soTimeout = 0
            loop()
        } finally {
            notifyDisconnectListeners()
        }
    }

    override fun close() {
        doClose(SocketException("Connection closed"))
    }

    private fun doClose(reason: Throwable) {
        try {
            socket.close()
        } catch (ex: Throwable) {
            logger.error("Failed to close connection", ex)
        }
        answerHandlers.values.forEach {
            try {
                it.handler.completeExceptionally(reason)
            } catch (ex: Throwable) {
                logger.error("Failed to interrupt answer handler", ex)
            }
        }
    }

    override fun close(group: Int, code: String?, description: String) {
        logger.info("Closing connection, code = $code, description = $description")
        try {
            val id = UUID.randomUUID().toString()
            sendMessage(
                Message(
                    id = id,
                    type = MessageType.COMMAND,
                    command = CommandId.CLOSE_CONNECTION,
                    content = CloseConnectionCommand(
                        group = group,
                        code = code,
                        description = description
                    )
                )
            )
            waitForAnswer(
                command = CommandId.CLOSE_CONNECTION,
                id = id
            )
        } catch (ex: Throwable) {
            logger.error("Failed to send [CloseConnection] command", ex)
        }
        close()
    }

    private fun notifyDisconnectListeners() {
        disconnectEmitter.fire(socket)
    }

    private fun validateSignature() {
        logger.info("Validating signature...")

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
        sendMessage(
            Message(
                id = UUID.randomUUID().toString(),
                type = MessageType.COMMAND,
                command = CommandId.PROTOCOL_COMPATIBLE
            )
        )
        receiveMessage(
            command = CommandId.PROTOCOL_COMPATIBLE
        )
    }

    private fun send(message: String) = synchronized(sync) {
        logger.info("Sending message: [${message.trimWithEllipsis(400)}]")
        val messageBytes = message.toByteArray()
        output.writeInt(messageBytes.size)
        output.write(messageBytes)
        output.flush()
    }

    private fun loop() {
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

    private fun receiveMessage(
        command: String,
        expectedId: String? = null
    ): Message {
        val (answerMessage, error) = receiveMessage()
        if (error != null) throw error

        if (answerMessage.command != command) {
            throw IllegalStateException("Received message with unexpected command field" +
                    "($command != ${answerMessage.command}, id = $expectedId)")
        }

        if (expectedId != null && answerMessage.id != expectedId) {
            throw IllegalStateException("Received message with unexpected id field ($expectedId != ${answerMessage.id})")
        }

        return answerMessage
    }

    @Suppress("UNCHECKED_CAST")
    private fun receiveMessage(): Pair<Message, Throwable?> {
        val str = receive()
        return registry.deserialize(str)
    }

    private fun receive(): String {
        val len = input.readInt()
        val bytes = ByteArray(len)
        var offset = 0
        while (offset < len) {
            offset += input.read(bytes, offset, len - offset)
        }
        val message = bytes.toString(Charset.defaultCharset())
        logger.info("Received message: [${message.trimWithEllipsis(400)}]")
        return message
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

        executor.execute {
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
            handler.handler.completeExceptionally(RuntimeException(error.description))
        } catch (ex: Throwable) {
            logger.error("Error in answer handler", ex)
        }
    }

    private fun closeConnectionHandler(message: Message) {
        val content = message.content as CloseConnectionCommand
        logger.info("Received connection close command, code = ${content.code}, description = ${content.description}")
        try {
            sendMessage(
                Message(
                    id = message.id,
                    type = MessageType.ANSWER,
                    command = message.command
                )
            )
        } catch (ex: Throwable) {
            logger.error("Failed to send [CloseConnection] answer", ex)
        }
        doClose(ConnectionClosedException(content.group, content.code, content.description))
    }
}