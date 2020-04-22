package ai.tochka.protocol

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.*

class CommandMessageIdTest : ProtocolTests() {
    class TestServer {
        @CommandHandler("command-with-id")
        fun commandWithId(@MessageId messageId: String): String {
            return messageId
        }
    }

    @Test
    fun testCommandWithId() {
        val server = TestServer()
        serverChan.handler(server, TestServer::class.java)

        val id = UUID.randomUUID().toString()
        clientChan.sendMessage(
            Message(
                id = id,
                type = MessageType.COMMAND,
                command = "command-with-id"
            )
        )

        val answer = clientChan.waitForAnswer("command-with-id", id)
        assertEquals(id, answer.content)
    }

}