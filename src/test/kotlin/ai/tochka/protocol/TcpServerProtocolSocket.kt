package ai.tochka.protocol

import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class TcpServerProtocolSocketFactory(private val port: Int) : ProtocolSocketFactory {
    override fun connect(): ProtocolSocket {
        val serverSocket = ServerSocket()
        serverSocket.bind(InetSocketAddress(port))
        val socket = serverSocket.accept()
        return TcpServerProtocolSocket(socket, serverSocket)
    }
}

class TcpServerProtocolSocket(private val socket: Socket, private val serverSocket: ServerSocket) : ProtocolSocket {
    override var soTimeout: Int
        get() = socket.soTimeout
        set(value) { socket.soTimeout = value }

    override val inputStream: InputStream
        get() = socket.getInputStream()

    override val outputStream: OutputStream
        get() = socket.getOutputStream()

    override fun close() {
        socket.close()
        serverSocket.close()
    }
}