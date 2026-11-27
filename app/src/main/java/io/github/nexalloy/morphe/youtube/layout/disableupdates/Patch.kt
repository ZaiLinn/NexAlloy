package io.github.nexalloy.morphe.youtube.layout.disableupdates

import app.morphe.extension.youtube.patches.DisableLayoutUpdatesPatch
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch

val DisableLayoutUpdates = patch(
    name = "Disable layout updates",
    description = "Adds an option to disable server side layout updates and use an older UI.",
) {
    PreferenceScreen.GENERAL.addPreferences(
        SwitchPreference("morphe_disable_layout_updates", summary = true),
    )

    // Upstream inserts a call to the extension at the start of the Cronet header add method,
    // which returns an empty value for the layout config headers (X-Youtube-Cold-Config-Data,
    // X-Youtube-Cold-Hash-Data, X-Youtube-Hot-Hash-Data) or the original value otherwise.
    // At runtime the same method is hooked and the header value argument is replaced
    // with the extension result before the original method runs.
    CronetHeaderFingerprint.hookMethod {
        before {
            it.args[1] = DisableLayoutUpdatesPatch.disableLayoutUpdates(
                it.args[0] as String,
                it.args[1] as String,
            )
        }
    }
}
