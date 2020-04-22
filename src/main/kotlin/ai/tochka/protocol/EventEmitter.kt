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

import org.slf4j.Logger
import java.util.*
import java.util.concurrent.Executor

internal class EventEmitter<T>(private val logger: Logger, private val executor: Executor) {
    private val callbacks =
        Collections.synchronizedSet(HashSet<(T) -> Unit>())

    fun add(callback: (T) -> Unit): Disposable {
        callbacks.add(callback)
        return object : Disposable {
            override fun dispose() { callbacks.remove(callback) }
        }
    }

    fun fire(event: T) {
        executor.execute {
            callbacks.forEach { listener ->
                try {
                    listener(event)
                } catch (ex: Throwable) {
                    logger.error("Failed to notify listener", ex)
                }
            }
        }
    }
}