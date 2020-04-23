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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory
import java.lang.reflect.Modifier
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class MessageRegistry(objectMapper: ObjectMapper) {
    val objectMapper: ObjectMapper = objectMapper.copy()
        .registerKotlinModule()
        .registerModule(SimpleModule().apply {
            addDeserializer(
                OffsetDateTime::class.java,
                UnixOffsetDateTimeDeserializer()
            )
            addSerializer(
                OffsetDateTime::class.java,
                UnixOffsetDateTimeSerializer()
            )
        })

    private val logger = LoggerFactory.getLogger(MessageRegistry::class.java)

    private val contentTypes = ConcurrentHashMap(
        mapOf(
            ContentType(MessageType.COMMAND, CommandId.PROTOCOL_COMPATIBLE) to Any::class.java,
            ContentType(MessageType.COMMAND, CommandId.ERROR) to Error::class.java,
            ContentType(MessageType.COMMAND, CommandId.CLOSE_CONNECTION) to CloseConnectionCommand::class.java
        )
    )

    private val processedEnumClasses = Collections.newSetFromMap(ConcurrentHashMap<Class<*>, Boolean>())

    fun serialize(message: Message): String {
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

        val id = message.id
        return objectMapper.writeValueAsString(
            MessageWrapper(
                id = id,
                command = message.command,
                flags = flags.toLong() and 0xFFFFFFFF,
                content = message.content,
                tags = message.tags
            )
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun deserialize(str: String): Pair<Message, Throwable?> {
        val message = objectMapper.readValue(str, MessageWrapper::class.java) as MessageWrapper<Any?>
        val flags = message.flags.toInt()
        val type = messageTypeOf((flags shr 0) and 0x07)
        val status = messageStatusOf((flags shr 3) and 0x07)
        val priority = messagePriorityOf((flags shr 6) and 0x03)

        if (type == MessageType.UNKNOWN) throw IllegalStateException("Unknown message received")

        val contentType = ContentType(type, message.command)
        var contentClazz = contentTypes[contentType]
        if (contentClazz == null) {
            logger.warn("Unknown content type '$contentType'")
            contentClazz = Any::class.java
        }

        val (content, error) = if (type == MessageType.ANSWER) {
            when (status) {
                MessageStatus.UNKNOWN -> throw IllegalStateException("Unknown message status")
                MessageStatus.SUCCESS -> {
                    parseContent(message, contentClazz)
                }
                MessageStatus.FAILED, MessageStatus.ERROR -> {
                    val content = objectMapper.readValue(
                        objectMapper.writeValueAsString(message.content),
                        MessageError::class.java
                    )
                    Pair(
                        null,
                        ProtocolAnswerException(
                            content.group,
                            content.code,
                            content.description
                        )
                    )
                }
            }
        } else {
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

    fun registerContentType(type: MessageType, command: String, clazz: Class<*>) {
        val contentType = ContentType(type, command)
        if (contentTypes.containsKey(contentType)) return
        contentTypes[contentType] = clazz

        val enumModule = SimpleModule()
        registerOrdinalEnumFields(clazz, enumModule)
        objectMapper.registerModule(enumModule)

        logger.info("Registered content type $contentType = [${clazz.name}]")
    }

    @Suppress("UNCHECKED_CAST")
    private fun registerOrdinalEnumFields(clazz: Class<*>, module: SimpleModule) {
        if (clazz in processedEnumClasses) return
        processedEnumClasses.add(clazz)

        for (field in clazz.declaredFields) {
            val type = field.type as Class<Any>
            if (type.isEnum) {
                module.addSerializer(type, OrdinalEnumSerializer(type))
                module.addDeserializer(type, OrdinalEnumDeserializer(type))
                logger.info("Registered ordinal enum serializer for type [${type.name}]")
            }
            if (!Modifier.isStatic(field.modifiers)) {
                registerOrdinalEnumFields(type, module)
            }
        }
    }

    private fun parseContent(message: MessageWrapper<Any?>, contentClazz: Class<out Any>?): Pair<Any?, Throwable?> {
        return try {
            Pair(objectMapper.readValue(objectMapper.writeValueAsString(message.content), contentClazz), null)
        } catch (ex: Throwable) {
            Pair(null, ex)
        }
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
}