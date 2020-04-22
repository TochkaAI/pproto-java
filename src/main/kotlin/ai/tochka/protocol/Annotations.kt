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

/**
 * Помечает метод как вызов команды с идентификатором [id] на сервере.
 * Возвращаемое значение метода - ответ от сервера.
 *
 * Метод может иметь от 0 до 2 аргументов:
 * - Аргумент без аннотаций - тело запроса.
 * - Аргумент с аннотацией [Tag] - дополнительный тег.
 *
 * @see[Channel.service]
 */
@Target(AnnotationTarget.FUNCTION)
annotation class Command(val id: String)

/**
 * Помечает метод как отправку события с идентификатором [id] на сервер.
 * Возвращаемое значение метода должно быть типа [Void].
 *
 * Метод может иметь от 0 до 2 аргументов:
 * - Аргумент без аннотаций - тело запроса.
 * - Аргумент с аннотацией [Tag] - дополнительный тег.
 *
 * @see[Channel.service]
 */
@Target(AnnotationTarget.FUNCTION)
annotation class Event(val id: String)

/**
 * Помечает аргумент как тег.
 * Теги передаются отдельно от тела запроса.
 * Теги могут быть только типа [Long].
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Tag

/**
 * Помечает аргумент как идентификатор сообщения.
 * Идентфиикатор может быть только типа [String].
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class MessageId

/**
 * Помечает метод как обработчик запроса с идентификатором [id].
 * Возвращаемое значение метода - ответ от сервера.
 *
 * Метод может иметь от 0 до 2 аргументов:
 * - Аргумент без аннотаций - тело запроса.
 * - Аргумент с аннотацией [Tag] - дополнительный тег.
 *
 * @see[Channel.handler]
 */
@Target(AnnotationTarget.FUNCTION)
annotation class CommandHandler(val id: String)

/**
 * Помечает метод как обработчик запроса с идентификатором [id].
 * Возвращаемое значение метода должно быть типа [Void].
 *
 * Метод может иметь от 0 до 2 аргументов:
 * - Аргумент без аннотаций - тело запроса.
 * - Аргумент с аннотацией [Tag] - дополнительный тег.
 *
 * @see[Channel.handler]
 */
@Target(AnnotationTarget.FUNCTION)
annotation class EventHandler(val id: String)
