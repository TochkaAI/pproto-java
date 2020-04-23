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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.util.*

val PROTOCOL_SIGNATURE: UUID = UUID.fromString("fea6b958-dafb-4f5c-b620-fe0aafbd47e2")

@Suppress("unused")
object CommandId {
    const val PROTOCOL_COMPATIBLE = "173cbbeb-1d81-4e01-bf3c-5d06f9c878c3"
    const val UNKNOWN = "4aef29d6-5b1a-4323-8655-ef0d4f1bb79d"
    const val ERROR = "b18b98cc-b026-4bfe-8e33-e7afebfbe78b"
    const val WEB_AUTHORIZATION = "7bd4b1c1-3f8f-4bcf-b941-5a0f1e7b449f"
    const val CLOSE_CONNECTION = "e71921fd-e5b3-4f9b-8be7-283e8bb2a531"
}

enum class MessageType(val value: Int) {
    UNKNOWN(0),
    COMMAND(1),
    ANSWER(2),
    EVENT(3),
}

enum class MessagePriority(val value: Int) {
    HIGH(0),
    NORMAL(1),
    LOW(2),
}

enum class MessageStatus(val value: Int) {
    UNKNOWN(0),
    SUCCESS(1),
    FAILED(2),
    ERROR(3),
}

data class MessageError(
    val group: Int,
    val code: String,
    val description: String
)

data class Error(
    val commandId: String,
    val messageId: String,
    val code: String,
    val description: String
)

data class Message(
    val id: String,
    val type: MessageType,
    val command: String,
    val content: Any? = null,
    val status: MessageStatus = MessageStatus.SUCCESS,
    val priority: MessagePriority = MessagePriority.NORMAL,
    val tags: List<Long?>? = null
)

data class MessageWrapper<T>(
    val id: String,
    val command: String,
    val flags: Long,
    val content: T?,

    @JsonDeserialize(using = UnsignedLongListDeserializer::class)
    @JsonSerialize(using = UnsignedLongListSerializer::class)
    val tags: List<Long?>?
)

data class ContentType(
    val messageType: MessageType,
    val commandType: String
)

typealias CommandHandlerCallback = (message: Message) -> Unit

data class CloseConnectionCommand(
    val code: Int,
    val description: String
)