package ai.tochka.protocol

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.After
import org.junit.Before
import java.io.Closeable
import java.util.concurrent.Executors

abstract class ProtocolTests {
    protected val dispose = ArrayList<Closeable>()
    protected lateinit var serverConn: ProtocolConnection
    protected lateinit var clientConn: ProtocolConnection

    @Before
    fun beforeTest() {
        val executor1 = Executors.newSingleThreadExecutor()
        dispose.add(Closeable { executor1.shutdown() })

        val executor2 = Executors.newSingleThreadExecutor()
        dispose.add(Closeable { executor2.shutdown() })

        val mapper = jacksonObjectMapper()
            .setPropertyNamingStrategy(CustomPropertyNamingStrategy)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        serverConn = ProtocolConnection.create(
            ProtocolProperties(),
            TcpServerProtocolSocketFactory(8000),
            executor1,
            mapper
        )
        dispose.add(serverConn)
        val serverFuture = serverConn.start()

        clientConn = ProtocolConnection.create(
            ProtocolProperties(),
            TcpClientProtocolSocketFactory("127.0.0.1", 8000),
            executor2,
            mapper
        )
        dispose.add(clientConn)
        clientConn.start().get()
        serverFuture.get()
    }

    @After
    fun afterTest() {
        dispose.asReversed().forEach { it.close() }
    }
}