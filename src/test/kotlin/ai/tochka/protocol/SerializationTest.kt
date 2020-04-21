package ai.tochka.protocol

import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

class SerializationTest : ProtocolTests() {
    enum class TestEnum {
        FIRST,
        SECOND,
    }

    data class EnumWrapper(
        val enum: TestEnum
    )

    data class DateTimeWrapper(
        val value: OffsetDateTime
    )

    @Test
    fun testEnum() {
        registry.registerContentType(MessageType.COMMAND, "enum", EnumWrapper::class.java)

        var value = EnumWrapper(TestEnum.FIRST)
        var str = registry.objectMapper.writeValueAsString(value)
        assertEquals("""{"enum":0}""", str)

        value = EnumWrapper(TestEnum.SECOND)
        str = registry.objectMapper.writeValueAsString(value)
        assertEquals("""{"enum":1}""", str)
    }

    @Test
    fun testDateTime() {
        registry.registerContentType(MessageType.COMMAND, "enum", DateTimeWrapper::class.java)

        var value = DateTimeWrapper(OffsetDateTime.ofInstant(Instant.ofEpochMilli(100200), ZoneId.of("UTC")))
        var str = registry.objectMapper.writeValueAsString(value)
        assertEquals("""{"value":100200}""", str)

        value = DateTimeWrapper(OffsetDateTime.ofInstant(Instant.ofEpochMilli(1234567), ZoneId.of("UTC")))
        str = registry.objectMapper.writeValueAsString(value)
        assertEquals("""{"value":1234567}""", str)

        value = registry.objectMapper.readValue(str)
        assertEquals(DateTimeWrapper(OffsetDateTime.ofInstant(Instant.ofEpochMilli(1234567), ZoneId.of("UTC"))), value)
    }
}