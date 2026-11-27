package io.github.nexalloy.morphe.youtube.layout.captions.language

import android.view.View
import app.morphe.extension.youtube.patches.CopyTranscriptPatch
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.layout.flyout.flyoutPatch
import io.github.nexalloy.morphe.youtube.misc.engagement.EngagementPanelHook
import io.github.nexalloy.morphe.youtube.video.information.VideoInformationPatch
import io.github.nexalloy.patch
import io.github.nexalloy.morphe.youtube.layout.captions.core.settingsMenuCaptionGroup

val CopyTranscript = patch(
    name = "Copy transcript",
    description = "Adds a button to the transcript panel that copies the transcript.",
) {
    dependsOn(
        EngagementPanelHook,
        VideoInformationPatch,
        flyoutPatch,
    )

    settingsMenuCaptionGroup.addAll(listOf(
        SwitchPreference("morphe_copy_transcript", summary = true),
        SwitchPreference("morphe_copy_transcript_timestamps", summary = true),
        SwitchPreference("morphe_copy_transcript_shorts_flyout", summary = true),
    ))

    // Adds the copy button to the Shorts flyout menus.
    setExtensionIsPatchIncluded(CopyTranscriptPatch::class.java)

    // Panels reuse their header, so this is called for every engagement panel.
    // The panel is opened after its header is set up.
    EngagementPanelHeaderFingerprint.hookMethod {
        before {
            CopyTranscriptPatch.onPanelHeaderSetUp(it.args[0] as? View)
        }
    }
}
