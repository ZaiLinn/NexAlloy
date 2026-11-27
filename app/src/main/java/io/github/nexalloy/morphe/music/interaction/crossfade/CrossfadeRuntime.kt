package io.github.nexalloy.morphe.music.interaction.crossfade

import android.view.Surface
import app.morphe.extension.music.patches.CrossfadePatch.DelegateAccess
import app.morphe.extension.music.patches.CrossfadePatch.ExoPlayerAccess
import app.morphe.extension.music.patches.CrossfadePatch.MedialibPlayerAccess
import app.morphe.extension.music.patches.CrossfadePatch.PlayerCoordinatorAccess
import app.morphe.extension.music.patches.CrossfadePatch.PlayerFactoryAccess
import app.morphe.extension.music.patches.CrossfadePatch.SessionAccess
import app.morphe.extension.music.patches.CrossfadePatch.SharedCallbackAccess
import app.morphe.extension.music.patches.CrossfadePatch.SharedStateAccess
import app.morphe.extension.music.patches.CrossfadePatch.VideoSurfaceAccess
import app.morphe.extension.music.patches.CrossfadePatch.VideoToggleAccess
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/**
 * The discovered player structure of the host, and the adapters that make the
 * extension's Access interfaces work at runtime.
 *
 * The upstream patch injects `patch_*` methods into the host classes; the
 * runtime translation instead wraps every host object the extension touches in
 * an adapter that implements the interface the extension casts to and
 * forwards to the discovered fields and methods with reflection.
 *
 * Adapters are cached per host instance, so the extension's identity checks,
 * like `newPlayer != pendingInPlayer`, keep working. Adapters hold their host
 * through a weak reference, so the cache does not keep released players alive.
 */
internal class CrossfadeRuntime internal constructor(
    // Host classes.
    val coordinatorClass: Class<*>,
    val sharedCallbackClass: Class<*>,
    // Coordinator fields.
    private val exoPlayerField: Field,
    private val sessionField: Field,
    private val loadControlField: Field,
    private val sharedStateField: Field,
    private val sharedCallbackField: Field,
    private val videoSurfaceField: Field,
    private val playerVolumeField: Field,
    private val coordinatorListenerField: Field,
    private val coordinatorCwhListenerField: Field?,
    // Factory.
    private val factoryField: Field,
    private val factoryMethod: Method,
    private val guardField: Field?,
    private val timelineField: Field,
    // Shared callback.
    private val cqbField: Field,
    private val dltFieldOnShared: Field,
    // Player implementation.
    private val exoCwhField: Field?,
    private val cwhAddListenerMethod: Method?,
    private val listenerWrapperField: Field,
    private val listenerSetInWrapper: Field,
    private val listenerSetRemoveMethod: Method,
    private val directListenerSetField: Field?,
    private val dltFieldOnExo: Field,
    private val playbackInfoField: Field,
    private val playbackStateField: Field,
    private val currentPositionMethod: Method,
    private val durationMethod: Method,
    private val setVolumeMethod: Method,
    private val setPlayWhenReadyMethod: Method,
    private val setVideoSurfaceMethod: Method,
    private val releaseMethod: Method,
    val releaseRunnableClass: Class<*>?,
    val releaseRunnableReceiverField: Field?,
    // Video surface.
    private val videoSurfaceExoField: Field,
    // MedialibPlayer.
    private val playerChainField: Field,
    private val delegateFields: List<Pair<Class<*>, Field>>,
    private val playNextInQueueMethod: Method,
    private val loadVideoMethod: Method,
    // Audio/video toggle.
    private val stateProviderField: Field,
    private val getStateMethod: Method,
    private val isAudioModeMethod: Method,
    private val setStateMethod: Method,
    private val broadcasterField: Field,
    private val silentSetMethod: Method?,
    private val audioPreferredState: Any,
    private val videoPreferredState: Any,
) {
    init {
        // The host keeps most of these members private, and reading them from
        // the module is exactly what the injected bridges of the patcher did.
        for (field in listOf(
            exoPlayerField, sessionField, loadControlField, sharedStateField, sharedCallbackField,
            videoSurfaceField, playerVolumeField, coordinatorListenerField, factoryField,
            timelineField, cqbField, dltFieldOnShared, listenerWrapperField, listenerSetInWrapper,
            dltFieldOnExo, playbackInfoField, playbackStateField, videoSurfaceExoField,
            playerChainField, stateProviderField, broadcasterField
        ) + delegateFields.map { it.second }) field.isAccessible = true
        coordinatorCwhListenerField?.isAccessible = true
        exoCwhField?.isAccessible = true
        guardField?.isAccessible = true
        directListenerSetField?.isAccessible = true
        releaseRunnableReceiverField?.isAccessible = true
        for (method in listOf(
            factoryMethod, listenerSetRemoveMethod, currentPositionMethod, durationMethod,
            setVolumeMethod, setPlayWhenReadyMethod, setVideoSurfaceMethod, releaseMethod,
            playNextInQueueMethod, loadVideoMethod, getStateMethod, isAudioModeMethod,
            setStateMethod
        )) method.isAccessible = true
        cwhAddListenerMethod?.isAccessible = true
        silentSetMethod?.isAccessible = true
    }

    /** Adapters per host instance, so the extension's identity checks keep working. */
    private val adapters: MutableMap<Any, Any> =
        Collections.synchronizedMap(WeakHashMap<Any, Any>())

    /** Unwraps an adapter back to its host, or passes the object through. */
    fun unwrap(obj: Any?): Any? = (obj as? CrossfadeHost)?.hostRef?.get() ?: obj

    /** Wraps the MedialibPlayer for the extension. */
    fun wrapMedialibPlayer(host: Any?): MedialibPlayerAccess? = wrap(host) {
        MusicMedialibPlayerAdapter(this, it)
    }

    /** Wraps the player coordinator for the extension. */
    fun wrapCoordinator(host: Any?): PlayerCoordinatorAccess? = wrap(host) {
        MusicCoordinatorAdapter(this, it)
    }

    /** Wraps the audio/video toggle for the extension. */
    fun wrapVideoToggle(host: Any?): VideoToggleAccess? = wrap(host) {
        MusicVideoToggleAdapter(this, it)
    }

    /**
     * Wraps a node of the player chain: the chain mixes decorator base classes
     * (9.46.51 walks `zgl` for the queue player and `zfx` for the `zgo`/`zgb`
     * decorators), so each node is matched against every discovered delegate
     * class and reads the field of the class it belongs to. The coordinator
     * ends the walk. Unknown nodes end the walk, so a structural change of the
     * host fails closed instead of misbehaving.
     */
    private fun wrapChain(node: Any?): Any? {
        if (node == null) return null
        if (coordinatorClass.isInstance(node)) return wrap(node) { MusicCoordinatorAdapter(this, it) }
        val delegateField = delegateFields.firstOrNull { (cls, _) -> cls.isInstance(node) }?.second
            ?: return null
        return wrap(node) { MusicDelegateAdapter(this, it, delegateField) }
    }

    private fun wrapExoPlayer(host: Any?): ExoPlayerAccess? = wrap(host) { MusicExoPlayerAdapter(this, it) }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> wrap(host: Any?, create: (WeakReference<Any>) -> T): T? {
        if (host == null) return null
        val cache = adapters as MutableMap<Any, Any>
        return synchronized(cache) {
            val existing = cache[host]
            if (existing != null) existing as T
            else create(WeakReference(host)).also { cache[host] = it }
        }
    }

    private fun get(hostRef: WeakReference<Any>): Any =
        hostRef.get() ?: throw IllegalStateException("host released")

    /** Marker of an adapter, for unwrapping back to the host. */
    internal interface CrossfadeHost {
        val hostRef: WeakReference<Any>
    }

    private class MusicMedialibPlayerAdapter(
        private val runtime: CrossfadeRuntime,
        override val hostRef: WeakReference<Any>,
    ) : MedialibPlayerAccess, CrossfadeHost {
        override fun patch_getPlayerChain(): Any? =
            runtime.wrapChain(runtime.playerChainField.get(runtime.get(hostRef)))

        override fun patch_playNextInQueue() {
            runtime.playNextInQueueMethod.invoke(runtime.get(hostRef))
        }

        override fun patch_loadVideoWith(descriptor: Any) {
            runtime.loadVideoMethod.invoke(runtime.get(hostRef), descriptor)
        }
    }

    private class MusicDelegateAdapter(
        private val runtime: CrossfadeRuntime,
        override val hostRef: WeakReference<Any>,
        private val delegateField: Field,
    ) : DelegateAccess, CrossfadeHost {
        override fun patch_getDelegate(): Any? =
            runtime.wrapChain(delegateField.get(runtime.get(hostRef)))
    }

    private class MusicCoordinatorAdapter(
        private val runtime: CrossfadeRuntime,
        override val hostRef: WeakReference<Any>,
    ) : PlayerCoordinatorAccess, CrossfadeHost {
        private val host get() = runtime.get(hostRef)

        override fun patch_getExoPlayer(): Any? = runtime.wrapExoPlayer(runtime.exoPlayerField.get(host))

        override fun patch_setExoPlayer(player: Any) {
            runtime.exoPlayerField.set(host, runtime.unwrap(player))
        }

        /**
         * A coordinator setter that also moves its listeners to the new player
         * is preferred over a raw field write, but no supported 9.x version
         * has one. The fallback registers the MediaSession listener on the new
         * player's analytics collector, or the MediaSession never receives
         * onIsPlayingChanged(true) after a swap.
         */
        override fun patch_setPlayerWithBindings(player: Any) {
            val host = this.host
            val incoming = runtime.unwrap(player) ?: return
            val cwhField = runtime.exoCwhField
            val addListener = runtime.cwhAddListenerMethod
            val listenerField = runtime.coordinatorCwhListenerField
            if (cwhField != null && addListener != null && listenerField != null) {
                val cwh = cwhField.get(incoming)
                val listener = listenerField.get(host)
                if (cwh != null && listener != null) addListener.invoke(cwh, listener)
            }
            runtime.exoPlayerField.set(host, incoming)
        }

        override fun patch_getSession(): Any? {
            val session = runtime.sessionField.get(host) ?: return null
            return runtime.wrap(session) { MusicSessionAdapter(this.runtime, it) }
        }

        override fun patch_getLoadControl(): Any? = runtime.loadControlField.get(host)

        override fun patch_getSharedState(): Any? {
            val sharedState = runtime.sharedStateField.get(host) ?: return null
            return runtime.wrap(sharedState) { MusicSharedStateAdapter(this.runtime, it) }
        }

        override fun patch_getSharedCallback(): Any? {
            val sharedCallback = runtime.sharedCallbackField.get(host) ?: return null
            return runtime.wrap(sharedCallback) { MusicSharedCallbackAdapter(this.runtime, it) }
        }

        override fun patch_getVideoSurface(): Any? {
            val surface = runtime.videoSurfaceField.get(host) ?: return null
            return runtime.wrap(surface) { MusicVideoSurfaceAdapter(this.runtime, it) }
        }

        /** Lives in the player's audio offload listener set, not in its ListenerSet. */
        override fun patch_getCoordinatorListener(): Any? =
            runtime.coordinatorListenerField.get(host)

        /** The volume the app last set on its player, which includes its volume normalization. */
        override fun patch_getVolume(): Float = runtime.playerVolumeField.getFloat(host)
    }

    private class MusicSessionAdapter(
        private val runtime: CrossfadeRuntime,
        override val hostRef: WeakReference<Any>,
    ) : SessionAccess, CrossfadeHost {
        override fun patch_getFactory(): Any? {
            val factory = runtime.factoryField.get(runtime.get(hostRef)) ?: return null
            return runtime.wrap(factory) { MusicPlayerFactoryAdapter(this.runtime, it) }
        }
    }

    private class MusicPlayerFactoryAdapter(
        private val runtime: CrossfadeRuntime,
        override val hostRef: WeakReference<Any>,
    ) : PlayerFactoryAccess, CrossfadeHost {
        override fun patch_createPlayer(coordinator: Any, loadControl: Any, flags: Int): Any? {
            val factoryHost = runtime.get(hostRef)
            val coordinatorHost = runtime.unwrap(coordinator)
            // The ExoPlayer constructor refuses to attach while the
            // TrackSelector listener of the shared state is set.
            runtime.guardField?.let { guard ->
                guard.set(runtime.sharedStateField.get(coordinatorHost), null)
            }
            val player = runtime.factoryMethod.invoke(factoryHost, coordinatorHost, loadControl, flags)
            return runtime.wrapExoPlayer(player)
        }
    }

    private class MusicSharedStateAdapter(
        private val runtime: CrossfadeRuntime,
        override val hostRef: WeakReference<Any>,
    ) : SharedStateAccess, CrossfadeHost {
        private val host get() = runtime.get(hostRef)

        override fun patch_getTimeline(): Any? = runtime.timelineField.get(host)

        override fun patch_setTimeline(timeline: Any?) {
            runtime.timelineField.set(host, timeline)
        }
    }

    private class MusicSharedCallbackAdapter(
        private val runtime: CrossfadeRuntime,
        override val hostRef: WeakReference<Any>,
    ) : SharedCallbackAccess, CrossfadeHost {
        private val host get() = runtime.get(hostRef)

        override fun patch_getCqb(): Any? = runtime.cqbField.get(host)

        override fun patch_setCqb(cqb: Any?) {
            runtime.cqbField.set(host, cqb)
        }

        override fun patch_getDlt(): Any? = runtime.dltFieldOnShared.get(host)

        override fun patch_setDlt(dlt: Any?) {
            runtime.dltFieldOnShared.set(host, dlt)
        }
    }

    private class MusicVideoSurfaceAdapter(
        private val runtime: CrossfadeRuntime,
        override val hostRef: WeakReference<Any>,
    ) : VideoSurfaceAccess, CrossfadeHost {
        override fun patch_setPlayerReference(player: Any) {
            runtime.videoSurfaceExoField.set(runtime.get(hostRef), runtime.unwrap(player))
        }
    }

    private class MusicExoPlayerAdapter(
        private val runtime: CrossfadeRuntime,
        override val hostRef: WeakReference<Any>,
    ) : ExoPlayerAccess, CrossfadeHost {
        private val host get() = runtime.get(hostRef)

        override fun patch_getPlaybackState(): Int =
            runtime.playbackStateField.getInt(runtime.playbackInfoField.get(host))

        override fun patch_getCurrentPosition(): Long = runtime.currentPositionMethod.invoke(host) as Long

        override fun patch_getDuration(): Long = runtime.durationMethod.invoke(host) as Long

        override fun patch_setVolume(volume: Float) {
            runtime.setVolumeMethod.invoke(host, volume)
        }

        override fun patch_setPlayWhenReady(play: Boolean) {
            runtime.setPlayWhenReadyMethod.invoke(host, play)
        }

        override fun patch_setVideoSurface(surface: Surface?) {
            runtime.setVideoSurfaceMethod.invoke(host, surface)
        }

        override fun patch_release() {
            runtime.releaseMethod.invoke(host)
        }
        /** The ExoPlayer's ListenerSet, shared with the MediaSession. */
        override fun patch_getListenerSet(): Any? =
            runtime.listenerSetInWrapper.get(runtime.listenerWrapperField.get(host))

        override fun patch_setDltCallback(dlt: Any?) {
            runtime.dltFieldOnExo.set(host, dlt)
        }

        override fun patch_addDirectListener(listener: Any) {
            @Suppress("UNCHECKED_CAST")
            (runtime.directListenerSetField?.get(host) as? MutableCollection<Any?>)?.add(listener)
        }

        override fun patch_removeDirectListener(listener: Any) {
            @Suppress("UNCHECKED_CAST")
            (runtime.directListenerSetField?.get(host) as? MutableCollection<Any?>)?.remove(listener)
        }

        /**
         * Removes the analytics collector from the event dispatch, so the
         * release of an outgoing player does not reach the MediaSession.
         */
        override fun patch_detachCwhFromEventDispatch() {
            val cwhField = runtime.exoCwhField ?: return
            runtime.listenerSetRemoveMethod.invoke(
                runtime.listenerWrapperField.get(host), cwhField.get(host)
            )
        }
    }

    private class MusicVideoToggleAdapter(
        private val runtime: CrossfadeRuntime,
        override val hostRef: WeakReference<Any>,
    ) : VideoToggleAccess, CrossfadeHost {
        private fun stateProvider(): Any = runtime.stateProviderField.get(runtime.get(hostRef))!!

        override fun patch_isAudioMode(): Boolean {
            val state = runtime.getStateMethod.invoke(stateProvider())
            return runtime.isAudioModeMethod.invoke(null, state) as Boolean
        }

        override fun patch_forceAudioModeSilent() = silentSet(runtime.audioPreferredState)

        override fun patch_restoreVideoModeSilent() = silentSet(runtime.videoPreferredState)

        private fun silentSet(state: Any) {
            val silentSet = runtime.silentSetMethod ?: return
            silentSet.invoke(runtime.broadcasterField.get(stateProvider()), state)
        }

        /** Notifies subscribers, so ones left stale by the silent variants resync. */
        override fun patch_restoreVideoMode() {
            runtime.setStateMethod.invoke(stateProvider(), runtime.videoPreferredState)
        }
    }
}
