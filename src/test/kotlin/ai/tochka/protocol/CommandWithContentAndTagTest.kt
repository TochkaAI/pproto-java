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

class CommandWithContentAndTagTest : ProtocolTests() {
    data class CommandContent(
        val foo: String,
        val bar: Int
    )

    data class AnswerContent(
        val foo: String,
        val tag: Long
    )

    interface TestClient {
        @Command("command-with-content-and-tag")
        fun commandWithContentAndTag(@Tag tag: Long, command: CommandContent): AnswerContent
    }

    class TestServer {
        @CommandHandler("command-with-content-and-tag")
        fun commandWithContentAndTag(@Tag tag: Long, command: CommandContent): AnswerContent {
            assertEquals(123, command.bar)
            return AnswerContent(
                foo = command.foo,
                tag = tag
            )
        }
    }

    @Test
    fun testCommandWithContentAndTag() {
        val client = clientChan.service(TestClient::class.java)

        val server = TestServer()
        serverChan.handler(server, TestServer::class.java)

        val answer = client.commandWithContentAndTag(
            -1000,
            CommandContent(
                foo = "test string",
                bar = 123
            )
        )
        assertEquals("test string", answer.foo)
        assertEquals(-1000, answer.tag)
    }
}