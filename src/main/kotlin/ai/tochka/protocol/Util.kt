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
import java.lang.reflect.Parameter

private val tagClasses = setOf(
    Long::class.javaPrimitiveType!!,
    Long::class.javaObjectType
)

private val messageIdClasses = setOf(
    String::class.java
)

internal sealed class ParameterDesc {
    data class Command(val parameter: Parameter, val type: Class<*>) : ParameterDesc()
    data class Tag(val parameter: Parameter) : ParameterDesc()
    data class MessageId(val parameter: Parameter) : ParameterDesc()
}

internal fun Method.parseParameters(): List<ParameterDesc> {
    val result = this.parameters
        .map { it.parseParameter() }

    val duplicate = result
        .groupBy { it::class }
        .mapValues { it.value.size }
        .filterValues { it > 1 }
        .entries
        .firstOrNull()

    if (duplicate != null) {
        throw RuntimeException("Method [$this] has more than one ${duplicate.key.simpleName} parameter")
    }

    return result
}

internal fun Parameter.parseParameter(): ParameterDesc {
    val tagAnnotation = this.getAnnotation(Tag::class.java)
    if (tagAnnotation != null) {
        if (this.type !in tagClasses) {
            throw RuntimeException("Parameter [${this}] has wrong class, must be Long")
        }
        return ParameterDesc.Tag(this)
    }

    val messageIdAnnotation = this.getAnnotation(MessageId::class.java)
    if (messageIdAnnotation != null) {
        if (this.type !in messageIdClasses) {
            throw RuntimeException("Parameter [${this}] has wrong class, must be String")
        }
        return ParameterDesc.MessageId(this)
    }

    return ParameterDesc.Command(this, this.type)
}

internal fun String.trimWithEllipsis(maxLength: Int): String {
    return if (this.length > maxLength) {
        this.take(maxLength - 3) + "..."
    } else {
        this
    }
}