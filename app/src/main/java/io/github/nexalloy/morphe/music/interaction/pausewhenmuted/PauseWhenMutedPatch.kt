package io.github.nexalloy.morphe.music.interaction.pausewhenmuted

import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import app.morphe.extension.music.patches.PauseWhenMutedPatch
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.hookMethod
import io.github.nexalloy.patch

/**
 * Runtime port of "Pause when muted".
 *
 * Upstream injects calls right before YouTube Music runs MediaSession.setMetadata and
 * MediaSession.setPlaybackState. The app only ever calls both from its own media session
 * (the media compat forwarding of its player), so hooking the framework methods in the
 * YouTube Music process is equivalent and keeps the session object available, which the
 * extension needs to pause and resume the player.
 */
val pauseWhenMutedPatch = patch(
    name = "Pause when muted",
    description = "Adds an option to pause playback when the system volume is muted, " +
            "and to resume it when the volume is raised.",
) {
    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_music_pause_when_muted", summary = true),
    )

    runCatching {
        val setMetadata = MediaSession::class.java
            .getDeclaredMethod("setMetadata", MediaMetadata::class.java)
        setMetadata.hookMethod {
            before {
                PauseWhenMutedPatch.onSetMetadata(
                    it.thisObject as MediaSession,
                    it.args[0] as MediaMetadata?
                )
            }
        }
    }

    runCatching {
        val setPlaybackState = MediaSession::class.java
            .getDeclaredMethod("setPlaybackState", PlaybackState::class.java)
        setPlaybackState.hookMethod {
            before {
                PauseWhenMutedPatch.onSetPlaybackState(it.args[0] as PlaybackState?)
            }
        }
    }
}
