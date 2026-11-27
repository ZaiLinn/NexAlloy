package io.github.nexalloy.morphe.youtube.layout.captions.language

import app.morphe.extension.youtube.patches.CaptionsInPipPatch
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook
import io.github.nexalloy.morphe.youtube.layout.captions.core.settingsMenuCaptionGroup

val CaptionsInPip = patch(
    name = "Captions in PiP",
    description = "Adds an option to show captions in picture-in-picture mode.",
) {
    dependsOn(VersionCheck)

    settingsMenuCaptionGroup.addAll(listOf(
        SwitchPreference("morphe_captions_in_pip"),
    ))

    CaptionBridge.apply {
        overlayPipField = ::playerOverlayPipField.field.accessible()
        overlayNameMethodName = ::captionsOverlayNameMethod.method.name
        presenterTrackField = ::captionPresenterTrackField.field.accessible()
    }

    // 20.29+ gives overlays a player state that includes whether picture-in-picture is active.
    // Report it as inactive to the captions overlay, by changing what the captions overlay
    // is asked with, while the state itself stays unchanged.
    // Overlays can be wrapped; the captions view itself need not implement the
    // visibility interface. Hook the concrete ABI implementations in the host.
    val visibilityHooks = ::overlayVisibilityImplementationMethods.dexMethodList.map { implementation ->
        implementation.toMember() to (fun io.github.nexalloy.HookDsl<io.github.nexalloy.IScopedHookCallback>.() {
        before {
            if (innerDepth != 0) return@before
            val overlay = it.thisObject ?: return@before
            val playerState = it.args[0] ?: return@before

            // While in picture-in-picture, YouTube hides most player overlays.
            if (CaptionsInPipPatch.isPipActive(CaptionBridge.overlayName(overlay))) return@before
            if (!CaptionBridge.overlayPipField.getBoolean(playerState)) return@before

            it.setObjectExtra("captionsInPipState", playerState)
            CaptionBridge.overlayPipField.setBoolean(playerState, false)
        }

        after {
            val playerState = it.getObjectExtra("captionsInPipState") ?: return@after

            CaptionBridge.overlayPipField.setBoolean(playerState, true)
        }
        })
    }
    val overlayVisibilityHook = scopedHook(*visibilityHooks.toTypedArray())
    ::overlayVisibilityCallers.dexMethodList.forEach { caller ->
        caller.hookMethod(overlayVisibilityHook)
    }

    // The current caption track can be set again when entering or leaving picture-in-picture,
    // which clears the caption on screen until the next one. Only set the track if it changed.
    CaptionTrackEventFingerprint.hookMethod(scopedHook(::captionEventSetTrackMethod.method) {
        before {
            if (innerDepth != 0) return@before
            val presenter = it.thisObject ?: return@before
            val track = it.args[0] ?: return@before

            val currentTrack = runCatching {
                CaptionBridge.presenterTrackField.get(presenter)
            }.getOrNull()

            if (CaptionsInPipPatch.isCurrentTrack(track, currentTrack)) {
                // Skip the original set call, the track is already shown.
                it.result = null
            }
        }
    })
}
