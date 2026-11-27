package io.github.nexalloy.morphe.music.video.skipsilence

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.string
import org.luckypray.dexkit.result.FieldData

/**
 * The Media3 DefaultAudioSink class.
 *
 * YT Music 9.46.51: defpackage.cdp, unique by both strings
 * ("AudioTrackAudioOutput" and "Failed to set playback params").
 */
internal object DefaultAudioSinkFingerprint : Fingerprint(
    filters = listOf(
        string("AudioTrackAudioOutput"),
        string("Failed to set playback params"),
    ),
)

/**
 * DefaultAudioSink's applyAudioProcessorPlaybackParameters,
 * where the silence skipping processor flag is set.
 *
 * YT Music 9.46.51: cdp.O(long) — reads the audio processors (iget-object of the
 * processor chain), reads the skip silence flag (iget-boolean) and stores it back
 * (iput-boolean). The class has several methods with the same signature; strict
 * instruction matching disambiguates them.
 */
@StrictQuery
internal object ApplySkipSilenceFingerprint : Fingerprint(
    classFingerprint = DefaultAudioSinkFingerprint,
    returnType = "V",
    parameters = listOf("J"),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = "L"
        ),
        fieldAccess(
            opcode = Opcode.IGET_BOOLEAN,
            definingClass = "this",
            location = MatchAfterWithin(2)
        ),
        fieldAccess(
            definingClass = "this",
            opcode = Opcode.IPUT_BOOLEAN
        )
    )
)

/**
 * The field that stores the silence skipping flag read by
 * [ApplySkipSilenceFingerprint]'s method.
 *
 * YT Music 9.46.51: cdp.E.
 */
internal val skipSilenceEnabledField = findFieldDirect {
    ApplySkipSilenceFingerprint.instructionMatches[1].instruction.fieldRef!!
}

/**
 * DefaultAudioSink's setSkipSilenceEnabled, which stores the flag read by
 * [ApplySkipSilenceFingerprint]'s method. Other boolean setters of the class have the same signature.
 *
 * YT Music 9.46.51: cdp.C(boolean).
 */
internal fun setSkipSilenceEnabledFingerprint(skipSilenceEnabledField: FieldData) = Fingerprint(
    definingClass = skipSilenceEnabledField.declaredClass!!.descriptor,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Z"),
    filters = listOf(
        fieldAccess(
            reference = skipSilenceEnabledField,
            opcode = Opcode.IPUT_BOOLEAN
        )
    )
)

internal val setSkipSilenceEnabledMethod = findMethodDirect {
    setSkipSilenceEnabledFingerprint(skipSilenceEnabledField()).run()
}

/**
 * Constructor of the silence skipping audio processor:
 * minimum silence duration (us), retention ratio, max silence to keep (us), min volume percentage, threshold level.
 *
 * YT Music 9.46.51: cdx(long, float, long, int, short).
 */
internal object SilenceSkippingProcessorClassFingerprint : Fingerprint(
    filters = listOf(
        string("bytesConsumed is not aligned to frame size: %s")
    )
)

internal object SilenceSkippingProcessorConstructorFingerprint : Fingerprint(
    classFingerprint = SilenceSkippingProcessorClassFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
    parameters = listOf("J", "F", "J", "I", "S"),
)

/**
 * Silence skipping processor's queueInput, which receives the decoded audio.
 *
 * YT Music 9.46.51: cdx.f(ByteBuffer).
 */
internal object SilenceSkippingProcessorQueueInputFingerprint : Fingerprint(
    classFingerprint = SilenceSkippingProcessorClassFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Ljava/nio/ByteBuffer;")
)

/**
 * Silence skipping processor's isNoise, which compares an audio sample with the silence threshold level.
 *
 * YT Music 9.46.51: cdx.w(byte, byte), reads the short threshold field.
 */
internal object SilenceSkippingProcessorIsNoiseFingerprint : Fingerprint(
    classFingerprint = SilenceSkippingProcessorClassFingerprint,
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf("B", "B"),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_SHORT,
            definingClass = "this"
        )
    )
)

/**
 * The threshold level field read by the silence skipping processor's isNoise.
 *
 * YT Music 9.46.51: cdx.g.
 */
internal val silenceThresholdLevelField = findFieldDirect {
    SilenceSkippingProcessorIsNoiseFingerprint.instructionMatches.first().instruction.fieldRef!!
}

/**
 * Silence skipping processor's onFlush, the only method without parameters that allocates the silence buffers.
 *
 * YT Music 9.46.51: cdx.q(), allocates the two silence byte arrays.
 */
internal object SilenceSkippingProcessorFlushFingerprint : Fingerprint(
    classFingerprint = SilenceSkippingProcessorClassFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        opcode(Opcode.NEW_ARRAY)
    )
)
