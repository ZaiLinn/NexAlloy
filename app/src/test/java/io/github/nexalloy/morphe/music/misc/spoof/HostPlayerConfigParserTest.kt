package io.github.nexalloy.morphe.music.misc.spoof

import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class HostPlayerConfigParserTest {
    class Registry {
        companion object {
            val instance = Registry()

            @JvmStatic
            fun getGeneratedRegistry() = instance
        }
    }

    class Config(val bytes: ByteArray = byteArrayOf()) {
        fun createBuilder() = Builder()

        companion object {
            @JvmField
            val defaultInstance = Config()
        }
    }

    class Builder {
        fun mergeFrom(bytes: ByteArray, registry: Registry): Builder {
            assertSame(Registry.instance, registry)
            // The parser must build from the object returned by mergeFrom.
            return Builder().also { it.bytes = bytes.copyOf() }
        }

        private var bytes = byteArrayOf()
        fun build() = Config(bytes)
    }

    @Test
    fun `uses the default message as receiver and builds independent configs with the host registry`() {
        val parser = HostPlayerConfigParser(
            Config::class.java.getDeclaredField("defaultInstance"),
            Config::class.java.getDeclaredMethod("createBuilder"),
            Builder::class.java.getDeclaredMethod("mergeFrom", ByteArray::class.java, Registry::class.java),
            Builder::class.java.getDeclaredMethod("build"),
            Registry::class.java.getDeclaredMethod("getGeneratedRegistry")
        )
        val replacement = parser.parse(byteArrayOf(1, 2, 3)) as Config
        val originalCopy = parser.parse(byteArrayOf(4, 5)) as Config

        assertContentEquals(byteArrayOf(1, 2, 3), replacement.bytes)
        assertContentEquals(byteArrayOf(4, 5), originalCopy.bytes)
        assertContentEquals(byteArrayOf(), Config.defaultInstance.bytes)
        assertNotSame(replacement, originalCopy)
        assertNotSame(Config.defaultInstance, replacement)
    }
}
