package ai.tochka.protocol

import org.slf4j.Logger
import java.util.*
import java.util.concurrent.Executor

internal class EventEmitter<T>(private val logger: Logger, private val executor: Executor) {
    private val callbacks =
        Collections.synchronizedSet(HashSet<(T) -> Unit>())

    fun add(callback: (T) -> Unit): Disposable {
        callbacks.add(callback)
        return object : Disposable {
            override fun dispose() { callbacks.remove(callback) }
        }
    }

    fun fire(event: T) {
        executor.execute {
            callbacks.forEach { listener ->
                try {
                    listener(event)
                } catch (ex: Throwable) {
                    logger.error("Failed to notify listener", ex)
                }
            }
        }
    }
}