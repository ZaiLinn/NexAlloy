package io.github.nexalloy.morphe.music.interaction.remember.repeatstate

import app.morphe.extension.music.patches.RememberRepeatStatePatch
import io.github.nexalloy.morphe.music.misc.playservice.is_9_32_or_greater
import io.github.nexalloy.morphe.music.misc.playservice.versionCheckPatch
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook

/**
 * Runtime port of "Remember repeat state".
 *
 * Upstream overrides the result of the repeat mode comparison inside the watch controller
 * when a new track starts, so the app treats the repeat mode as disabled and skips its
 * reset to repeat-off. The runtime equivalent skips the reset call itself: the call is
 * only ever made from the watch controller, so it is hooked with a scoped hook that only
 * fires while the watch controller method is running.
 */
val rememberRepeatStatePatch = patch(
    name = "Remember repeat state",
    description = "Adds an option to remember the repeat state when playing a new track or playlist.",
) {
    dependsOn(versionCheckPatch)

    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_music_remember_repeat_state"),
    )

    if (!is_9_32_or_greater) return@patch

    val fingerprint = RepeatTrackFingerprint
    val resetMethod = ::repeatResetMethod.method
    val repeatOff = ::repeatOffField.field.get(null)!!

    fingerprint.hookMethod(scopedHook(resetMethod) {
        before {
            // Skip the reset to repeat-off so the repeat state is kept for the new track.
            if (it.args[0] === repeatOff && RememberRepeatStatePatch.rememberRepeatState()) {
                it.result = null
            }
        }
    })
}
