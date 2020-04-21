package ai.tochka.protocol

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

class CommandWithContentTest : ProtocolTests() {
    companion object {
        private val testDateTime = OffsetDateTime.ofInstant(Instant.ofEpochSecond(123), ZoneId.of("UTC"))
    }

    data class CommandContent(
        val foo: String,
        val bar: Int
    )

    data class AnswerContent(
        val foo: String,
        val qux: OffsetDateTime
    )

    interface TestClient {
        @Command("command-with-content")
        fun commandWithContent(command: CommandContent): AnswerContent
    }

    class TestServer {
        @CommandHandler("command-with-content")
        fun commandWithContent(command: CommandContent): AnswerContent {
            assertEquals(123, command.bar)
            return AnswerContent(
                foo = command.foo,
                qux = testDateTime
            )
        }
    }

    @Test
    fun testCommandWithContent() {
        val client = clientChan.service(TestClient::class.java)

        val server = TestServer()
        serverChan.handler(server, TestServer::class.java)

        val answer = client.commandWithContent(
            CommandContent(
                foo = "test string",
                bar = 123
            )
        )
        assertEquals("test string", answer.foo)
        assertEquals(testDateTime, answer.qux)
    }
}