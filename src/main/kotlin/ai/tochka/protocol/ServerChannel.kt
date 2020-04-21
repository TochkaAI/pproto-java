package ai.tochka.protocol

import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.time.Duration
import java.util.concurrent.Executor
import java.util.function.Consumer
import kotlin.concurrent.thread


class ServerChannel @JvmOverloads constructor(
    private val port: Int,
    private val registry: MessageRegistry,
    private val executor: Executor,
    private val readTimeout: Duration = Duration.ofSeconds(5)
) : Closeable {
    private val logger = LoggerFactory.getLogger(ServerChannel::class.java)
    private val serverSocket = ServerSocket(port)

    fun listen(onConnect: (Channel) -> Unit) {
        logger.info("Listening for client connections on [0.0.0.0:$port]")
        try {
            while (true) {
                val socket = serverSocket.accept()
                startSocketThread(socket, onConnect)
            }
        } finally {
            logger.info("Server socket closed")
        }
    }

    fun listen(onConnect: Consumer<Channel>) {
        listen { onConnect.accept(it) }
    }

    private fun startSocketThread(
        socket: Socket,
        onConnect: (Channel) -> Unit
    ) {
        val address = socket.remoteSocketAddress

        thread(name = "server-channel-$address") {
            try {
                logger.info("Connection accepted, remote address = [$address]")
                val channel = ChannelImpl(socket, readTimeout, registry, executor)
                channel.init()
                onConnect(channel)
                channel.run()
            } catch (ex: IOException) {
                logger.error("Error in server socket loop: $ex")
            }
        }
    }

    override fun close() {
        serverSocket.close()
    }
}