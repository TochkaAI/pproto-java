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
        val client = clientChan.service(TestClient::class.java)

        val server = TestServer()
        serverChan.handler(server, TestServer::class.java)

        val answer = client.commandWithTag(100)
        assertEquals(101, answer)
    }
}