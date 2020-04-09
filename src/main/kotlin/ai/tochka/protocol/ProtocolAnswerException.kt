package ai.tochka.protocol

/**
 * Базовый класс ошибок протокола.
 * Когда бросается исключение именно этого типа, это означает что произошла внутренняя ошибка.
 * Примеры ошибок: разрыв соединения, неправильный формат JSON данных.
 * В поле [cause] содержится исходная причина ошибки.
 */
open class ProtocolException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Ошибка бизнес-логики на стороне сервера.
 * Поля этой ошибки специфичны для конкретного приложения, использующего протокол.
 * @param[group] Номер группы ошибок.
 * @param[code] Код ошибки в группе.
 * @param[message] Описание ошибки.
 */
class ProtocolAnswerException(val group: Int, val code: String, message: String) : ProtocolException(message) {
    override fun toString() = "ProtocolAnswerException(group=$group, code=$code, message=$message)"
}
