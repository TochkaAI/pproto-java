package ai.tochka.protocol

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class EmptyCommandTest : ProtocolTests() {
    interface TestClient {
        @Command("empty-command")
        fun emptyCommand()
    }

    class TestServer {
        val handled = AtomicBoolean()

        @CommandHandler("empty-command")
        fun emptyCommand() {
            handled.set(true)
        }
    }

    @Test
    fun testEmptyCommand() {
        val client = clientChan.service(TestClient::class.java)

        val server = TestServer()
        serverChan.handler(server, TestServer::class.java)

        client.emptyCommand()
        assertTrue(server.handled.get())
    }
}