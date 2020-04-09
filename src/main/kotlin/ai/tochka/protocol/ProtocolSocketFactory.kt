package ai.tochka.protocol

import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

interface ProtocolSocket : Closeable {
    var soTimeout: Int
    val inputStream: InputStream
    val outputStream: OutputStream
}

/**
 * Фабрика сокетов для использования в [ProtocolConnection].
 */
interface ProtocolSocketFactory {
    fun connect(): ProtocolSocket
}

/**
 * Реализация фабрики, которая устанавливает клиентское TCP соединение по адресу [host]:[port].
 */
class TcpClientProtocolSocketFactory(
    private val host: String,
    private val port: Int
) : ProtocolSocketFactory {
    private val logger = LoggerFactory.getLogger(TcpClientProtocolSocketFactory::class.java)

    override fun connect(): ProtocolSocket {
        logger.info("Connecting to ${host}:${port}...")
        return TcpProtocolSocket(Socket(host, port))
    }
}

class TcpProtocolSocket(private val socket: Socket) : ProtocolSocket {
    override var soTimeout: Int
        get() = socket.soTimeout
        set(value) { socket.soTimeout = value }

    override val inputStream: InputStream
        get() = socket.getInputStream()

    override val outputStream: OutputStream
        get() = socket.getOutputStream()

    override fun close() {
        socket.close()
    }
}