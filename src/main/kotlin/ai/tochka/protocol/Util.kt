package ai.tochka.protocol

import java.lang.reflect.Method

internal fun commandIndex(method: Method): Int? {
    val commandParams = method.parameters
        .withIndex()
        .filter { it.value.annotations.isEmpty() }
    if (commandParams.size > 1) {
        throw RuntimeException("Method [$method] has more than one body parameters")
    }
    return commandParams.singleOrNull()?.index
}

internal fun tagIndex(method: Method): Int? {
    val tagParams = method.parameters
        .withIndex()
        .filter { it.value.isAnnotationPresent(Tag::class.java) }
    if (tagParams.size > 1) {
        throw RuntimeException("Method [$method] has more than one @ai.tochka.Tag parameters")
    }
    val tagParam = tagParams.singleOrNull()
    if (tagParam != null && tagParam.value.type != Long::class.javaPrimitiveType) {
        throw RuntimeException("Parameter [${tagParam.value}] has wrong class, must be Long")
    }
    return tagParam?.index
}