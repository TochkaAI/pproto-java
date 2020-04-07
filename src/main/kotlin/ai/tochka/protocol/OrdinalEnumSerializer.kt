package ai.tochka.protocol

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider

@Suppress("UNCHECKED_CAST")
class OrdinalEnumDeserializer<T : Any>(clazz: Class<T>) : JsonDeserializer<T>() {
    private val values = clazz.getMethod("values").invoke(null) as Array<T>

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): T {
        val ordinal = p.readValueAs(Int::class.java)
        return values[ordinal]
    }
}

@Suppress("UNCHECKED_CAST")
class OrdinalEnumSerializer<T : Any>(clazz: Class<T>) : JsonSerializer<T>() {
    private val values = clazz.getMethod("values").invoke(null) as Array<T>

    override fun serialize(value: T, gen: JsonGenerator, serializers: SerializerProvider) {
        val ordinal = values.indexOf(value)
        gen.writeNumber(ordinal)
    }
}