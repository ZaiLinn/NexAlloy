package io.github.nexalloy.morphe.youtube.video.speed.replayslower

import app.morphe.extension.shared.settings.preference.SeekBarPreference
import app.morphe.extension.youtube.videoplayer.ReplayVideoButton
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.morphe.shared.misc.settings.preference.InputType
import io.github.nexalloy.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.TextPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import io.github.nexalloy.morphe.youtube.interaction.swipecontrols.SwipeControls
import io.github.nexalloy.morphe.youtube.layout.buttons.overlay.PlayerOverlayButtonsSettings
import io.github.nexalloy.morphe.youtube.layout.buttons.overlay.addPlayerOverlayPreferences
import io.github.nexalloy.morphe.youtube.layout.player.buttons.addPlayerBottomButton
import io.github.nexalloy.morphe.youtube.layout.player.buttons.playerOverlayButtonsHook
import io.github.nexalloy.morphe.youtube.misc.playercontrols.LegacyPlayerControls
import io.github.nexalloy.morphe.youtube.video.information.SeekFingerprint
import io.github.nexalloy.morphe.youtube.video.information.SeekRelativeFingerprint
import io.github.nexalloy.morphe.youtube.video.information.VideoInformationPatch
import io.github.nexalloy.morphe.youtube.video.information.onCreateHook
import io.github.nexalloy.patch

val ReplaySlower = patch(
    name = "Replay slower button",
    description = "Adds options to rewind and play part of the video at a slower speed, " +
            "with a player button or by swiping left on the video.",
) {
    dependsOn(
        VideoInformationPatch,
        PlayerOverlayButtonsSettings,
        playerOverlayButtonsHook,
        LegacyPlayerControls,
        SwipeControls,
    )

    setExtensionIsPatchIncluded(ReplayVideoButton::class.java)

    addPlayerOverlayPreferences(
        noTitleUnsortedPreferenceCategory(
            SwitchPreference("morphe_replay_video_button", summary = true),
            SwitchPreference("morphe_replay_video_swipe", summary = true),
            NonInteractivePreference(
                "morphe_replay_video_seconds",
                tag = SeekBarPreference::class.java,
                selectable = true,
            ),
            NonInteractivePreference(
                "morphe_replay_video_slow_seconds",
                tag = SeekBarPreference::class.java,
                selectable = true,
            ),
            TextPreference("morphe_replay_video_speed", inputType = InputType.NUMBER_DECIMAL),
        )
    )

    addPlayerBottomButton(ReplayVideoButton::initializeButton)

    // A seek by the user ends the replay.
    listOf(
        SeekFingerprint,
        SeekRelativeFingerprint,
    ).forEach { fingerprint ->
        fingerprint.hookMethod {
            before { ReplayVideoButton.onSeek() }
        }
    }

    // Restore the speed before the next video plays.
    onCreateHook { controller -> ReplayVideoButton.newVideoStarted(controller) }
}
