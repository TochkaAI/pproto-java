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