package ai.tochka.protocol

open class ProtocolException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause)

class ProtocolAnswerException(val group: Int, val code: String, message: String) : ProtocolException(message) {
    override fun toString() = "ProtocolAnswerException(group=$group, code=$code, message=$message)"
}