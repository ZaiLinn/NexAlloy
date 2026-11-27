package io.github.nexalloy.morphe.music.video.information

import app.morphe.extension.music.shared.VideoInformation
import app.morphe.extension.shared.Logger
import io.github.nexalloy.bindProxy
import io.github.nexalloy.hookMethod
import io.github.nexalloy.patch
import java.lang.ref.WeakReference
import java.lang.reflect.Method

/**
 * Bridges the host player to the extension's seek interface. The patcher implementation adds
 * the PlaybackController interface to the player class; the runtime module instead keeps a
 * proxy bound to the player's lifecycle and calls the player's own seek method through it.
 */
internal class MusicPlaybackController(
    obj: Any,
    private val seekTo: Method,
    private val seekSourceUnknown: Any,
) : VideoInformation.PlaybackController {

    private val playerRef = WeakReference(obj)

    init {
        obj.bindProxy(this)
    }

    override fun patch_seekTo(videoTime: Long): Boolean {
        val player = playerRef.get() ?: return false
        return seekTo(player, videoTime, seekSourceUnknown) as? Boolean ?: false
    }
}

private val onCreateHooks = mutableListOf<() -> Unit>()
private val videoTimeHooks = mutableListOf<(Long) -> Unit>()
private val videoIdHooks = mutableListOf<(String) -> Unit>()

/**
 * Runs a hook on the main thread when the player initializes.
 * The extension's [VideoInformation.initialize] is always called first,
 * then these hooks in registration order (upstream inserts them the same way).
 */
fun onMusicCreateHook(hook: () -> Unit) {
    onCreateHooks.add(hook)
}

/**
 * Runs a hook with the current playback position about once a second.
 * [VideoInformation.setVideoTime] is always called first, then these hooks
 * in registration order, so consumers (e.g. SponsorBlock) see fresh values.
 */
fun musicVideoTimeHook(hook: (videoTime: Long) -> Unit) {
    videoTimeHooks.add(hook)
}

/**
 * Runs a hook with the new video id when a track changes.
 * [VideoInformation.setVideoId] and [VideoInformation.setVideoLength] are always
 * called first, then these hooks in registration order.
 */
fun musicVideoIdHook(hook: (videoId: String) -> Unit) {
    videoIdHooks.add(hook)
}

private inline fun safeHook(block: () -> Unit) {
    try {
        block()
    } catch (err: Exception) {
        // Fail-closed: a broken consumer hook must never break playback.
        Logger.printException({ "Music video information hook failed" }, err)
    }
}

/**
 * Runtime port of the music video information patch. Gives the extension the current
 * video id, video length, playback position and a seek callback for YouTube Music.
 */
val musicVideoInformationPatch = patch(
    description = "Hooks video ID, video time, video length and seekTo for YouTube Music.",
) {
    val seekMethod = ::musicPlayerSeekMethod.method
    val seekSourceUnknown = ::musicSeekSourceUnknownField.field.get(null)!!
    val videoIdMethod = ::musicVideoIdMethod.method
    val videoLengthMethod = ::musicVideoLengthMethod.method

    // Give the player to the extension and reset the per-video state each time it is
    // created, then run the registered create hooks (upstream inserts all of these
    // calls into the player constructor in the same order).
    VideoEndFingerprint.declaredClass.constructors.forEach { constructor ->
        constructor.hookMethod {
            after {
                val player = it.thisObject ?: return@after
                safeHook {
                    VideoInformation.setPlayer(
                        MusicPlaybackController(player, seekMethod, seekSourceUnknown)
                    )
                    VideoInformation.initialize()
                    onCreateHooks.forEach { hook -> safeHook(hook) }
                }
            }
        }
    }

    // Feed the current playback position into the extension, about once a second.
    // The position is the second long parameter of the progress report method; the
    // first long parameter is a -1 sentinel.
    val timeParameterIndex = PlayerControllerSetTimeReferenceFingerprint.dexMethod
        .paramTypeNames.countingSecondLongParameter()
    PlayerControllerSetTimeReferenceFingerprint.hookMethod {
        before {
            val time = it.args[timeParameterIndex] as? Long ?: return@before
            safeHook {
                VideoInformation.setVideoTime(time)
                videoTimeHooks.forEach { hook -> safeHook { hook(time) } }
            }
        }
    }

    // New track: forward the video id and the video length to the extension,
    // id first and length second, like the upstream synthetic method does.
    VideoIdFingerprint.hookMethod {
        before {
            val model = it.args[0] ?: return@before
            safeHook {
                val videoId = videoIdMethod(model) as? String
                if (videoId != null) {
                    VideoInformation.setVideoId(videoId)
                    VideoInformation.setVideoLength(videoLengthMethod(model) as Long)
                    videoIdHooks.forEach { hook -> safeHook { hook(videoId) } }
                }
            }
        }
    }
}

/**
 * Index (in the Xposed argument list) of the second long parameter, which the upstream patch
 * identified as the playback position. Long and double parameters take one argument slot in
 * the argument array of a reflection hook, unlike the wide register pairs of smali.
 */
private fun List<String>.countingSecondLongParameter(): Int {
    var longCount = 0
    forEachIndexed { index, typeName ->
        if (typeName == "long" && ++longCount == 2) return index
    }
    error("Progress method has no second long parameter")
}
