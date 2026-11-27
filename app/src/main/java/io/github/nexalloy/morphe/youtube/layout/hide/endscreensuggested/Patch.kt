package io.github.nexalloy.morphe.youtube.layout.hide.endscreensuggested

import app.morphe.extension.youtube.patches.HideEndScreenSuggestedVideoPatch
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch

val HideEndScreenSuggestedVideo = patch(
    name = "Hide end screen suggested video",
    description = "Adds an option to hide the suggested video at the end of videos.",
) {
    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_hide_end_screen_suggested_video", summary = true),
    )

    val endScreenShow = ::endScreenShowMethod.method
    val autoNavStatus = ::autoNavStatusMethod.method

    // The autoplay controller field of the end screen method's declaring class.
    // Anchor (21.45.82): `aroa.D` (type odg).
    val autoNavControllerField = endScreenShow.declaringClass.declaredFields
        .first { it.type == autoNavStatus.declaringClass }
        .apply { isAccessible = true }

    // Upstream inserts a conditional return-void at the start of the end screen method,
    // which skips the end screen when the setting is on and autoplay is turned off.
    // At runtime the method body is skipped by setting the result of the void method.
    endScreenShow.hookMethod {
        before {
            if (!HideEndScreenSuggestedVideoPatch.hideEndScreenSuggestedVideo()) {
                return@before
            }

            // This reference checks whether autoplay is turned on.
            val autoNav = autoNavControllerField.get(it.thisObject)

            // Hide suggested video end screen only when autoplay is turned off.
            if (autoNav != null && autoNavStatus.invoke(autoNav) != true) {
                it.result = null
            }
        }
    }
}
