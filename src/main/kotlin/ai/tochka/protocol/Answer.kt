package ai.tochka.protocol

/**
 * Обобщённый класс для ответа.
 * Позволяет прочитать контент и теги.
 */
data class Answer<T>(
    val content: T,
    val tags: List<Long?>?
)
