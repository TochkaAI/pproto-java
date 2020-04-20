package ai.tochka.protocol

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import java.time.OffsetDateTime

class UnixOffsetDateTimeSerializer : JsonSerializer<OffsetDateTime>() {
    override fun serialize(value: OffsetDateTime, gen: JsonGenerator, serializers: SerializerProvider) {
        val long = value.toInstant().toEpochMilli()
        gen.writeNumber(long)
    }
}