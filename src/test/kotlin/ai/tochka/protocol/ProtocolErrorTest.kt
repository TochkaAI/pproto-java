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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class ProtocolErrorTest : ProtocolTests() {
    interface TestClient {
        @Command("answer-error")
        fun answerError()

        @Command("unhandled-error")
        fun unhandledError()

        @Event("event-error")
        fun eventError()

        @Command("is-alive")
        fun isAlive()
    }

    class TestServer {
        val eventReceived = CompletableFuture<Unit>()

        @CommandHandler("is-alive")
        fun isAlive() {}

        @CommandHandler("answer-error")
        fun answerError() {
            throw ProtocolAnswerException(
                group = 100,
                code = "error-code",
                message = "Error message"
            )
        }

        @CommandHandler("unhandled-error")
        fun unhandledError() {
            throw RuntimeException()
        }

        @EventHandler("event-error")
        fun eventError() {
            eventReceived.complete(Unit)
            throw RuntimeException()
        }
    }

    @Test(expected = ProtocolAnswerException::class)
    fun testAnswerError() {
        val client = clientChan.service(TestClient::class.java)

        val server = TestServer()
        serverChan.handler(server, TestServer::class.java)

        try {
            client.answerError()
        } catch (ex: ProtocolAnswerException) {
            client.isAlive()

            assertEquals(100, ex.group)
            assertEquals("error-code", ex.code)
            assertEquals("Error message", ex.message)
            throw ex
        }
    }

    @Test(expected = ProtocolException::class)
    fun testUnhandledError() {
        val client = clientChan.service(TestClient::class.java)

        val server = TestServer()
        serverChan.handler(server, TestServer::class.java)

        try {
            client.unhandledError()
        } catch (ex: Throwable) {
            client.isAlive()
            throw ex
        }
    }

    @Test
    fun testEventError() {
        val client = clientChan.service(TestClient::class.java)

        val server = TestServer()
        serverChan.handler(server, TestServer::class.java)

        client.eventError()
        server.eventReceived.get(60, TimeUnit.SECONDS)
        client.isAlive()
    }
}