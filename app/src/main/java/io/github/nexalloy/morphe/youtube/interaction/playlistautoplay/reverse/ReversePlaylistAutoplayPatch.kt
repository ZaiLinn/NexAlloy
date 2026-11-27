package io.github.nexalloy.morphe.youtube.interaction.playlistautoplay.reverse

import app.morphe.extension.youtube.patches.ReversePlaylistAutoplayPatch
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.interaction.playlistautoplay.disable.playlistRepeatModeHook
import io.github.nexalloy.morphe.youtube.interaction.playlistautoplay.previousNavigationRequestField
import io.github.nexalloy.morphe.youtube.interaction.playlistautoplay.queueNavigationIntentField
import io.github.nexalloy.morphe.youtube.interaction.playlistautoplay.queueNavigationItemMethod
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.youtube.video.information.VideoInformationPatch
import io.github.nexalloy.patch

/**
 * Lets the extension autoplay the previous video of a playlist in place of the next video.
 */
internal val reversePlaylistAutoplayHook = patch {
    dependsOn(
        playlistRepeatModeHook,
        VideoInformationPatch,
    )

    val previousRequest = ::previousNavigationRequestField.field
    val requestIntent = ::queueNavigationIntentField.field

    // A playlist is loaded into the playback queue, which picks the next video for AUTOPLAY and
    // the previous video for PREVIOUS, and repeats the playlist in both directions.
    // Swapping the request only here keeps the rest of the AUTOPLAY handling.
    ::queueNavigationItemMethod.hookMethod {
        before {
            val intent = requestIntent.get(it.args[0]) as? Enum<*> ?: return@before
            if (ReversePlaylistAutoplayPatch.shouldPlayPrevious(intent)) {
                it.args[0] = previousRequest.get(null)
            }
        }
    }
}

val ReversePlaylistAutoplay = patch(
    name = "Reverse playlist autoplay",
    description = "Adds an option to autoplay playlists in reverse order, from the last video to the first.",
) {
    dependsOn(reversePlaylistAutoplayHook)

    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_reverse_playlist_autoplay", summary = true),
    )
}
