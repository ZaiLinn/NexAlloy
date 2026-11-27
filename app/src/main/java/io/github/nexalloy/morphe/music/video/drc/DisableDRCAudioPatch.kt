package io.github.nexalloy.morphe.music.video.drc

import app.morphe.extension.shared.patches.DisableDRCAudioPatch
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.music.misc.playservice.is_9_20_or_greater
import io.github.nexalloy.morphe.music.misc.playservice.versionCheckPatch
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.patch

val DisableDRCAudio = patch(
    name = "Disable DRC audio",
    description = "Adds options to disable DRC (Dynamic Range Compression) audio and loudness normalization."
) {
    dependsOn(versionCheckPatch)

    // Upstream puts these on the VIDEO screen, which the music settings of this module
    // do not have; the player screen is the closest match.
    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_disable_drc_audio_stream", summary = true),
        SwitchPreference("morphe_disable_loudness_normalization", summary = true),
    )

    // Audio formats can have a DRC version, which the player plays if DRC audio is enabled.
    // Media requests also include the preference. Both get it from the same method,
    // which all the places that create the request preferences call.
    // Upstream injects an early return of false at the start of the method.
    ::isDrcAudioEnabledMethod.hookMethod {
        before {
            if (DisableDRCAudioPatch.disableDrcAudio()) {
                it.result = false
            }
        }
    }

    // Nullifying the normalization gain disables the normalization.
    // Upstream injects a const 0 into the normalization parameter at the start of the method.
    if (is_9_20_or_greater) {
        ::volumeNormalizationConfigMethod.hookMethod {
            before {
                if (DisableDRCAudioPatch.disableLoudnessNormalization()) {
                    it.args[2] = 0f
                }
            }
        }
    }
}
