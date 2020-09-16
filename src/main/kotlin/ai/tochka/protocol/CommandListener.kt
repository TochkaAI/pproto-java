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
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType

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

        if (messageType == MessageType.COMMAND) {
            var returnType = method.genericReturnType

            if (returnType is ParameterizedType && returnType.rawType == Answer::class.java) {
                returnType = returnType.actualTypeArguments[0]
            }

            if (returnType != Void::class.javaPrimitiveType) {
                registry.registerContentType(MessageType.ANSWER, commandId, returnType as Class<*>)
            }
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

            var tags = message.tags
            var content = result

            if (result is Answer<*>) {
                tags = result.tags
                content = result.content
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
                            content = if (content == Unit) null else content,
                            tags = tags
                        )
                    )
                }
            }
        }
    }
}