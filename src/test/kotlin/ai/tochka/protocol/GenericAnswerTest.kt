package ai.tochka.protocol

import org.junit.Assert
import org.junit.Test
import java.util.*

class GenericAnswerTest : ProtocolTests() {
    data class CommandContent(
        val foo: String,
        val bar: Int
    )

    data class AnswerContent(
        val foo: String,
        val bar: Int,
        val baz: Double
    )

    interface TestClient {
        @Command("command-with-generic-answer")
        fun genericCommand(command: CommandContent): Answer<AnswerContent>
    }

    class TestServer {
        @CommandHandler("command-with-generic-answer")
        fun genericCommand(command: CommandContent): Answer<AnswerContent> {
            return Answer(
                content = AnswerContent(
                    foo = command.foo,
                    bar = command.bar,
                    baz = 1.2
                ),
                tags = listOf(1L, 2L, null)
            )
        }
    }

    @Test
    fun testGenericAnswer() {
        clientChan.service(TestClient::class.java)

        val server = TestServer()
        serverChan.handler(server, TestServer::class.java)

        val id = UUID.randomUUID().toString()

        clientChan.sendMessage(Message(
            id = id,
            status = MessageStatus.SUCCESS,
            type = MessageType.COMMAND,
            command = "command-with-generic-answer",
            content = CommandContent(
                foo = "test string",
                bar = 123
            ),
            priority = MessagePriority.NORMAL
        ))
        val answerMsg = clientChan.waitForAnswer("command-with-generic-answer", id)

        val answerContent = AnswerContent(
            foo = "test string",
            bar = 123,
            baz = 1.2
        )
        Assert.assertEquals(answerContent, answerMsg.content)

        val answerTags = listOf(1L, 2L, null)
        Assert.assertEquals(answerTags, answerMsg.tags)
    }
}
