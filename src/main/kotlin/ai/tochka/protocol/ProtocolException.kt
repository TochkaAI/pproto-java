package ai.tochka.protocol

/**
 * Базовый класс ошибок протокола.
 * Когда бросается исключение именно этого типа, это означает что произошла внутренняя ошибка.
 * Примеры ошибок: разрыв соединения, неправильный формат JSON данных.
 * В поле [cause] содержится исходная причина ошибки.
 */
open class ProtocolException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause)
