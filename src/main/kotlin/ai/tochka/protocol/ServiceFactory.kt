package ai.tochka.protocol

import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.*
import java.util.concurrent.ConcurrentHashMap

internal class ServiceFactory(private val channel: MessageChannel, private val registry: MessageRegistry) {
    private val cache = ConcurrentHashMap<Class<*>, Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(clazz: Class<T>): T {
        return cache.computeIfAbsent(clazz) { iface ->
            val handlers = iface.methods
                .associate { Pair(it, createHandler(it)) }
            val obj = Any()

            Proxy.newProxyInstance(this.javaClass.classLoader, arrayOf(iface)) { _, method, args ->
                val handler = handlers[method]
                if (handler != null) {
                    handler(args)
                } else {
                    // перенаправление методов класса Object
                    method.invoke(obj, *(args ?: emptyArray()))
                }
            }
        } as T
    }

    private fun createHandler(method: Method): (Array<Any?>?) -> Any? {
        val tagIndex = tagIndex(method)
        val commandIndex = commandIndex(method)

        var answerJavaType = method.returnType
        var isUnitAnswer = false
        if (answerJavaType == Void::class.javaPrimitiveType) {
            answerJavaType = Any::class.java
            isUnitAnswer = true
        }

        val commandAnnotation = method.getAnnotation(Command::class.java)
        val eventAnnotation = method.getAnnotation(Event::class.java)

        if (commandAnnotation != null && eventAnnotation != null) {
            throw RuntimeException("Method [$method] cannot be a command and an event handler at the same time")
        }

        val commandId: String
        val messageType: MessageType
        val haveAnswer: Boolean

        when {
            commandAnnotation != null -> {
                commandId = commandAnnotation.id
                messageType = MessageType.COMMAND
                haveAnswer = true
            }
            eventAnnotation != null -> {
                commandId = eventAnnotation.id
                messageType = MessageType.EVENT
                haveAnswer = false
            }
            else -> {
                throw RuntimeException("Method [$method] must be annotated with @ai.tochka.Command or @ai.tochka.Event")
            }
        }

        if (!haveAnswer && !isUnitAnswer) {
            throw RuntimeException("Event handler [$method] cannot return an answer")
        }

        if (commandIndex != null) {
            registry.registerContentType(MessageType.COMMAND, commandId, method.parameterTypes[commandIndex])
        }
        if (haveAnswer) {
            registry.registerContentType(MessageType.ANSWER, commandId, answerJavaType)
        }

        return { args ->
            var id: String? = null
            try {
                val content = commandIndex?.let { args?.get(it) }
                val tag = tagIndex?.let { args?.get(it) as Long? }

                id = UUID.randomUUID().toString()
                channel.sendMessage(Message(
                    id = id,
                    type = messageType,
                    command = commandId,
                    content = content,
                    tags = tag?.let { listOf(it) } ?: emptyList()
                ))
                if (haveAnswer) {
                    val answerMessage = channel.waitForAnswer(commandId, id)
                    answerMessage.content
                } else {
                    null
                }
            } catch (ex: ProtocolException) {
                throw ex
            } catch (ex: Throwable) {
                throw ProtocolException(
                    "Error while processing command [$commandId] with id [$id]",
                    ex
                )
            }
        }
    }
}