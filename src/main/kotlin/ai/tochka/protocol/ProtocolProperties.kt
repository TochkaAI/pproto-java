package ai.tochka.protocol

class ProtocolProperties {
    var initialConnectTimeout: Long = 1000L
    var maxConnectTimeout: Long = 64000L
    var readTimeout: Long = 30000L
    var password: String? = null
}