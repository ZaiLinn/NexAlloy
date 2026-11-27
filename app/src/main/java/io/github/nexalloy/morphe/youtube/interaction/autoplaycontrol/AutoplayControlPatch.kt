package io.github.nexalloy.morphe.youtube.interaction.autoplaycontrol

import app.morphe.extension.youtube.patches.AutoplayControlPatch
import app.morphe.extension.youtube.videoplayer.AutoplayButton
import io.github.nexalloy.morphe.shared.misc.settings.preference.InputType
import io.github.nexalloy.morphe.shared.misc.settings.preference.ListPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.TextPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import io.github.nexalloy.morphe.youtube.interaction.playlistautoplay.disable.DisablePlaylistAutoplay
import io.github.nexalloy.morphe.youtube.layout.player.buttons.addPlayerBottomButton
import io.github.nexalloy.morphe.youtube.layout.player.buttons.playerOverlayButtonsHook
import io.github.nexalloy.morphe.youtube.misc.playercontrols.LegacyPlayerControls
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.youtube.video.information.VideoInformationPatch
import io.github.nexalloy.morphe.youtube.video.videoid.VideoId
import io.github.nexalloy.morphe.youtube.video.videoid.hookBackgroundPlayVideoId
import io.github.nexalloy.patch

val AutoplayControl = patch(
    name = "Autoplay control",
    description = "Adds a player button and a rule for the next video: playlists and queues go on, " +
            "a Mix stops after a number of videos, a suggested video never plays.",
) {
    dependsOn(
        DisablePlaylistAutoplay,
        playerOverlayButtonsHook,
        LegacyPlayerControls,
        VideoInformationPatch,
        VideoId,
    )

    PreferenceScreen.PLAYER.addPreferences(
        noTitleUnsortedPreferenceCategory(
            SwitchPreference("morphe_autoplay_control", summary = true),
            ListPreference("morphe_autoplay_mode"),
            TextPreference("morphe_autoplay_mix_stop_after", inputType = InputType.NUMBER),
        )
    )

    addPlayerBottomButton(AutoplayButton::initializeButton)

    // Mixes are mostly listened to in the background, where the regular video ID hook is not called.
    hookBackgroundPlayVideoId(AutoplayControlPatch::newVideoLoaded)
}
