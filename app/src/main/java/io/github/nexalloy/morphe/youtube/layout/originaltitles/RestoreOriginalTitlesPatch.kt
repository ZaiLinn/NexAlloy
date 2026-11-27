package io.github.nexalloy.morphe.youtube.layout.originaltitles

import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch

internal val restoreOriginalTitlesPatch = patch(
    name = "Restore original titles",
    description = "Adds an option to show original video titles, video descriptions, and channel descriptions.",
) {
    dependsOn(videoTitlesHookPatch)
    PreferenceScreen.FEED.addPreferences(SwitchPreference("morphe_restore_original_titles", summary = true))
}
