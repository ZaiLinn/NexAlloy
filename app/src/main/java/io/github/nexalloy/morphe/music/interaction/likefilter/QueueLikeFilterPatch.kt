package io.github.nexalloy.morphe.music.interaction.likefilter

import app.morphe.extension.music.patches.QueueLikeFilterPatch
import app.morphe.extension.music.patches.QueueLikeFilterPatch.QueueAccess
import app.morphe.extension.shared.Logger
import io.github.nexalloy.morphe.music.misc.audiovideoswitch.queueExecutorField
import io.github.nexalloy.morphe.music.misc.audiovideoswitch.queueItemVideoIdMethod
import io.github.nexalloy.morphe.music.misc.audiovideoswitch.queueLaneListenerClass
import io.github.nexalloy.morphe.music.misc.audiovideoswitch.queueLaneMethod
import io.github.nexalloy.morphe.music.misc.audiovideoswitch.queueListenerAttachMethod
import io.github.nexalloy.morphe.music.misc.audiovideoswitch.queueManagerConstructor
import io.github.nexalloy.morphe.music.misc.audiovideoswitch.queueRemoveMethod
import io.github.nexalloy.morphe.music.misc.audiovideoswitch.queueStorageCurrentIndexMethod
import io.github.nexalloy.morphe.music.misc.audiovideoswitch.queueStorageField
import io.github.nexalloy.morphe.music.misc.audiovideoswitch.queueStorageItemsMethod
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.music.video.information.musicVideoIdHook
import io.github.nexalloy.morphe.music.video.information.musicVideoInformationPatch
import io.github.nexalloy.morphe.music.video.playerresponse.Hook
import io.github.nexalloy.morphe.music.video.playerresponse.PlayerResponseMethodHook
import io.github.nexalloy.morphe.music.video.playerresponse.addPlayerResponseMethodHook
import io.github.nexalloy.morphe.shared.misc.settings.preference.InputType
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.TextPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import io.github.nexalloy.hookMethod
import io.github.nexalloy.patch
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.Executor

/**
 * Runtime port of the music queue likes filter patch. The queue manager the upstream reaches
 * with the interface it adds during patching is instead captured with its constructor, and
 * the members of the queue the extension reads are invoked with reflection. The queue listener
 * the upstream generates is a dynamic proxy that reports every callback as a queue change.
 *
 * The queue members are resolved by the audio/video switch patch, which uses the same stable
 * queue relationships the Jam patch of the upstream resolves, so enabling both patches never
 * changes what is matched.
 */
val queueLikeFilterPatch = patch(
    name = "Queue likes filter",
    description = "Adds an option to remove songs with few likes from auto-generated mixes and radio, " +
            "using the like counts of Return YouTube Dislike. Playlists and albums are left unchanged.",
) {
    dependsOn(
        musicVideoInformationPatch,
        PlayerResponseMethodHook,
    )

    PreferenceScreen.PLAYER.addPreferences(
        // Keep the toggle directly above the minimum, even though the Player screen
        // otherwise sorts its preferences by title.
        noTitleUnsortedPreferenceCategory(
            SwitchPreference("morphe_music_queue_like_filter", summary = true),
            TextPreference(
                key = "morphe_music_queue_like_filter_minimum_likes",
                inputType = InputType.NUMBER,
            ),
        ),
    )

    // region Fail-closed helper.

    fun hookRegion(name: String, block: () -> Unit) {
        runCatching(block).onFailure { Logger.printException({ "Queue likes filter hook failed: $name" }, it) }
    }

    // endregion

    // region Hand the queue manager to the extension.

    // Called when the app queue manager is created, like the upstream constructor capture.
    hookRegion("queue capture") {
        val access = QueueAccessMembers(
            executorField = ::queueExecutorField.field,
            storageField = ::queueStorageField.field,
            itemsMethod = ::queueStorageItemsMethod.method,
            laneMethod = ::queueLaneMethod.method,
            currentIndexMethod = ::queueStorageCurrentIndexMethod.method,
            videoIdMethod = ::queueItemVideoIdMethod.method,
            removeMethod = ::queueRemoveMethod.method,
            attachListenerMethod = ::queueListenerAttachMethod.method,
            listenerClass = ::queueLaneListenerClass.clazz,
        )

        ::queueManagerConstructor.constructor.hookMethod {
            after {
                val manager = it.thisObject ?: return@after
                QueueLikeFilterPatch.setQueue(MusicQueueAccess(manager, access))
            }
        }
    }

    // endregion

    // region Songs added by the user are never removed.

    // Called with the songs the user adds with "Play next" and "Add to queue", before they
    // are added, like the upstream injection right after the response item list is read.
    hookRegion("songs enqueued") {
        val itemsField = ::queueEnqueueSuccessItemsField.field
        QueueEnqueueSuccessFingerprint.hookMethod {
            before {
                val response = it.args[0] ?: return@before
                @Suppress("UNCHECKED_CAST")
                val items = itemsField.get(response) as? List<Any?> ?: return@before
                QueueLikeFilterPatch.onSongsEnqueued(items)
            }
        }
    }

    // endregion

    // Check the queue again whenever playback moves to another track.
    musicVideoIdHook(QueueLikeFilterPatch::onVideoIdChanged)

    // Track the playlist of the current track, so playlists and albums are left unchanged.
    addPlayerResponseMethodHook(
        Hook.VideoIdAndPlaylistId(QueueLikeFilterPatch::onPlaylistChanged)
    )
}

/**
 * The resolved members of the queue, created once when the patch applies.
 */
private class QueueAccessMembers(
    val executorField: Field,
    val storageField: Field,
    val itemsMethod: Method,
    val laneMethod: Method,
    val currentIndexMethod: Method,
    val videoIdMethod: Method,
    val removeMethod: Method,
    val attachListenerMethod: Method,
    val listenerClass: Class<*>,
)

/**
 * The app queue manager wrapped for the extension. Implements the interface the upstream
 * adds to the manager during patching with reflection over the resolved queue members.
 */
private class MusicQueueAccess(
    private val manager: Any,
    private val members: QueueAccessMembers,
) : QueueAccess {

    private fun storage() = members.storageField.get(manager)

    override fun patch_likeFilterExecutor(): Executor = members.executorField.get(manager) as Executor

    override fun patch_likeFilterCurrentIndex(): Int =
        members.currentIndexMethod.invoke(storage()) as Int

    override fun patch_likeFilterLaneItems(lane: Int): List<Any?>? =
        members.itemsMethod.invoke(storage(), lane) as? List<Any?>?

    override fun patch_likeFilterVideoId(item: Any?): String? =
        members.videoIdMethod.invoke(item) as? String

    override fun patch_likeFilterRemove(item: Any?) {
        members.removeMethod.invoke(manager, item)
    }

    override fun patch_likeFilterLane(lane: Int): Any? = members.laneMethod.invoke(storage(), lane)

    override fun patch_likeFilterAddListener(lane: Any?, listener: Any?) {
        // The upstream generates a class that implements the app queue listener; the runtime
        // module cannot add interfaces, so the listener of the extension is replaced with a
        // proxy of the app queue listener interface, which the set of the lane stores.
        val proxy = Proxy.newProxyInstance(
            members.listenerClass.classLoader,
            arrayOf(members.listenerClass),
        ) { proxy, method, args -> handleListenerCallback(proxy, method, args) }
        members.attachListenerMethod.invoke(lane, proxy)
    }
}

/**
 * Every callback of the queue listener tells the extension that the queue changed, like the
 * listener class the upstream generates during patching. All callbacks of the interface are
 * void, so no other return value than the identity of the proxy is ever needed.
 */
private fun handleListenerCallback(proxy: Any, method: Method, args: Array<Any?>?): Any? {
    if (method.declaringClass == Any::class.java) {
        return when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            "toString" -> proxy.javaClass.name
            else -> null
        }
    }

    QueueLikeFilterPatch.onQueueChanged()
    return null
}
