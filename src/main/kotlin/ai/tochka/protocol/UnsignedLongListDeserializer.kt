package ai.tochka.protocol

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer

class UnsignedLongListDeserializer : JsonDeserializer<List<Long?>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): List<Long?> {
        if (p.currentToken !== JsonToken.START_ARRAY) {
            throw JsonParseException(p, "Start array expected")
        }
        var token = p.nextToken()
        val list = ArrayList<Long?>()
        while (token != JsonToken.END_ARRAY) {
            if (token == JsonToken.VALUE_NULL) {
                list.add(null)
            } else {
                val str = p.readValueAs(String::class.java)
                val long = java.lang.Long.parseUnsignedLong(str)
                list.add(long)
            }
            token = p.nextToken()
        }
        return list
    }
}