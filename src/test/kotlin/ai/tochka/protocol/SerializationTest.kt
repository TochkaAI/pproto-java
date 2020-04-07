package ai.tochka.protocol

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
        clientConn.registerContentType(MessageType.COMMAND, "enum", EnumWrapper::class.java)

        var value = EnumWrapper(TestEnum.FIRST)
        var str = clientConn.objectMapper.writeValueAsString(value)
        assertEquals("""{"enum":0}""", str)

        value = EnumWrapper(TestEnum.SECOND)
        str = clientConn.objectMapper.writeValueAsString(value)
        assertEquals("""{"enum":1}""", str)
    }

    @Test
    fun testDateTime() {
        clientConn.registerContentType(MessageType.COMMAND, "enum", DateTimeWrapper::class.java)

        var value = DateTimeWrapper(OffsetDateTime.ofInstant(Instant.ofEpochSecond(100), ZoneId.of("UTC")))
        var str = clientConn.objectMapper.writeValueAsString(value)
        assertEquals("""{"value":100000}""", str)

        value = DateTimeWrapper(OffsetDateTime.ofInstant(Instant.ofEpochSecond(1234567), ZoneId.of("UTC")))
        str = clientConn.objectMapper.writeValueAsString(value)
        assertEquals("""{"value":1234567000}""", str)
    }
}