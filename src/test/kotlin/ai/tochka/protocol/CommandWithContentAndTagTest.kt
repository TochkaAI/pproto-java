package ai.tochka.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandWithContentAndTagTest : ProtocolTests() {
    data class CommandContent(
        val foo: String,
        val bar: Int
    )

    data class AnswerContent(
        val foo: String,
        val tag: Long
    )

    interface TestClient {
        @Command("command-with-content-and-tag")
        fun commandWithContentAndTag(@Tag tag: Long, command: CommandContent): AnswerContent
    }

    class TestServer {
        @CommandHandler("command-with-content-and-tag")
        fun commandWithContentAndTag(@Tag tag: Long, command: CommandContent): AnswerContent {
            assertEquals(123, command.bar)
            return AnswerContent(
                foo = command.foo,
                tag = tag
            )
        }
    }

    @Test
    fun testCommandWithContentAndTag() {
        val client = clientChan.service(TestClient::class.java)

        val server = TestServer()
        serverChan.handler(server, TestServer::class.java)

        val answer = client.commandWithContentAndTag(
            -1000,
            CommandContent(
                foo = "test string",
                bar = 123
            )
        )
        assertEquals("test string", answer.foo)
        assertEquals(-1000, answer.tag)
    }
}