package io.github.nexalloy.morphe.youtube.video.drc

import io.github.nexalloy.SkipTest
import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.anyInstruction
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.newInstance
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.string

private const val PLAYBACK_AGNOSTIC_USER_PREFERENCES =
    "Lcom/google/android/libraries/youtube/media/interfaces/PlaybackAgnosticUserPreferences;"

/**
 * Code that creates the playback preferences of the media requests:
 * `preferences.isDrcEnabled()` -> `preferences.getAudioTrackId()` ->
 * `new PlaybackAgnosticUserPreferences(isDrcEnabled, audioTrackId, ...)`, directly or with a factory.
 *
 * The first match is the method that gets if DRC audio is enabled. It is called with
 * invoke-virtual, while other code creates the preferences from an interface with a copy of it.
 * More than one method creates the preferences, and all call the same method.
 *
 * Matches multiple methods by design: the patch resolves the shared isDrcEnabled method from
 * the first match. The base query is pinned to the single-apqe wrapper methods
 * (Async/DefaultMediaFetchControllerWrapper.g), because a broad query is not affordable at
 * runtime: the instruction filters fetch the instructions of every candidate, and a query
 * that matches thousands of methods exhausts the host heap before filtering.
 */
@SkipTest
@StrictQuery
internal object PlaybackAgnosticUserPreferencesFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Lapqe;"),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            parameters = listOf(),
            returnType = "Z"
        ),
        methodCall(
            parameters = listOf(),
            returnType = "Ljava/lang/String;",
            location = MatchAfterWithin(3)
        ),
        anyInstruction(
            // 20.26.
            newInstance(PLAYBACK_AGNOSTIC_USER_PREFERENCES),
            // 20.51+
            methodCall(returnType = PLAYBACK_AGNOSTIC_USER_PREFERENCES),
            location = MatchAfterWithin(15)
        )
    )
)

/**
 * The method that creates the streaming URL with the volume normalization parameters,
 * `...rng.<normalization>...;trkcfg...`. The first parameter holds the normalization value.
 * Upstream additionally requires at least 3 parameters, which is filtered after the query
 * in the patch.
 */
internal object VolumeNormalizationConfigFingerprint : Fingerprint(
    strings = listOf("rng.", ";trkcfg.")
)

/**
 * 21.17 - 21.18 variant of [VolumeNormalizationConfigFingerprint].
 * Only used when the modern config method is not present.
 */
internal object VolumeNormalizationConfigLegacyFingerprint : Fingerprint(
    filters = listOf(
        opcode(Opcode.IGET_OBJECT),
        opcode(Opcode.IGET_OBJECT, MatchAfterImmediately()),
        opcode(Opcode.IGET_OBJECT, MatchAfterImmediately()),
        opcode(Opcode.IF_NEZ, MatchAfterWithin(5)),
        opcode(Opcode.IGET_OBJECT),
        string("rng."),
        string(";trkcfg.")
    )
)
