package ai.tochka.protocol

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.After
import org.junit.Before
import java.net.SocketException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

abstract class ProtocolTests {
    protected lateinit var registry: MessageRegistry
    protected lateinit var serverChan: Channel
    protected lateinit var clientChan: ClientChannel

    private lateinit var listenChannel: ServerChannel
    private lateinit var listenThread: Thread

    private val clientExecutor = Executors.newSingleThreadExecutor()
    private val serverExecutor = Executors.newSingleThreadExecutor()

    @Before
    fun beforeTest() {
        val mapper = jacksonObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        registry = MessageRegistry(mapper)

        listenChannel = ServerChannel(8000, registry, serverExecutor)
        val serverFuture = CompletableFuture<Unit>()

        listenThread = thread(name = "listen-thread") {
            try {
                listenChannel.listen {
                    serverChan = it
                    serverFuture.complete(Unit)
                }
            } catch (ex: SocketException) {
                // pass
            }
        }

        clientChan = ClientChannel("127.0.0.1", 8000, registry, clientExecutor)
        val clientFuture = CompletableFuture<Unit>()
        clientChan.onConnect {
            clientFuture.complete(Unit)
        }

        clientFuture.get(60, TimeUnit.SECONDS)
        serverFuture.get(60, TimeUnit.SECONDS)
    }

    @After
    fun afterTest() {
        clientChan.close()
        serverChan.close()
        listenChannel.close()
        listenThread.join()
        serverExecutor.shutdown()
        clientExecutor.shutdown()
    }
}