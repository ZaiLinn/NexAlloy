/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4618
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Runtime port of "Disable audio ducking"
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/music/interaction/audioducking/DisableAudioDuckingPatch.kt).
 *
 * The upstream patch rewrites the 'will pause when ducked' argument of the focus request
 * constructor in front of the call, and returns early from the focus change handler when
 * the extension reports the change should be ignored. The runtime port reaches both points
 * with constructor and method hooks.
 */

package io.github.nexalloy.morphe.music.interaction.audioducking

import app.morphe.extension.music.patches.DisableAudioDuckingPatch
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook

val disableAudioDuckingPatch = patch(
    name = "Disable audio ducking",
    description = "Adds an option to keep the volume unchanged when another app plays " +
        "a short sound, such as a notification.",
) {
    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_music_disable_audio_ducking", summary = true)
    )

    // Android ducks the app itself, unless the request declares the app pauses when ducked.
    AudioFocusRequestFingerprint.hookMethod(scopedHook(::audioFocusRequestConstructor.member) {
        before {
            val last = it.args.size - 1
            it.args[last] = DisableAudioDuckingPatch.willPauseWhenDucked(it.args[last] as Boolean)
        }
    })

    // The app is then told to duck, and would lower the volume itself.
    AudioFocusChangeFingerprint.hookMethod {
        before {
            if (DisableAudioDuckingPatch.ignoreAudioFocusChange(it.args[0] as Int)) {
                it.result = null
            }
        }
    }
}
