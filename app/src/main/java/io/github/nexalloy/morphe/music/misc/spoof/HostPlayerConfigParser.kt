package io.github.nexalloy.morphe.music.misc.spoof

import java.lang.reflect.Field
import java.lang.reflect.Method

/** Parses config bytes using the host's protobuf classes and extension registry. */
internal class HostPlayerConfigParser(
    private val defaultInstance: Field,
    private val createBuilder: Method,
    private val mergeFrom: Method,
    private val build: Method,
    private val generatedRegistry: Method,
) {
    fun parse(bytes: ByteArray): Any {
        val builder = createBuilder.invoke(defaultInstance.get(null))
        val merged = mergeFrom.invoke(builder, bytes, generatedRegistry.invoke(null))
        return build.invoke(merged)!!
    }
}
