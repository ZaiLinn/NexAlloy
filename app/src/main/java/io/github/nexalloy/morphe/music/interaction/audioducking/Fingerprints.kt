/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4618
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Runtime port of the upstream patcher fingerprints
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/music/interaction/audioducking/Fingerprints.kt).
 */

package io.github.nexalloy.morphe.music.interaction.audioducking

import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.methodCall

internal object AudioFocusRequestFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(),
    strings = listOf("AudioFocus Requested"),
    filters = listOf(
        // AudioFocusRequestCompat constructor, the last parameter is 'will pause when ducked'.
        methodCall(
            opcode = Opcode.INVOKE_DIRECT,
            name = "<init>",
            parameters = listOf(
                "Landroid/media/AudioManager\$OnAudioFocusChangeListener;",
                "Landroid/os/Handler;",
                "Landroidx/media/AudioAttributesCompat;",
                "Z"
            )
        )
    )
)

internal object AudioFocusChangeFingerprint : Fingerprint(
    name = "onAudioFocusChange",
    returnType = "V",
    parameters = listOf("I"),
    strings = listOf("AudioFocus DUCK")
)

/**
 * The constructor call of the audio focus request, whose last parameter is replaced with
 * the value of the extension call the upstream patch injects in front of it.
 */
internal val audioFocusRequestConstructor = findMethodDirect {
    AudioFocusRequestFingerprint.instructionMatches.last().instruction.methodRef!!
}
