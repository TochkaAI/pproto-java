package ai.tochka.protocol

annotation class Command(val type: String)
annotation class Event(val type: String)
annotation class Tag
annotation class CommandHandler(val type: String)
annotation class EventHandler(val type: String)
