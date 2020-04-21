package ai.tochka.protocol

interface MessageChannel {
    fun sendMessage(message: Message)
    fun waitForAnswer(command: String, id: String): Message
    fun registerHandler(command: String, handler: CommandHandlerCallback)
}