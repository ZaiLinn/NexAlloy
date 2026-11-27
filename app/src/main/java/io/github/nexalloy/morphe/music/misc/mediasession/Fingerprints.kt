package io.github.nexalloy.morphe.music.misc.mediasession

import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.methodCall

/**
 * Matches the method YouTube Music uses to forward the playback state to its media session.
 * The RemoteCallbackList broadcast filter tells the app's own setter apart from the extension
 * classes, which also set the playback state but have no callbacks to notify.
 * Anchor (9.46.51): `Lfg;->l(Landroid/support/v4/media/session/PlaybackStateCompat;)V`,
 * which calls `MediaSession.setPlaybackState` after `RemoteCallbackList.beginBroadcast`.
 */
internal object MediaSessionSetPlaybackStateFingerprint : Fingerprint(
    filters = listOf(
        methodCall(smali = "Landroid/os/RemoteCallbackList;->beginBroadcast()I"),
        methodCall(
            definingClass = "Landroid/media/session/MediaSession;",
            name = "setPlaybackState",
            parameters = listOf("Landroid/media/session/PlaybackState;")
        )
    )
)
