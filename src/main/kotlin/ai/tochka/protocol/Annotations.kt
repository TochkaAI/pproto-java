package ai.tochka.protocol

/**
 * Помечает метод как вызов команды [type] на сервере.
 * Возвращаемое значение метода - ответ от сервера.
 *
 * Метод может иметь от 0 до 2 аргументов:
 * - Аргумент без аннотаций - тело запроса.
 * - Аргумент с аннотацией [Tag] - дополнительный тег.
 *
 * @see[ProtocolServiceFactory.create]
 */
@Target(AnnotationTarget.FUNCTION)
annotation class Command(val type: String)

/**
 * Помечает метод как отправку события [type] на сервер.
 * Возвращаемое значение метода должно быть типа [Void].
 *
 * Метод может иметь от 0 до 2 аргументов:
 * - Аргумент без аннотаций - тело запроса.
 * - Аргумент с аннотацией [Tag] - дополнительный тег.
 *
 * @see[ProtocolServiceFactory.create]
 */
@Target(AnnotationTarget.FUNCTION)
annotation class Event(val type: String)

/**
 * Помечает аргумент как тег.
 * Теги передаются отдельно от тела запроса.
 * Теги могут быть только типа [Long].
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Tag

/**
 * Помечает метод как обработчик запроса типа [type].
 * Возвращаемое значение метода - ответ от сервера.
 *
 * Метод может иметь от 0 до 2 аргументов:
 * - Аргумент без аннотаций - тело запроса.
 * - Аргумент с аннотацией [Tag] - дополнительный тег.
 *
 * @see[ProtocolListener.connect]
 */
@Target(AnnotationTarget.FUNCTION)
annotation class CommandHandler(val type: String)

/**
 * Помечает метод как обработчик запроса типа [type].
 * Возвращаемое значение метода должно быть типа [Void].
 *
 * Метод может иметь от 0 до 2 аргументов:
 * - Аргумент без аннотаций - тело запроса.
 * - Аргумент с аннотацией [Tag] - дополнительный тег.
 *
 * @see[ProtocolListener.connect]
 */
@Target(AnnotationTarget.FUNCTION)
annotation class EventHandler(val type: String)
