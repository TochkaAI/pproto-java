package ai.tochka.protocol

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

class UnixOffsetDateTimeDeserializer : JsonDeserializer<OffsetDateTime>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): OffsetDateTime {
        val long = p.readValueAs(Long::class.java)
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(long), ZoneId.of("UTC"))
    }
}