package ai.tochka.protocol

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
