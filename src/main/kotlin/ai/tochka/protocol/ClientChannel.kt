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
import java.net.Socket
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.function.Consumer
import kotlin.concurrent.thread
import kotlin.math.min
import kotlin.system.exitProcess

class ClientChannel @JvmOverloads constructor(
    private val host: String,
    private val port: Int,
    private val registry: MessageRegistry,
    private val executor: Executor,
    private val initialConnectTimeout: Duration = Duration.ofSeconds(1),
    private val maxConnectTimeout: Duration = Duration.ofSeconds(64),
    private val readTimeout: Duration = Duration.ofSeconds(5)
) : Channel, Closeable {
    private val logger = LoggerFactory.getLogger(ClientChannel::class.java)

    private val sync = Any()

    private var channel: ChannelImpl? = null

    private val serviceFactory = ServiceFactory(this, registry)
    private val listener = CommandListener(this, registry)
    private val handlers = ConcurrentHashMap<String, CommandHandlerCallback>()

    private val connectEmitter = EventEmitter<Socket>(logger, executor)
    private val disconnectEmitter = EventEmitter<Socket>(logger, executor)

    @Volatile
    private var running = true

    private val thread: Thread = start()

    override fun <T : Any> service(clazz: Class<T>): T {
        return serviceFactory.get(clazz)
    }

    override fun <T : Any> handler(handler: T, clazz: Class<T>) {
        listener.register(handler, clazz)
    }

    fun onConnect(callback: (Socket) -> Unit): Disposable {
        return connectEmitter.add(callback)
    }

    fun onConnect(callback: Consumer<Socket>): Disposable {
        return onConnect { callback.accept(it) }
    }

    override fun onDisconnect(block: (Socket) -> Unit): Disposable {
        return disconnectEmitter.add(block)
    }

    override fun close() {
        logger.info("Stopping connection thread...")
        running = false
        closeConnection()
        thread.join()
        logger.info("Connection thread stopped")
    }

    override fun close(code: Int, description: String) {
        running = false
        channel?.close(code, description)
        close()
    }

    override fun sendMessage(message: Message) = synchronized(sync) {
        channel().sendMessage(message)
    }

    override fun waitForAnswer(command: String, id: String): Message {
        return channel().waitForAnswer(command, id)
    }

    override fun registerHandler(command: String, handler: CommandHandlerCallback) = synchronized<Unit>(sync) {
        if (handlers.containsKey(command))
            throw RuntimeException("Command handler for '$command' already registered")
        handlers[command] = handler
        channel?.registerHandler(command, handler)
    }

    private fun start() = thread(name = "client-channel-$host:$port") {
        logger.info("Connection thread started")
        var backoff = initialConnectTimeout.toMillis()

        while (running) {
            try {
                connect()
                channel?.socket?.let { connectEmitter.fire(it) }
                backoff = initialConnectTimeout.toMillis()
                channel().run()
            } catch (ex: IOException) {
                logger.error("Connection failed, cause = $ex, backoff = $backoff")
                if (running) Thread.sleep(backoff)
                backoff = min(maxConnectTimeout.toMillis(), backoff * 2)
            } catch (ex: Throwable) {
                logger.error("Fatal error in connection thread", ex)
                running = false
                exitProcess(1)
            } finally {
                channel?.socket?.let { disconnectEmitter.fire(it) }
                closeConnection()
            }
        }

        logger.info("Connection thread exiting")
    }

    private fun channel() = channel ?: throw RuntimeException("Connection not available")

    private fun connect() {
        val socket = Socket(host, port)
        val channel = ChannelImpl(socket, readTimeout, registry, executor)
        channel.init()

        synchronized(sync) {
            handlers.forEach { (command, handler) ->
                channel.registerHandler(command, handler)
            }
            this.channel = channel
        }

        logger.info("Connected")
    }

    private fun closeConnection() = synchronized(sync) {
        try {
            channel?.close()
        } catch (ex: Throwable) {
            logger.error("Error while closing connection", ex)
        } finally {
            channel = null
        }
    }
}