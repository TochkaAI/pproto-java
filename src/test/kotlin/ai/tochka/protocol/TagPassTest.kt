package ai.tochka.protocol

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.*

class TagPassTest : ProtocolTests() {
    class EmptyHandler {
        @CommandHandler(id = "tag-pass-test")
        fun request() = Unit
    }

    @Test
    fun testTagPassedThrough() {
        serverChan.handler(EmptyHandler(), EmptyHandler::class.java)

        val id = UUID.randomUUID().toString()

        clientChan.sendMessage(Message(
            id = id,
            tags = listOf(1, 2, 3),
            status = MessageStatus.SUCCESS,
            type = MessageType.COMMAND,
            command = "tag-pass-test",
            content = null,
            priority = MessagePriority.NORMAL
        ))
        val answerMsg = clientChan.waitForAnswer("tag-pass-test", id)

        assertEquals(listOf<Long>(1, 2, 3), answerMsg.tags)
    }
}