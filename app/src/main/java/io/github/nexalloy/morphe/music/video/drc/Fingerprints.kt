package io.github.nexalloy.morphe.music.video.drc

import io.github.nexalloy.SkipTest
import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.anyInstruction
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.newInstance
import io.github.nexalloy.morphe.string

private const val PLAYBACK_AGNOSTIC_USER_PREFERENCES =
    "Lcom/google/android/libraries/youtube/media/interfaces/PlaybackAgnosticUserPreferences;"

/**
 * Code that creates the playback preferences of the media requests:
 * `preferences.isDrcEnabled()` -> `preferences.getAudioTrackId()` ->
 * `new PlaybackAgnosticUserPreferences(isDrcEnabled, audioTrackId, ...)`, directly or with a factory.
 *
 * YT Music 9.46.51: zcc.g(zgv) and zby.g(zgv) — the media capability update handlers of
 * MediaFetchController — call `yox.c()` (isDrcEnabled, invoke-virtual) and `yox.b()`
 * (audioTrackId) and then pass both to the factory that creates the preferences.
 * The interface based creation sites (zby.J, zcc.i) call `yol.h()` with invoke-interface
 * and are not matched, the same as upstream, which hooks the getter resolved from the
 * first virtual match.
 *
 * Matches multiple methods by design: the patch resolves the shared isDrcEnabled method from
 * the first match. The base query is pinned to the g(zgv) wrapper methods (the only creators
 * that call the getters virtually), because a broad query is not affordable at
 * runtime: the instruction filters fetch the instructions of every candidate, and a query
 * that matches thousands of methods exhausts the host heap before filtering.
 */
@SkipTest
@StrictQuery
internal object PlaybackAgnosticUserPreferencesFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Lzgv;"),
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
            newInstance(PLAYBACK_AGNOSTIC_USER_PREFERENCES),
            methodCall(returnType = PLAYBACK_AGNOSTIC_USER_PREFERENCES),
            location = MatchAfterWithin(15)
        )
    )
)

/**
 * The method that returns if DRC audio is enabled, resolved from the first
 * [PlaybackAgnosticUserPreferencesFingerprint] match. All the places that create the
 * playback preferences of the media requests call it.
 *
 * YT Music 9.46.51: yox.c() — the cached DRC preference of the player settings wrapper.
 */
internal val isDrcAudioEnabledMethod = findMethodDirect {
    PlaybackAgnosticUserPreferencesFingerprint.matchAll()
        .first().instructionMatches.first().instruction.methodRef!!
}

/**
 * The method that creates the streaming URL with the volume normalization parameters.
 * The upstream fingerprint requires at least 3 parameters, which is not expressible in the
 * DexKit query and is filtered here.
 *
 * YT Music 9.46.51: ayn.R(zer, zmh, float, boolean) — the third parameter (float) is the
 * normalization gain the method passes to the volume normalization config of the media request.
 */
internal val volumeNormalizationConfigMethod = findMethodDirect {
    VolumeNormalizationConfigFingerprint.matchAll()
        .single { it.method.paramTypes.size >= 3 }.method
}

/**
 * The method that creates the streaming URL with the volume normalization parameters,
 * `...rng.<normalization>...;trkcfg...`. The first parameter holds the normalization value.
 * Upstream additionally requires at least 3 parameters, which is filtered after the query
 * in the patch.
 *
 * YT Music 9.46.51: ayn.R(zer, zmh, float, boolean) — the only method of the app with both
 * strings; the third parameter (float) is the normalization gain that the method passes to
 * the volume normalization config of the media request.
 */
internal object VolumeNormalizationConfigFingerprint : Fingerprint(
    strings = listOf("rng.", ";trkcfg.")
)
