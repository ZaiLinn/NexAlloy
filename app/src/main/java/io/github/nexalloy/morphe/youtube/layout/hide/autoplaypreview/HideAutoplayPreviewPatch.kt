package io.github.nexalloy.morphe.youtube.layout.hide.autoplaypreview

import app.morphe.extension.youtube.patches.HideAutoplayPreviewPatch
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.layout.hide.playeroverlaybuttons.LayoutConstructorFingerprint
import io.github.nexalloy.morphe.youtube.layout.hide.playeroverlaybuttons.layoutConstructorViewStubShowMethod
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.morphe.youtube.misc.playservice.is_21_39_or_greater
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook
import java.lang.reflect.Method

val autoplayPreviewShowMethod = findMethodDirect {
    layoutConstructorViewStubShowMethod("autonav_preview_stub")
}

val HideAutoplayPreview = patch(
    name = "Hide autoplay preview",
    description = "Adds an option to hide the autoplay preview at the end of videos.",
) {
    dependsOn(VersionCheck)

    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_hide_autoplay_preview", summary = true)
    )

    if (is_21_39_or_greater) {
        // Upstream jumps over the code that inflates the autoplay preview view stub when it is
        // hidden. At runtime the inflate call of the view stub is scoped-hooked and skipped
        // instead.
        val skippedResult = skippedResult(::autoplayPreviewShowMethod.method)
        LayoutConstructorFingerprint.hookMethod(scopedHook(::autoplayPreviewShowMethod.method) {
            before {
                if (innerDepth == 0 && HideAutoplayPreviewPatch.hideAutoplayPreview()) {
                    it.result = skippedResult
                }
            }
        })
    }
}

/** The default value of a method return type, used to skip the original method body. */
private fun skippedResult(method: Method): Any? = when (method.returnType) {
    java.lang.Boolean.TYPE -> false
    java.lang.Byte.TYPE, java.lang.Character.TYPE, java.lang.Short.TYPE, Integer.TYPE -> 0
    java.lang.Long.TYPE -> 0L
    java.lang.Float.TYPE -> 0f
    java.lang.Double.TYPE -> 0.0
    else -> null
}
