package ai.tochka.protocol

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class EventTest : ProtocolTests() {
    data class EventContent(
        val foo: Int
    )

    interface TestClient {
        @Event("event")
        fun sendEvent(@Tag userId: Long, content: EventContent)
    }

    class TestServer {
        val received = CompletableFuture<EventContent>()

        @EventHandler("event")
        fun event(@Tag userId: Long, content: EventContent) {
            assertEquals(123, userId)
            received.complete(content)
        }
    }

    @Test
    fun testEvent() {
        val serviceFactory = ProtocolServiceFactory(clientConn)
        val client = serviceFactory.create(TestClient::class.java)

        val listener = ProtocolListener(serverConn)
        val server = TestServer()
        listener.connect(server, TestServer::class.java)

        client.sendEvent(123, EventContent(foo = 456))
        val received = server.received.get(60, TimeUnit.SECONDS)
        assertEquals(EventContent(foo = 456), received)
    }
}