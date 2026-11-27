package io.github.nexalloy.morphe.music.video.playerresponse

import app.morphe.extension.shared.Logger
import io.github.nexalloy.morphe.music.misc.playservice.versionCheckPatch
import io.github.nexalloy.hookMethod
import io.github.nexalloy.patch

private val hooks = mutableSetOf<Hook<*>>()

fun addPlayerResponseMethodHook(hook: Hook<*>) {
    hooks += hook
}

/**
 * Runtime port of the music player response method hook patch. The upstream patch injects
 * calls at the head of the player parameter builder in a fixed order:
 * proto-buffer hooks registered before the video id run first, then the video id hooks,
 * then the video id + playlist hooks, then the remaining proto-buffer hooks; the modified
 * proto buffer is written back to the parameter afterwards. This port dispatches the same
 * order from a single hook, in registration order within each group.
 */
val PlayerResponseMethodHook = patch(
    description = "Hooks the YouTube Music player parameter builder to expose video id, playlist id and player parameter.",
) {
    dependsOn(versionCheckPatch)

    val PARAMETER_VIDEO_ID = 0
    val PARAMETER_PROTO_BUFFER = 2
    val PARAMETER_PLAYLIST_ID = 3
    val PARAMETER_PLAYLIST_INDEX = 4

    PlayerParameterBuilderFingerprint.hookMethod {
        before { param ->
            val videoId = param.args[PARAMETER_VIDEO_ID] as String
            val playlistId = param.args[PARAMETER_PLAYLIST_ID] as String?
            val playlistIndex = param.args[PARAMETER_PLAYLIST_INDEX] as Int
            var protobuf = param.args[PARAMETER_PROTO_BUFFER] as String?

            fun dispatch(block: () -> Unit) = try {
                block()
            } catch (err: Exception) {
                Logger.printException({ "Player response method hook failed" }, err)
            }

            hooks.filterIsInstance<Hook.ProtoBufferParameterBeforeVideoId>().forEach { hook ->
                dispatch { protobuf = hook(protobuf, videoId) }
            }
            hooks.filterIsInstance<Hook.VideoId>().forEach { hook ->
                dispatch { hook(videoId) }
            }
            hooks.filterIsInstance<Hook.VideoIdAndPlaylistId>().forEach { hook ->
                dispatch { hook(videoId, playlistId, playlistIndex) }
            }
            hooks.filterIsInstance<Hook.PlayerParameter>().forEach { hook ->
                dispatch { protobuf = hook(protobuf, videoId) }
            }

            param.args[PARAMETER_PROTO_BUFFER] = protobuf
        }
    }
}

sealed class Hook<T> {
    class VideoId(val hook: (videoId: String) -> Unit) : Hook<(String) -> Unit>() {
        operator fun invoke(p1: String) = hook(p1)
    }

    class VideoIdAndPlaylistId(
        val hook: (videoId: String, playlistId: String?, playlistIndex: Int) -> Unit
    ) : Hook<(String, String?, Int) -> Unit>() {
        operator fun invoke(p1: String, p2: String?, p3: Int) = hook(p1, p2, p3)
    }

    class PlayerParameter(
        val hook: (protobuf: String?, videoId: String) -> String?
    ) : Hook<(String?, String) -> String?>() {
        operator fun invoke(p1: String?, p2: String): String? = hook(p1, p2)
    }

    class ProtoBufferParameterBeforeVideoId(
        val hook: (protobuf: String?, videoId: String) -> String?
    ) : Hook<(String?, String) -> String?>() {
        operator fun invoke(p1: String?, p2: String): String? = hook(p1, p2)
    }
}
