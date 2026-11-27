package io.github.nexalloy.morphe.youtube.interaction.playlistautoplay.disable

import app.morphe.extension.youtube.patches.DisablePlaylistAutoplayPatch
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.interaction.playlistautoplay.NativeNavigationStateFingerprint
import io.github.nexalloy.morphe.youtube.layout.playlistautoplay.navigationIntentHook
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch

/**
 * Captures the playlist repeat mode, as YouTube also uses the AUTOPLAY navigation
 * to repeat the current video.
 */
internal val playlistRepeatModeHook = patch {
    NativeNavigationStateFingerprint.hookMethod {
        before {
            DisablePlaylistAutoplayPatch.setRepeatMode(it.args[2] as Int)
        }
    }
}

val DisablePlaylistAutoplay = patch(
    name = "Disable playlist autoplay",
    description = "Adds an option to stop a playlist from automatically advancing to the next video.",
) {
    dependsOn(playlistRepeatModeHook)

    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_disable_playlist_autoplay", summary = true),
    )

    navigationIntentHook(DisablePlaylistAutoplayPatch::shouldSkipPlaylistAutoplay)
}
