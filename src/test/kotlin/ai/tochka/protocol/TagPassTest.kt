package ai.tochka.protocol

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.*

class TagPassTest : ProtocolTests() {
    class EmptyHandler {
        @CommandHandler(type = "tag-pass-test")
        fun request() = Unit
    }

    @Test
    fun testTagPassedThrough() {
        val listener = ProtocolListener(serverConn)
        listener.connect(EmptyHandler(), EmptyHandler::class.java)

        val id = UUID.randomUUID().toString()

        clientConn.sendMessage(Message(
            id = id,
            tags = listOf(1, 2, 3),
            status = MessageStatus.SUCCESS,
            type = MessageType.COMMAND,
            command = "tag-pass-test",
            content = null,
            priority = MessagePriority.NORMAL
        ))
        val answerMsg = clientConn.waitForAnswer("tag-pass-test", id)

        assertEquals(listOf<Long>(1, 2, 3), answerMsg.tags)
    }
}