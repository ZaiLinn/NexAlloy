package io.github.nexalloy.morphe.youtube.layout.hide.relatedvideooverlay

import app.morphe.extension.youtube.patches.HideRelatedVideoOverlayPatch
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch

val HideRelatedVideoOverlay = patch(
    name = "Hide related video overlay",
    description = "Adds an option to hide the related video overlay shown when swiping up in fullscreen.",
) {
    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_hide_player_related_videos_overlay", summary = true)
    )

    // Upstream inserts a conditional return-void at the start of the method.
    // At runtime the method body is skipped by setting the result of the void method.
    RelatedEndScreenResultsFingerprint.hookMethod {
        before {
            if (HideRelatedVideoOverlayPatch.hideRelatedVideoOverlay()) {
                it.result = null
            }
        }
    }
}
