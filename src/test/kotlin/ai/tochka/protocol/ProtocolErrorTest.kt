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