package io.github.nexalloy.morphe.music.interaction.crossfade

import io.github.nexalloy.AllowAbstract
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.StringComparisonType
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.string

internal const val EXO_PLAYER_TYPE = "Landroidx/media3/exoplayer/ExoPlayer;"

/**
 * The MedialibPlayer stopVideo. Anchor (9.46.51): `yto.J(I)` logs
 * "MedialibPlayer.stopVideo(), %s" with the stop reason. `yto.H` logs
 * "MedialibPlayer.blockingStopVideo()" which does not start with the anchor.
 */
internal object MusicStopVideoFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("I"),
    strings = listOf(string("MedialibPlayer.stopVideo()", StringComparisonType.STARTS_WITH))
)

/**
 * The MedialibPlayer pauseVideo. Anchor (9.46.51): `yto.I(I)` logs
 * "MedialibPlayer.pauseVideo(), %s".
 */
internal object MusicPauseVideoFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("I"),
    strings = listOf(string("MedialibPlayer.pauseVideo()", StringComparisonType.STARTS_WITH))
)

/**
 * The MedialibPlayer playVideo. Anchor (9.46.51): `yto.n()` logs both
 * "MedialibPlayer.playVideo()" and the event-guard name "playVideo".
 */
internal object MusicPlayVideoFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("playVideo", "MedialibPlayer.playVideo()")
)

/**
 * The MedialibPlayer loadVideo, which receives the load descriptor of a track.
 * Anchor (9.46.51): `yto.l(yww)` logs "MedialibPlayer.loadVideo(cpn=%s ...".
 */
internal object MusicLoadVideoFingerprint : Fingerprint(
    classFingerprint = MusicStopVideoFingerprint,
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        string("MedialibPlayer.loadVideo(", StringComparisonType.STARTS_WITH)
    )
)

/**
 * The player coordinator's gapless play-next. The class of this method is the
 * coordinator, and its first object field read is the session.
 * Anchor (9.46.51): `yyr.z()` reads `this.h` first and logs "playNextInQueue."
 * and "gapless.seek.next".
 */
internal object MusicPlayNextInQueueFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("gapless.seek.next", "playNextInQueue."),
    filters = listOf(
        opcode(Opcode.IGET_OBJECT)
    )
)

/**
 * Sets the player volume, including the app's volume normalization, and stores
 * it in a field. Anchor (9.46.51): `yyr.P(F)` calls the player interface's
 * `E(F)V` and writes `this.ao` right after; `yyr.N(F)` forwards instead.
 */
@StrictQuery
internal object MusicPlayerVolumeFingerprint : Fingerprint(
    classFingerprint = MusicPlayNextInQueueFingerprint,
    returnType = "V",
    parameters = listOf("F"),
    filters = listOf(
        methodCall(opcode = Opcode.INVOKE_INTERFACE, parameters = listOf("F"), returnType = "V"),
        fieldAccess(opcode = Opcode.IPUT, type = "F", location = MatchAfterImmediately())
    )
)

/**
 * The audio/video toggle of the player. Anchor (9.46.51): `hzr.d()` logs
 * "Failed to update user last selected audio".
 */
internal object MusicAudioVideoToggleFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("Failed to update user last selected audio")
)

/**
 * The ExoPlayer implementation. A synthetic Runnable also logs with the
 * "ExoPlayerImpl" tag, so the interface check is required. Matches seekTo,
 * since newer app versions log with this tag in more than one method.
 * Anchor (9.46.51): `byy.seekTo` logs "seekTo ignored because an ad is playing"
 * and `byy` directly implements ExoPlayer.
 */
internal object MusicExoPlayerImplFingerprint : Fingerprint(
    interfaces = listOf(EXO_PLAYER_TYPE),
    strings = listOf("ExoPlayerImpl", "seekTo ignored because an ad is playing")
)

/**
 * The name of the player's release method, declared by the ExoPlayer interface.
 * Anchor (9.46.51): `ExoPlayer.L()` is the only void method ExoPlayer declares
 * itself (`byy.L` logs "Release " when it runs).
 */
@AllowAbstract
internal object MusicExoPlayerReleaseFingerprint : Fingerprint(
    definingClass = EXO_PLAYER_TYPE,
    returnType = "V",
    parameters = emptyList()
)

/**
 * The DismissWatchEvent handler. Unlike clearQueue it never runs on a normal
 * skip, so it tells a queue dismissal apart from a track change (#1671).
 * Anchor (9.46.51): `ggb.handleDismissWatchEvent(gtx)` is the only handler of
 * its name that calls into WatchWhileLayout; `igr`'s does not.
 */
@StrictQuery
internal object MusicHandleDismissWatchEventFingerprint : Fingerprint(
    name = "handleDismissWatchEvent",
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            definingClass = "Lcom/google/android/apps/youtube/music/watchpage/ui/WatchWhileLayout;"
        )
    )
)

/**
 * MediaSession loop-state adapter. It runs on every repeat mode change, which
 * is the only place the live repeat mode can be read from.
 * Anchor (9.46.51): the synthetic `gfu.a(Object)` case logs
 * "attempted to update repeat mode but media session was null: %d".
 */
internal object MusicLoopStateAdapterFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        string("attempted to update repeat mode but media session was null", StringComparisonType.STARTS_WITH)
    )
)

/**
 * The sleep timer state enum. It keeps its constant names, which makes it
 * stable to match. Anchor (9.46.51): `iiz` declares INACTIVE, ACTIVE_TIMER and
 * ACTIVE_END_OF_TRACK in its static initializer.
 */
internal object MusicSleepTimerStateFingerprint : Fingerprint(
    name = "<clinit>",
    strings = listOf("INACTIVE", "ACTIVE_TIMER", "ACTIVE_END_OF_TRACK")
)

/**
 * The getter of the live sleep timer state. Anchor (9.46.51): `ijc.a()` is the
 * only method of the app that returns `iiz` without parameters.
 */
internal object MusicSleepTimerStateGetterFingerprint : Fingerprint(
    isStatic = false,
    returnType = { MusicSleepTimerStateFingerprint().declaredClass!!.descriptor },
    parameters = emptyList()
)

/**
 * The sleep timer state setter. The end of song timer waits for the track to
 * end naturally, which a crossfade never lets happen (#2017).
 * Anchor (9.46.51): `ijc.f(iiz)` is the only method of the state manager that
 * takes a state.
 */
internal object MusicSleepTimerStateChangedFingerprint : Fingerprint(
    classFingerprint = MusicSleepTimerStateGetterFingerprint,
    isStatic = false,
    returnType = "V",
    parameters = { listOf(MusicSleepTimerStateFingerprint().declaredClass!!.descriptor) }
)

/**
 * The audio effect session helper. Anchor (9.46.51): the constructor of `zgw`
 * builds the OPEN and CLOSE_AUDIO_EFFECT_CONTROL_SESSION intents.
 */
internal object MusicAudioEffectSessionFingerprint : Fingerprint(
    name = "<init>",
    strings = listOf(
        "android.media.action.OPEN_AUDIO_EFFECT_CONTROL_SESSION",
        "android.media.action.CLOSE_AUDIO_EFFECT_CONTROL_SESSION"
    )
)

/**
 * Closes the audio effect session of the player. The app closes the session of
 * the old player as soon as the new player reports its own, which detaches
 * equalizers from a track that still fades out.
 * Anchor (9.46.51): `zgw.a(I)` adds "android.media.extra.AUDIO_SESSION" to the
 * close intent and broadcasts it.
 */
internal object MusicCloseAudioEffectSessionFingerprint : Fingerprint(
    classFingerprint = MusicAudioEffectSessionFingerprint,
    returnType = "V",
    parameters = listOf("I"),
    filters = listOf(
        string("android.media.extra.AUDIO_SESSION"),
        methodCall(smali = "Landroid/content/Context;->sendBroadcast(Landroid/content/Intent;)V")
    )
)
