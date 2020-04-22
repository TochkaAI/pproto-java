package ai.tochka.protocol

import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

internal class CommandListener(
    private val channel: MessageChannel,
    private val registry: MessageRegistry
) {
    private val logger =
        LoggerFactory.getLogger(CommandListener::class.java)

    fun <T : Any> register(handler: T, clazz: Class<T>) {
        logger.info("Processing listener [${clazz.name}]")

        val commandMethods = clazz.methods
            .filter { it.isAnnotationPresent(CommandHandler::class.java) }
        val eventMethods = clazz.methods
            .filter { it.isAnnotationPresent(EventHandler::class.java) }

        for (method in commandMethods) {
            val annotation = method.getAnnotation(CommandHandler::class.java)
            registerMethod(handler, method, annotation.id, MessageType.COMMAND)
            logger.info("Command handler registered [$method]")
        }

        for (method in eventMethods) {
            val annotation = method.getAnnotation(EventHandler::class.java)
            registerMethod(handler, method, annotation.id, MessageType.EVENT)
            logger.info("Event handler registered [$method]")
        }
    }

    private fun registerMethod(bean: Any, method: Method, commandId: String, messageType: MessageType) {
        val params = method.parseParameters()

        for (param in params) {
            if (param is ParameterDesc.Command) {
                registry.registerContentType(messageType, commandId, param.type)
            }
        }

        if (messageType == MessageType.COMMAND && method.returnType != Void::class.javaPrimitiveType) {
            registry.registerContentType(MessageType.ANSWER, commandId, method.returnType)
        }

        channel.registerHandler(commandId) { message ->
            val args = params.map { param ->
                when (param) {
                    is ParameterDesc.Command -> message.content
                    is ParameterDesc.Tag -> message.tags?.singleOrNull()
                    is ParameterDesc.MessageId -> message.id
                }
            }.toTypedArray()

            val (result, error) = try {
                Pair(method.invoke(bean, *args), null)
            } catch (ex: InvocationTargetException) {
                val inner = ex.targetException
                if (inner is ProtocolAnswerException) {
                    Pair(null, inner)
                } else {
                    logger.error("Error in command handler", inner)
                    Pair(null,
                        ProtocolAnswerException(group = -1, code = "", message = "Unexpected error")
                    )
                }
            }

            if (messageType == MessageType.COMMAND) {
                if (error != null) {
                    channel.sendMessage(
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
                    channel.sendMessage(
                        Message(
                            id = message.id,
                            type = MessageType.ANSWER,
                            command = message.command,
                            content = if (result == Unit) null else result,
                            tags = message.tags
                        )
                    )
                }
            }
        }
    }
}