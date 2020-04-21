package ai.tochka.protocol

import java.io.Closeable
import java.net.Socket

/**
 * Канал связи между клиентом и сервером.
 */
interface Channel : MessageChannel, Closeable {
    /**
     * Создаёт реализацию интерфейса [clazz].
     * Все методы [clazz] должны быть помечены аннотациями [Command] или [Event].
     * Вызовы этого метода и всех методов [clazz] потокобезопасены.
     * Одновременные запросы из разных потоков к одному [Channel] будут выстраиваться в очередь.
     * @see[Command]
     * @see[Event]
     */
    fun <T : Any> service(clazz: Class<T>): T

    /**
     * Регистрация обработчика [handler] класса [clazz].
     * Методы [handler], которые помечены [CommandHandler] или [EventHandler] будут вызываться,
     * когда в [Channel] придут запросы с соответствующими типами.
     * Можно зарегистрировать только один обработчик для каждого типа.
     * Вызов этого метода потокобезопасен.
     * @see[CommandHandler]
     * @see[EventHandler]
     */
    fun <T : Any> handler(handler: T, clazz: Class<T>)
    fun onDisconnect(block: (Socket) -> Unit): Disposable
}