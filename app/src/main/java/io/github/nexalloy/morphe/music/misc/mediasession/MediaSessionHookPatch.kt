package io.github.nexalloy.morphe.music.misc.mediasession

import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import app.morphe.extension.shared.Logger
import io.github.nexalloy.morphe.shared.MediaSessionSetMetadataFingerprint
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook

/**
 * A metadata hook and whether it wants the [MediaSession] in addition to the metadata.
 * One list keeps the upstream execution order: hooks run in registration order,
 * regardless of the variant they were registered with.
 */
private class MetadataHook(
    val withSession: Boolean,
    val hook: (MediaSession?, MediaMetadata?) -> Unit
)

private val setMetadataHooks = mutableListOf<MetadataHook>()
private val setPlaybackStateHooks = mutableListOf<(PlaybackState?) -> Unit>()

/**
 * Runs a hook before each `MediaSession.setMetadata` that YouTube Music calls
 * from its media session wrapper.
 *
 * @param hook Receives the [MediaMetadata].
 */
fun addSetMetadataHook(hook: (metadata: MediaMetadata?) -> Unit) {
    setMetadataHooks.add(MetadataHook(withSession = false) { _, metadata -> hook(metadata) })
}

/**
 * Runs a hook before each `MediaSession.setMetadata` that YouTube Music calls
 * from its media session wrapper.
 *
 * @param hook Receives the [MediaSession] and the [MediaMetadata].
 */
fun addSetMetadataWithSessionHook(hook: (session: MediaSession, metadata: MediaMetadata?) -> Unit) {
    setMetadataHooks.add(MetadataHook(withSession = true) { session, metadata ->
        hook(session!!, metadata)
    })
}

/**
 * Runs a hook before each `MediaSession.setPlaybackState` that YouTube Music calls
 * from its media session wrapper.
 *
 * @param hook Receives the [PlaybackState].
 */
fun addSetPlaybackStateHook(hook: (playbackState: PlaybackState?) -> Unit) {
    setPlaybackStateHooks.add(hook)
}

/**
 * Runtime port of the media session hook patch. Upstream injects a call in front of the
 * `MediaSession.setMetadata` / `MediaSession.setPlaybackState` invocations inside the
 * app's media session forwarding methods; the runtime module hooks the framework methods
 * scoped to those call sites instead, which fires the same callbacks with the same
 * arguments (the session is the receiver of the framework call).
 */
val mediaSessionHookPatch = patch(
    description = "Hooks the metadata and the playback state that YouTube Music gives to its media session.",
) {
    val setMetadataMethod =
        MediaSession::class.java.getDeclaredMethod("setMetadata", MediaMetadata::class.java)
    val setPlaybackStateMethod =
        MediaSession::class.java.getDeclaredMethod("setPlaybackState", PlaybackState::class.java)

    fun dispatch(block: () -> Unit) = try {
        block()
    } catch (err: Exception) {
        // Fail-closed: a broken consumer hook must never break the media session.
        Logger.printException({ "Media session hook failed" }, err)
    }

    MediaSessionSetMetadataFingerprint.hookMethod(scopedHook(setMetadataMethod) {
        before {
            val session = it.thisObject as? MediaSession
            val metadata = it.args[0] as? MediaMetadata
            setMetadataHooks.forEach { hook ->
                dispatch {
                    if (hook.withSession && session != null) {
                        hook.hook(session, metadata)
                    } else if (!hook.withSession) {
                        hook.hook(null, metadata)
                    }
                }
            }
        }
    })

    MediaSessionSetPlaybackStateFingerprint.hookMethod(scopedHook(setPlaybackStateMethod) {
        before {
            val playbackState = it.args[0] as? PlaybackState
            setPlaybackStateHooks.forEach { hook ->
                dispatch { hook(playbackState) }
            }
        }
    })
}
