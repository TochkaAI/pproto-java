package ai.tochka.protocol

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer

@Suppress("UNCHECKED_CAST")
class OrdinalEnumDeserializer<T : Any>(clazz: Class<T>) : JsonDeserializer<T>() {
    private val values = clazz.getMethod("values").invoke(null) as Array<T>

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): T {
        val ordinal = p.readValueAs(Int::class.java)
        return values[ordinal]
    }
}