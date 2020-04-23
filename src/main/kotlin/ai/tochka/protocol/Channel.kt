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

import java.io.Closeable
import java.net.Socket

/**
 * Канал связи между клиентом и сервером.
 */
interface Channel : MessageChannel, Closeable {
    /**
     * Создаёт реализацию интерфейса [clazz].
     * Все методы [clazz] должны быть помечены аннотациями [Command] или [Event].
     * Вызовы этого метода и всех методов [clazz] потокобезопасены.
     * Одновременные запросы из разных потоков к одному [Channel] будут выстраиваться в очередь.
     * @see[Command]
     * @see[Event]
     */
    fun <T : Any> service(clazz: Class<T>): T

    /**
     * Регистрация обработчика [handler] класса [clazz].
     * Методы [handler], которые помечены [CommandHandler] или [EventHandler] будут вызываться,
     * когда в [Channel] придут запросы с соответствующими типами.
     * Можно зарегистрировать только один обработчик для каждого типа.
     * Вызов этого метода потокобезопасен.
     * @see[CommandHandler]
     * @see[EventHandler]
     */
    fun <T : Any> handler(handler: T, clazz: Class<T>)

    fun onDisconnect(block: (Socket) -> Unit): Disposable

    /**
     * Закрытие соединения с указанием причины.
     * @param[code] Код причины. 0 - несовместимость версии протоколов.
     * @param[description] Описание причины.
     */
    fun close(code: Int = 0, description: String = "")
}