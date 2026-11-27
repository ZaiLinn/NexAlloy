package io.github.nexalloy.morphe.music.layout.captions

import app.morphe.extension.music.patches.DisableAutoCaptionsPatch
import io.github.nexalloy.hookMethod
import io.github.nexalloy.scopedHook
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.patch

val AutoCaptions = patch(
    name = "Disable auto captions",
    description = "Adds an option to disable captions from being automatically enabled.",
) {
    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_music_disable_auto_captions", summary = true)
    )

    val reasonField = ::captionSelectionReasonField.field
    val defaultTrackField = ::hasDefaultTrackField.field
    val ownerFields = ::hasDefaultTrackOwnerFields.fieldList
    val trackListsField = ownerFields.first().apply { isAccessible = true }
    val trackSelectionField = ownerFields.last().apply { isAccessible = true }
    val isDisableOption = ::subtitleTrackIsDisableOptionMethod.method

    // Do not use the default caption track of the caption tracks.
    // Upstream transforms the default track flag where the caption track event reads it.
    // At runtime the same is achieved by adapting the stored flag for the duration of
    // the caption track event, which restores the original flag afterwards.
    SetCaptionTrackFingerprint.hookMethod {
        before {
            val trackLists = trackListsField.get(it.thisObject) ?: return@before
            val trackSelection = trackSelectionField.get(trackLists) ?: return@before
            val original = defaultTrackField.getBoolean(trackSelection)
            val overridden = DisableAutoCaptionsPatch.disableDefaultCaptionTrack(original)
            if (overridden != original) {
                defaultTrackField.setBoolean(trackSelection, overridden)
                it.setObjectExtra("morphe_originalDefaultTrack", original)
            }
        }
        after {
            val original = it.getObjectExtra("morphe_originalDefaultTrack") ?: return@after
            it.setObjectExtra("morphe_originalDefaultTrack", null)
            trackListsField.get(it.thisObject)?.let { trackLists ->
                trackSelectionField.get(trackLists)?.let { trackSelection ->
                    defaultTrackField.setBoolean(trackSelection, original as Boolean)
                }
            }
        }
    }

    // Use the option to disable captions when captions are enabled automatically.
    // Upstream transforms the result of the first disable option check of the caption
    // track event. At runtime the same is achieved by transforming the result of the
    // check while the caption track event runs it, only for its first call.
    SetCaptionTrackFingerprint.hookMethod(
        scopedHook(isDisableOption to {
            after {
                if (outerParam.getObjectExtra("morphe_autoCaptionsHandled") == true) return@after
                outerParam.setObjectExtra("morphe_autoCaptionsHandled", true)
                it.result = DisableAutoCaptionsPatch.disableAutoCaptions(
                    it.result as Boolean,
                    reasonField.getInt(outerParam.args[0])
                )
            }
        })
    )
}
