package io.github.nexalloy.morphe.youtube.layout.captions.language

import app.morphe.extension.youtube.patches.TranscriptPatch as ExtensionTranscriptPatch
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.misc.contexthook.Endpoint
import io.github.nexalloy.morphe.youtube.misc.contexthook.addClientVersionHook
import io.github.nexalloy.morphe.youtube.misc.contexthook.clientContextHookPatch
import io.github.nexalloy.patch
import io.github.nexalloy.morphe.youtube.layout.captions.core.settingsMenuCaptionGroup

val Transcript = patch(
    name = "Fix transcript",
    description = "Adds an option to fix an issue where transcript is unavailable due to a precondition check failure.",
) {
    dependsOn(clientContextHookPatch)

    settingsMenuCaptionGroup.addAll(listOf(
        SwitchPreference("morphe_fix_transcript", summary = true),
    ))

    addClientVersionHook(Endpoint.TRANSCRIPT) { version ->
        ExtensionTranscriptPatch.getTranscriptAppVersionOverride(version ?: "")
    }
}
