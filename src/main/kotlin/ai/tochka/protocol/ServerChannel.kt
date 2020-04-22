/*
 * MIT License
 *
 * Copyright (c) 2020 Alexander Shilov (ashlanderr) <aleksandr.schilow2012@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

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