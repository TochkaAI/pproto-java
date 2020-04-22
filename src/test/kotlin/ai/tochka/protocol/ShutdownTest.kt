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

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class ShutdownTest : ProtocolTests() {
    interface TestClient {
        @Command("empty-command")
        fun emptyCommand()
    }

    class TestServer {
        val received = CompletableFuture<Unit>()
        val complete = CompletableFuture<Unit>()

        @CommandHandler("empty-command")
        fun emptyCommand() {
            received.complete(Unit)
            complete.get()
        }
    }

    @Test
    fun testShutdown() {
        val client = clientChan.service(TestClient::class.java)

        val server = TestServer()
        serverChan.handler(server, TestServer::class.java)

        val result = CompletableFuture<Unit>()
        val requestThread = thread {
            try {
                client.emptyCommand()
                result.complete(Unit)
            } catch (ex: Throwable) {
                result.completeExceptionally(ex)
            }
        }
        server.received.get(60, TimeUnit.SECONDS)
        clientChan.close()

        val ex = try {
            result.get(60, TimeUnit.SECONDS)
            null
        } catch (ex: ExecutionException) {
            ex.cause
        } finally {
            requestThread.interrupt()
            server.complete.complete(Unit)
        }
        assertTrue("Expected ProtocolException", ex is ProtocolException)

        try {
            client.emptyCommand()
            assertTrue("Expected ProtocolException", false)
        } catch (ex: ProtocolException) {
            // pass
        }
    }
}