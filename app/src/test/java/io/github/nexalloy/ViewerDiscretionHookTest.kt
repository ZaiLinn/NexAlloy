package io.github.nexalloy

import io.github.nexalloy.morphe.youtube.interaction.viewerdiscretion.adultContentAllowedMethod
import io.github.nexalloy.morphe.youtube.interaction.viewerdiscretion.skipDiscretionDialogMethod
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.luckypray.dexkit.DexKitBridge

class ViewerDiscretionHookTest {
    @Test
    fun `resolve the host Boolean producer instead of framework unboxing`() {
        System.loadLibrary("dexkit")
        DexKitBridge.create("binaries/com.google.android.youtube_21.45.82.apk").use { bridge ->
            with(bridge) {
                val getter = adultContentAllowedMethod()
                assertEquals("Lasql;->n()Ljava/lang/Boolean;", getter.descriptor)
                assertEquals(skipDiscretionDialogMethod().declaredClassName, getter.declaredClassName)
            }
        }
    }
}
