package io.github.nexalloy.revanced.meta.filter

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Ported from the reference implementation (dev.threads.sieve.FilterTest):
 * character evidence and ambiguity handling of the OpenCC STCharacters dictionary.
 */
class ScriptFilterTest {
    private fun script(): ScriptFilter {
        // The test working directory is the app module; fall back to the repo root.
        var path = Path.of("src/main/assets/STCharacters.txt")
        if (!Files.exists(path)) path = Path.of("app/src/main/assets/STCharacters.txt")
        return ScriptFilter(Files.newInputStream(path))
    }

    @Test
    fun scriptEvidenceAndAmbiguity() {
        val f = script()
        // Simplified-only characters hit the simplified filter.
        assertTrue(f.blocks("这是一个测试", true, false))
        assertFalse(f.blocks("這是一個測試", true, false))
        // Traditional-only characters hit the traditional filter.
        assertTrue(f.blocks("這是一個測試", false, true))
        assertFalse(f.blocks("这是一个测试", false, true))
        // Shared characters alone are ambiguous and never filter ("回家", "今天下雨").
        assertFalse(f.blocks("今天下雨", true, false))
        assertFalse(f.blocks("今天下雨", false, true))
        assertFalse(f.blocks("回家后去吃面", false, true))
        assertFalse(f.blocks("繁體回覆", true, false))
        assertFalse(f.blocks("回家", true, false))
        // Blocking both scripts blocks any Han character.
        assertTrue(f.blocks("今天下雨", true, true))
        assertFalse(f.blocks("Hello 🙂 123", true, true))
        assertFalse(f.blocks(null, true, true))
        assertFalse(f.blocks("这是测试", false, false))
        // Mixed text hits whichever script it contains.
        assertTrue(f.blocks("这是混合測試", true, false))
        assertTrue(f.blocks("这是混合測試", false, true))
        assertTrue(f.blocks("𠀀", true, true))
    }
}
