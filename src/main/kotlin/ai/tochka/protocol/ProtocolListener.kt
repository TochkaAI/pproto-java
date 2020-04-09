package ai.tochka.protocol

import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Вспомогательный класс, который регистрирует обработчики запросов и событий в [connection].
 */
class ProtocolListener(
    private val connection: ProtocolConnection
) {
    private val logger = LoggerFactory.getLogger(ProtocolListener::class.java)

    /**
     * Регистрация обработчика [service] класса [clazz].
     * Методы [service], которые помечены [CommandHandler] или [EventHandler] будут вызываться,
     * когда в [connection] придут запросы с соответствующими типами.
     * Можно зарегистрировать только один обработчик для каждого типа.
     * Вызов этого метода потокобезопасен.
     * @see[CommandHandler]
     * @see[EventHandler]
     */
    fun <T : Any> connect(service: T, clazz: Class<T>) {
        logger.info("Processing listener [${clazz.name}]")

        val commandMethods = clazz.methods
            .filter { it.isAnnotationPresent(CommandHandler::class.java) }
        val eventMethods = clazz.methods
            .filter { it.isAnnotationPresent(EventHandler::class.java) }

        for (method in commandMethods) {
            val annotation = method.getAnnotation(CommandHandler::class.java)
            registerMethod(service, method, annotation.type, MessageType.COMMAND)
            logger.info("Command handler registered [$method]")
        }

        for (method in eventMethods) {
            val annotation = method.getAnnotation(EventHandler::class.java)
            registerMethod(service, method, annotation.type, MessageType.EVENT)
            logger.info("Event handler registered [$method]")
        }
    }

    private fun registerMethod(bean: Any, method: Method, commandType: String, messageType: MessageType) {
        val tagIndex = tagIndex(method)
        val commandIndex = commandIndex(method)

        if (commandIndex != null) {
            connection.registerContentType(messageType, commandType, method.parameterTypes[commandIndex])
        }
        if (messageType == MessageType.COMMAND && method.returnType != Void::class.javaPrimitiveType) {
            connection.registerContentType(MessageType.ANSWER, commandType, method.returnType)
        }

        connection.registerCommandHandler(commandType) { message ->
            val args = Array<Any?>(method.parameterCount) { null }
            tagIndex?.let { args[tagIndex] = message.tags?.singleOrNull() }
            commandIndex?.let { args[commandIndex] = message.content }

            val (result, error) = try {
                Pair(method.invoke(bean, *args), null)
            } catch (ex: InvocationTargetException) {
                val inner = ex.targetException
                if (inner is ProtocolAnswerException) {
                    Pair(null, inner)
                } else {
                    logger.error("Error in command handler", inner)
                    Pair(null, ProtocolAnswerException(group = -1, code = "", message = "Unexpected error"))
                }
            }

            if (messageType == MessageType.COMMAND) {
                if (error != null) {
                    connection.sendMessage(
                        Message(
                            id = message.id,
                            type = MessageType.ANSWER,
                            status = MessageStatus.FAILED,
                            command = message.command,
                            content = MessageError(
                                group = error.group,
                                code = error.code,
                                description = error.message ?: ""
                            )
                        )
                    )
                } else {
                    connection.sendMessage(
                        Message(
                            id = message.id,
                            type = MessageType.ANSWER,
                            command = message.command,
                            content = if (result == Unit) null else result
                        )
                    )
                }
            }
        }
    }
}