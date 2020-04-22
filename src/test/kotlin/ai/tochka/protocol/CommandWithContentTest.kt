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

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

class CommandWithContentTest : ProtocolTests() {
    companion object {
        private val testDateTime = OffsetDateTime.ofInstant(Instant.ofEpochSecond(123), ZoneId.of("UTC"))
    }

    data class CommandContent(
        val foo: String,
        val bar: Int
    )

    data class AnswerContent(
        val foo: String,
        val qux: OffsetDateTime
    )

    interface TestClient {
        @Command("command-with-content")
        fun commandWithContent(command: CommandContent): AnswerContent
    }

    class TestServer {
        @CommandHandler("command-with-content")
        fun commandWithContent(command: CommandContent): AnswerContent {
            assertEquals(123, command.bar)
            return AnswerContent(
                foo = command.foo,
                qux = testDateTime
            )
        }
    }

    @Test
    fun testCommandWithContent() {
        val client = clientChan.service(TestClient::class.java)

        val server = TestServer()
        serverChan.handler(server, TestServer::class.java)

        val answer = client.commandWithContent(
            CommandContent(
                foo = "test string",
                bar = 123
            )
        )
        assertEquals("test string", answer.foo)
        assertEquals(testDateTime, answer.qux)
    }
}