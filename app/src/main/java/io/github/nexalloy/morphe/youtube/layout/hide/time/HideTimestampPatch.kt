package io.github.nexalloy.morphe.youtube.layout.hide.time

import app.morphe.extension.youtube.patches.HideTimestampPatch
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch

val HideTimestamp = patch(
    name = "Hide timestamp",
    description = "Adds an option to hide the timestamp in the bottom left of the video player.",
) {
    PreferenceScreen.SEEKBAR.addPreferences(
        SwitchPreference("morphe_hide_timestamp"),
    )

    // Upstream inserts a conditional return-void at the start of the time counter method.
    // At runtime the method body is skipped by setting the result of the void method.
    TimeCounterFingerprint.hookMethod {
        before {
            if (HideTimestampPatch.hideTimestamp()) {
                it.result = null
            }
        }
    }
}
