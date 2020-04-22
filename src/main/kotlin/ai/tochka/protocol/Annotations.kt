package ai.tochka.protocol

/**
 * Помечает метод как вызов команды с идентификатором [id] на сервере.
 * Возвращаемое значение метода - ответ от сервера.
 *
 * Метод может иметь от 0 до 2 аргументов:
 * - Аргумент без аннотаций - тело запроса.
 * - Аргумент с аннотацией [Tag] - дополнительный тег.
 *
 * @see[Channel.service]
 */
@Target(AnnotationTarget.FUNCTION)
annotation class Command(val id: String)

/**
 * Помечает метод как отправку события с идентификатором [id] на сервер.
 * Возвращаемое значение метода должно быть типа [Void].
 *
 * Метод может иметь от 0 до 2 аргументов:
 * - Аргумент без аннотаций - тело запроса.
 * - Аргумент с аннотацией [Tag] - дополнительный тег.
 *
 * @see[Channel.service]
 */
@Target(AnnotationTarget.FUNCTION)
annotation class Event(val id: String)

/**
 * Помечает аргумент как тег.
 * Теги передаются отдельно от тела запроса.
 * Теги могут быть только типа [Long].
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Tag

/**
 * Помечает аргумент как идентификатор сообщения.
 * Идентфиикатор может быть только типа [String].
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class MessageId

/**
 * Помечает метод как обработчик запроса с идентификатором [id].
 * Возвращаемое значение метода - ответ от сервера.
 *
 * Метод может иметь от 0 до 2 аргументов:
 * - Аргумент без аннотаций - тело запроса.
 * - Аргумент с аннотацией [Tag] - дополнительный тег.
 *
 * @see[Channel.handler]
 */
@Target(AnnotationTarget.FUNCTION)
annotation class CommandHandler(val id: String)

/**
 * Помечает метод как обработчик запроса с идентификатором [id].
 * Возвращаемое значение метода должно быть типа [Void].
 *
 * Метод может иметь от 0 до 2 аргументов:
 * - Аргумент без аннотаций - тело запроса.
 * - Аргумент с аннотацией [Tag] - дополнительный тег.
 *
 * @see[Channel.handler]
 */
@Target(AnnotationTarget.FUNCTION)
annotation class EventHandler(val id: String)
