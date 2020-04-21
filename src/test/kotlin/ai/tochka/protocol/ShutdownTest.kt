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