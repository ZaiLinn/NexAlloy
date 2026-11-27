package io.github.nexalloy.morphe.youtube.video.drc

import app.morphe.extension.shared.patches.DisableDRCAudioPatch
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.morphe.youtube.misc.playservice.is_21_17_or_greater
import io.github.nexalloy.morphe.youtube.misc.playservice.is_21_19_or_greater
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch

/**
 * The method that returns if DRC audio is enabled, resolved from the first
 * [PlaybackAgnosticUserPreferencesFingerprint] match. All the places that create the
 * playback preferences of the media requests call it.
 */
internal val isDrcAudioEnabledMethod = findMethodDirect {
    PlaybackAgnosticUserPreferencesFingerprint.matchAll()
        .first().instructionMatches.first().instruction.methodRef!!
}

/**
 * The streaming URL builder that takes the volume normalization value as its second parameter.
 * The upstream fingerprint requires at least 3 parameters, which is not expressible in the
 * DexKit query and is filtered here.
 */
internal val volumeNormalizationConfigMethod = findMethodDirect {
    VolumeNormalizationConfigFingerprint.matchAll()
        .single { it.method.paramTypes.size >= 3 }.method
}

private fun zeroOf(value: Any?): Any? = when (value) {
    is Long -> 0L
    is Int -> 0
    is Short -> 0.toShort()
    is Byte -> 0.toByte()
    is Float -> 0f
    is Double -> 0.0
    is Boolean -> false
    is Char -> ' '
    else -> value
}

val DisableDRCAudio = patch(
    name = "Disable DRC audio",
    description = "Adds options to disable DRC (Dynamic Range Compression) audio and loudness normalization."
) {
    dependsOn(VersionCheck)

    PreferenceScreen.VIDEO.addPreferences(
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

    // Nullifying the first parameter/check will disable the normalization.
    // Upstream injects a const 0 into the second parameter at the start of the method.
    if (is_21_19_or_greater) {
        ::volumeNormalizationConfigMethod.hookMethod {
            before {
                if (DisableDRCAudioPatch.disableLoudnessNormalization()) {
                    it.args[1] = zeroOf(it.args[1])
                }
            }
        }
    } else if (is_21_17_or_greater) {
        // The 21.17 - 21.18 legacy variant nullifies a value loaded in the middle of the
        // method, which cannot be replicated at runtime. Not ported, as the module targets
        // 21.19+ where the modern config method is used.
    }
}
