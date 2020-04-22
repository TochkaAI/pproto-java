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

import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayList

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
        val params = method.parseParameters()

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
                throw RuntimeException("Method [$method] must be annotated with @ai.tochka.protocol.Command or @ai.tochka.protocol.Event")
            }
        }

        if (!haveAnswer && !isUnitAnswer) {
            throw RuntimeException("Event handler [$method] cannot return an answer")
        }

        for (param in params) {
            when (param) {
                is ParameterDesc.Command -> {
                    registry.registerContentType(MessageType.COMMAND, commandId, param.type)
                }
                is ParameterDesc.MessageId -> {
                    throw RuntimeException("Error in [$method], handlers parameters cannot have @ai.tochka.protocol.MessageId annotation")
                }
            }
        }
        if (haveAnswer) {
            registry.registerContentType(MessageType.ANSWER, commandId, answerJavaType)
        }

        return { args ->
            var id: String? = null
            try {
                var content: Any? = null
                val tags = ArrayList<Long?>()

                for ((index, param) in params.withIndex()) {
                    when (param) {
                        is ParameterDesc.Command -> content = args?.get(index)
                        is ParameterDesc.Tag -> tags.add(args?.get(index) as Long?)
                    }
                }

                id = UUID.randomUUID().toString()
                channel.sendMessage(Message(
                    id = id,
                    type = messageType,
                    command = commandId,
                    content = content,
                    tags = tags
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