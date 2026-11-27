package io.github.nexalloy.morphe.youtube.interaction.reloadbutton

import app.morphe.extension.youtube.videoplayer.ReloadVideoButton
import io.github.nexalloy.R
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.layout.buttons.overlay.addPlayerOverlayPreferences
import io.github.nexalloy.morphe.youtube.misc.loadvideo.loadVideoHookPatch
import io.github.nexalloy.morphe.youtube.misc.playercontrols.ControlInitializer
import io.github.nexalloy.morphe.youtube.misc.playercontrols.LegacyPlayerControls
import io.github.nexalloy.morphe.youtube.misc.playercontrols.addTopControl
import io.github.nexalloy.morphe.youtube.misc.playercontrols.initializeTopControl
import io.github.nexalloy.morphe.youtube.video.information.VideoInformationPatch
import io.github.nexalloy.patch

val ReloadVideoButtonPatch = patch(
    name = "Reload video",
    description = "Adds an option to display reload video button in the video player.",
) {
    dependsOn(
        LegacyPlayerControls,
        VideoInformationPatch,
        loadVideoHookPatch,
    )

    addPlayerOverlayPreferences(
        SwitchPreference("morphe_reload_video_button")
    )

    // Upstream copies the player button icons into the resources of the patched app.
    // The module packages them with CopyResourcesTask (see app/build.gradle.kts)
    // and inflates its own button layout into the player controls.
    addTopControl(
        R.layout.morphe_reload_video_button,
        R.id.morphe_reload_video_button,
        R.id.morphe_reload_video_button
    )

    initializeTopControl(
        ControlInitializer(
            R.id.morphe_reload_video_button,
            ReloadVideoButton::initializeLegacyButton
        )
    )
}
