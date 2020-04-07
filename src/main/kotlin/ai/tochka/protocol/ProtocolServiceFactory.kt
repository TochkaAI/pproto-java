package ai.tochka.protocol

import java.lang.reflect.Method
import java.lang.reflect.Proxy

class ProtocolServiceFactory(
    private val connection: ProtocolConnection
) {
    @Suppress("UNCHECKED_CAST", "unused")
    fun <T> create(iface: Class<T>): T {
        val handlers = iface.methods
            .associate { Pair(it, createHandler(it)) }
        val obj = Any()

        return Proxy.newProxyInstance(this.javaClass.classLoader, arrayOf(iface)) { _, method, args ->
            val handler = handlers[method]
            if (handler != null) {
                handler(args)
            } else {
                // перенаправление методов класса Object
                method.invoke(obj, *(args ?: emptyArray()))
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

        val commandType: String
        val messageType: MessageType
        val haveAnswer: Boolean

        when {
            commandAnnotation != null -> {
                commandType = commandAnnotation.type
                messageType = MessageType.COMMAND
                haveAnswer = true
            }
            eventAnnotation != null -> {
                commandType = eventAnnotation.type
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
            connection.registerContentType(MessageType.COMMAND, commandType, method.parameterTypes[commandIndex])
        }
        if (haveAnswer) {
            connection.registerContentType(MessageType.ANSWER, commandType, answerJavaType)
        }

        return { args ->
            var id: String? = null
            try {
                val content = commandIndex?.let { args?.get(it) }
                val tag = tagIndex?.let { args?.get(it) as Long }

                id = connection.sendMessage(Message(
                    type = messageType,
                    command = commandType,
                    content = content,
                    tags = tag?.let { listOf(it) } ?: emptyList()
                ))
                if (haveAnswer) {
                    val answerMessage = connection.waitForAnswer(commandType, id)
                    answerMessage.content
                } else {
                    null
                }
            } catch (ex: ProtocolException) {
                throw ex
            } catch (ex: Throwable) {
                throw ProtocolException("Error while processing command [$commandType] with id [$id]", ex)
            }
        }
    }
}

