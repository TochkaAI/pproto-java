package ai.tochka.protocol

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider

class UnsignedLongListSerializer : JsonSerializer<List<Long?>>() {
    override fun serialize(value: List<Long?>, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeStartArray()
        for (item in value) {
            if (item != null) {
                gen.writeNumber(java.lang.Long.toUnsignedString(item))
            } else {
                gen.writeNull()
            }
        }
        gen.writeEndArray()
    }
}