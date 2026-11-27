package io.github.nexalloy.morphe.youtube.layout.captions.core

import app.morphe.extension.youtube.patches.CaptionsAfterSeekPatch as CaptionsAfterSeekExtension
import io.github.nexalloy.hookMethod
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook

/**
 * Runtime port of the upstream captionsAfterSeekPatch.
 *
 * Captions are timed cues that replace the caption on screen when they start.
 * After a long seek, the caption from before the seek stays on screen until the next cue starts:
 * - Captions are loaded in chunks of the cues that start in them, so the cue at the new
 *   position is missing if it started in an earlier chunk. Load the chunk where it starts too.
 * - The cue at the new position is loaded after the seek. YouTube shows cues added while the
 *   video time is inside them only with a server flag, so do that for caption cues.
 *
 * Upstream injects the second chunk load at the start of the chunk loader method. At runtime the
 * original method runs first, then it is invoked again with the start time of the caption at the
 * seek position. The second invocation resolves to an exact caption time, so it does not
 * schedule a third load.
 *
 * Upstream also ORs the add-cue-range server flag with an instanceof check of the cue range,
 * so caption cue ranges enter while the video time is inside them. At runtime the flag method is
 * scoped-hooked while cue ranges are added, and returns true if the added list contains a
 * caption cue. The cue ranges of one list belong to one feature, so this only widens the
 * behavior for lists that contain caption cues.
 */
val CaptionsAfterSeek = patch(
    name = "Captions after seek",
    description = "Shows the current caption after seeking.",
) {
    val loadChunkMethod = ::captionChunkLoaderMethod.method
    val chunkWindowsField = ::captionChunkWindowsField.field
    val chunkTimesField = ::captionChunkTimesField.field
    val addCueRanges = ::addCueRangesMethod.method
    val enterCueRangeFlag = ::enterCueRangeOnAddFlagMethod.method
    val captionCueRange = ::captionCueRangeClass.clazz

    // Load the chunk of the caption that is shown at the seek position.
    loadChunkMethod.hookMethod {
        after { param ->
            val time = param.args[0] as Long
            val windows = chunkWindowsField.get(param.thisObject) ?: return@after

            @Suppress("UNCHECKED_CAST")
            val captionTimes = chunkTimesField.get(windows) as? List<Long> ?: return@after

            val startTime = CaptionsAfterSeekExtension.getCaptionStartTime(captionTimes, time)
            if (startTime != time) {
                loadChunkMethod(param.thisObject, startTime)
            }
        }
    }

    // Start caption cue ranges that are added while the video time is inside them.
    addCueRanges.hookMethod(scopedHook(enterCueRangeFlag) {
        before {
            if (innerDepth != 0) return@before

            val cueRanges = outerParam.args[0] as? List<*> ?: return@before
            if (cueRanges.any { captionCueRange.isInstance(it) }) {
                it.result = true
            }
        }
    })
}
