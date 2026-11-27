package io.github.nexalloy.morphe.youtube.video.skipsilence

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.string

/**
 * The Media3 DefaultAudioSink class.
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
 * The class has several methods with the same signature; strict instruction matching
 * disambiguates them.
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
 * Constructor of the silence skipping audio processor:
 * minimum silence duration (us), retention ratio, max silence to keep (us), min volume percentage, threshold level.
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
 */
internal object SilenceSkippingProcessorQueueInputFingerprint : Fingerprint(
    classFingerprint = SilenceSkippingProcessorClassFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Ljava/nio/ByteBuffer;")
)

/**
 * Silence skipping processor's isNoise, which compares an audio sample with the silence threshold level.
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
 * Silence skipping processor's onFlush, the only method without parameters that allocates the silence buffers.
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
