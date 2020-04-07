package ai.tochka.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandWithTagTest : ProtocolTests() {
    interface TestClient {
        @Command("command-with-tag")
        fun commandWithTag(@Tag userId: Long): Long
    }

    class TestServer {
        @CommandHandler("command-with-tag")
        fun commandWithTag(@Tag userId: Long): Long {
            return userId + 1
        }
    }

    @Test
    fun testCommandWithTag() {
        val serviceFactory = ProtocolServiceFactory(clientConn)
        val client = serviceFactory.create(TestClient::class.java)

        val listener = ProtocolListener(serverConn)
        val server = TestServer()
        listener.connect(server, TestServer::class.java)

        val answer = client.commandWithTag(100)
        assertEquals(101, answer)
    }
}