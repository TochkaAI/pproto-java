package ai.tochka.protocol

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.cfg.MapperConfig
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod

/**
 * По-умолчанию jackson убирает префикс is для boolean полей,
 * то есть поле isAdmin будет сериализовано как "admin".
 * Этот класс выключает такое поведение и оставляет префикс.
 */
object CustomPropertyNamingStrategy : PropertyNamingStrategy() {
    override fun nameForGetterMethod(config: MapperConfig<*>, method: AnnotatedMethod, defaultName: String): String {
        return if (method.hasReturnType()
            && (method.rawReturnType === Boolean::class.javaObjectType || method.rawReturnType === Boolean::class.javaPrimitiveType)
            && method.name.startsWith("is")
        ) {
            method.name
        } else {
            super.nameForGetterMethod(config, method, defaultName)
        }
    }
}