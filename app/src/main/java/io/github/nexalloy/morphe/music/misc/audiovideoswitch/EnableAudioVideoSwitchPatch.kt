package io.github.nexalloy.morphe.music.misc.audiovideoswitch

import android.content.SharedPreferences
import android.view.View
import app.morphe.extension.music.patches.AutoSongVersionPatch
import app.morphe.extension.music.patches.EnableAudioVideoSwitchPatch
import app.morphe.extension.music.patches.EnableAudioVideoSwitchPatch.QueueAccess
import app.morphe.extension.shared.Logger
import io.github.nexalloy.MethodHookParam
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.music.video.information.musicVideoInformationPatch
import io.github.nexalloy.morphe.music.video.playerresponse.Hook
import io.github.nexalloy.morphe.music.video.playerresponse.PlayerResponseMethodHook
import io.github.nexalloy.morphe.music.video.playerresponse.addPlayerResponseMethodHook
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import io.github.nexalloy.patch
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.Executor

/**
 * Runtime port of the upstream "Enable audio/video switch" patch. The upstream patch generates
 * an ABI bridge class implementing the extension's [QueueAccess] interface on the host queue
 * manager, implements the host's queue lane listener interface in the extension's QueueListener,
 * and injects calls into four host entry points. This port resolves the same queue members with
 * the fingerprints of this package, captures the queue with a constructor hook, and provides the
 * bridge with reflection, so no code is generated.
 */
val enableAudioVideoSwitchPatch = patch(
    name = "Enable audio/video switch",
    description = "Adds an option to switch between the audio and the video version of a track " +
        "by redirecting videoId.",
) {
    dependsOn(
        // The switch reads the video id of the playing track, and the song version replacement
        // remembers the album of the player response.
        musicVideoInformationPatch,
        PlayerResponseMethodHook,
    )

    PreferenceScreen.PLAYER.addPreferences(
        noTitleUnsortedPreferenceCategory(
            SwitchPreference("morphe_music_enable_audio_video_switch", summary = true),
            SwitchPreference("morphe_music_auto_song_version", summary = true),
        )
    )

    // region Fail-closed helper.

    fun <T> hookRegion(name: String, block: () -> T): Result<T> {
        return runCatching(block).onFailure {
            Logger.printException({ "Audio/video switch hook failed: $name" }, it)
        }
    }

    // endregion

    // region Queue access bridge.

    // The queue members the bridge calls, resolved once. Every candidate is constrained by the
    // stable queue relationships of the upstream Jam patch and verified by the fingerprint tests.
    // Without the bridge only the watch deep link fallback of the extension remains, which the
    // upstream also falls back to when no queue exists. Do not hook the queue then.
    val members = hookRegion("queue abi") {
        require(::queueStorageModeMethod.method.returnType.isEnum) {
            "Audio/video switch queue mode is not an enum"
        }
        QueueMembers(
            classLoader = classLoader,
            enqueue = QueueEnqueueFingerprint.method,
            commandDefaultInstance = ::queueCommandDefaultInstanceField.field.get(null)!!,
            commandParser = ::queueCommandParser.method,
            commandRegistry = GeneratedRegistryFingerprint.method.invoke(null)!!,
            executorField = ::queueExecutorField.field,
            storageField = ::queueStorageField.field,
            itemsMethod = ::queueStorageItemsMethod.method,
            laneMethod = ::queueLaneMethod.method,
            currentIndexMethod = ::queueStorageCurrentIndexMethod.method,
            modeMethod = ::queueStorageModeMethod.method,
            localMode = ::queueLocalModeField.field.get(null)!!,
            laneMoveMethod = ::queueLaneMoveMethod.method,
            attachListenerMethod = ::queueListenerAttachMethod.method,
            removeMethod = ::queueRemoveMethod.method,
            videoIdMethod = ::queueItemVideoIdMethod.method,
            listenerInterface = ::queueLaneListenerClass.clazz,
        )
    }.getOrNull()

    // The queue manager has a single construction/capture point. The extension holds the bridge
    // weakly, so the captured queue never outlives the host's own reference.
    // Anchor (9.46.51): `Lifd;-><init>(...)V`.
    members?.let { bridge ->
        hookRegion("queue capture") {
            ::queueManagerConstructor.constructor.hookMethod {
                after { param ->
                    if (param.hasThrowable() || param.thisObject == null) return@after
                    EnableAudioVideoSwitchPatch.capture(
                        MusicQueueAccess(param.thisObject!!, bridge)
                    )
                }
            }
        }
    }

    // endregion

    // region Audio/video switch pill.

    // Attaches the touch interceptor of the extension to the pill container every place the app
    // looks the pill up from. The upstream injects a call after each lookup with the found view.
    hookRegion("pill container") {
        val receiverFields = ::audioVideoSwitchPillReceiverFields.fieldList
        ::audioVideoSwitchPillContainerMethods.dexMethodList.forEach { dexMethod ->
            dexMethod.toMember().hookMethod {
                after { param ->
                    if (param.hasThrowable()) return@after
                    val pill = resolvePillView(param, receiverFields) ?: return@after
                    EnableAudioVideoSwitchPatch.installAudioVideoSwitchInterceptor(pill)
                }
            }
        }
    }

    // endregion

    // region Song version replacement.

    // Remembers the album being played so the song version replacement can prefer its tracks.
    // Anchor (9.46.51): the constructor of `Ljns;`, the account-scoped preferences wrapper.
    hookRegion("account preferences") {
        AccountPreferencesConstructorFingerprint.hookMethod {
            after { param ->
                if (param.hasThrowable()) return@after
                AutoSongVersionPatch.setAccountPreferences(param.thisObject as? SharedPreferences)
            }
        }
    }

    addPlayerResponseMethodHook(
        Hook.VideoIdAndPlaylistId { videoId, playlistId, playlistIndex ->
            AutoSongVersionPatch.trackPlaylist(videoId, playlistId, playlistIndex)
        }
    )

    // endregion
}

/**
 * Finds the audio/video switch pill container a pill lookup reads its receiver from, the same
 * view the upstream passes to the interceptor:
 *  - The enclosing view itself, when the lookup happens inside a view (`Lhyo;-><init>`).
 *  - The instance field the lookup reads its receiver from, when it belongs to the enclosing
 *    object (WatchFragment.`A`).
 *  - The first view parameter, from which the app looks the full player page up and the pill
 *    inside it (`Ljsi;-><init>`).
 */
private fun resolvePillView(param: MethodHookParam, receiverFields: List<Field>): View? = runCatching {
    val pillId = AUDIO_VIDEO_SWITCH_PILL_CONTAINER_ID.toInt()

    (param.thisObject as? View)?.let { view ->
        return@runCatching view.findViewById<View>(pillId)
    }

    // The receiver fields are shared by every pill lookup, so the field of this object is the
    // receiver of this lookup. The view parameters of a lookup with a field receiver, such as
    // the fragment parent container, are not the lookup receiver.
    receiverFields.singleOrNull()
        ?.takeIf { it.declaringClass.isInstance(param.thisObject) }
        ?.get(param.thisObject)
        ?.let { receiver ->
            return@runCatching (receiver as? View)?.findViewById<View>(pillId)
        }

    (param.args.firstOrNull { it is View } as? View)?.let { root ->
        // The app looks the player page up from the root, and the pill inside the page.
        val page = root.findViewById<View>(PLAYER_PAGE_ID.toInt()) ?: return@runCatching null
        return@runCatching page.findViewById<View>(pillId)
    }

    null
}.getOrNull()

/**
 * The queue members the bridge calls, resolved once at patch time.
 */
private class QueueMembers(
    val classLoader: ClassLoader,
    val enqueue: Method,
    val commandDefaultInstance: Any,
    val commandParser: Method,
    val commandRegistry: Any,
    val executorField: Field,
    val storageField: Field,
    val itemsMethod: Method,
    val laneMethod: Method,
    val currentIndexMethod: Method,
    val modeMethod: Method,
    val localMode: Any,
    val laneMoveMethod: Method,
    val attachListenerMethod: Method,
    val removeMethod: Method,
    val videoIdMethod: Method,
    val listenerInterface: Class<*>,
)

/**
 * The queue of the host wrapped for the extension's [QueueAccess] interface, the same ABI the
 * upstream generates with patcher code. Every queue operation of the extension dispatches here,
 * so the bridge never changes what the host's own code observes.
 */
private class MusicQueueAccess(
    private val manager: Any,
    private val members: QueueMembers,
) : QueueAccess {

    private val storage: Any
        get() = members.storageField.get(manager)
            ?: throw IllegalStateException("Audio/video switch queue storage is unavailable")

    override fun patch_atvEnqueue(command: ByteArray) {
        val parsed = members.commandParser.invoke(
            null, members.commandDefaultInstance, command, members.commandRegistry
        )
        members.enqueue.invoke(manager, parsed)
    }

    override fun patch_atvExecutor(): Executor? = members.executorField.get(manager) as? Executor

    override fun patch_atvCurrentIndex(): Int = members.currentIndexMethod.invoke(storage) as Int

    override fun patch_atvItems(): Array<Any?> {
        val items = members.itemsMethod.invoke(storage, 0) as? List<*> ?: return emptyArray()
        @Suppress("UNCHECKED_CAST")
        return (items as java.util.List<Any?>).toArray()
    }

    override fun patch_atvVideoId(item: Any): String? = members.videoIdMethod.invoke(item) as? String

    override fun patch_atvRemove(item: Any) {
        members.removeMethod.invoke(manager, item)
    }

    override fun patch_atvMove(from: Int, to: Int) {
        members.laneMoveMethod.invoke(patch_atvPrimaryLane(), from, to)
    }

    override fun patch_atvPrimaryLane(): Any = members.laneMethod.invoke(storage, 0)!!

    /**
     * The lane the extension listens to. The extension passes its own QueueListener instance,
     * which the upstream implements with the host's listener interface; every callback of the
     * host interface only tells the extension the queue changed, which a proxy of the resolved
     * listener interface provides here.
     */
    override fun patch_atvAddListener(lane: Any, listener: Any) {
        val proxy = Proxy.newProxyInstance(
            members.classLoader,
            arrayOf(members.listenerInterface),
        ) { _, _, _ ->
            AutoSongVersionPatch.onQueueChanged()
            null
        }
        members.attachListenerMethod.invoke(lane, proxy)
    }

    override fun patch_atvLocal(): Boolean = members.modeMethod.invoke(storage) == members.localMode
}
