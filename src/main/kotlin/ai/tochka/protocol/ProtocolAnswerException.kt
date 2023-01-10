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
 * Ошибка бизнес-логики на стороне сервера.
 * Поля этой ошибки специфичны для конкретного приложения, использующего протокол.
 * @param[group] Номер группы ошибок.
 * @param[code] Код ошибки в группе.
 * @param[message] Описание ошибки.
 */
class ProtocolAnswerException(val group: Int, val code: String, message: String) : ProtocolException(message) {
    override fun toString() = "ProtocolAnswerException(group=$group, code=$code, message=$message)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ProtocolAnswerException

        if (group != other.group) return false
        if (code != other.code) return false
        if (message != other.message) return false

        return true
    }

    override fun hashCode(): Int {
        var result = group
        result = 31 * result + code.hashCode()
        result = 31 * result + message.hashCode()
        return result
    }
}
