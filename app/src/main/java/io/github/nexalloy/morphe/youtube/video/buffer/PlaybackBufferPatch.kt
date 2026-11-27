/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3386
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package io.github.nexalloy.morphe.youtube.video.buffer

import app.morphe.extension.youtube.patches.PlaybackBufferPatch
import app.morphe.extension.youtube.patches.PlaybackBufferPatch.PlaybackBufferSize
import app.morphe.extension.youtube.settings.Settings
import io.github.nexalloy.morphe.shared.misc.settings.preference.ListPreference
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch

internal val playbackBufferPatch = patch(
    name = "Playback buffer",
    description = "Adds an option to change the video playback buffer size.",
) {
    // DexKit's field instances are not accessibility-modified, and both the buffered
    // duration (public final) and the byte limit fields (private) need it for writes.
    val bufferedField = ::bufferedDurationField.field.apply { isAccessible = true }
    val limitFields = ::byteLimitFields.fieldList.map { it.apply { isAccessible = true } }
    val originalBufferedUs = ThreadLocal<Long?>()
    val originalByteLimits = ThreadLocal<IntArray?>()

    // The upstream patcher scales the buffered duration at every field read inside
    // shouldContinueLoading. At runtime the same is achieved by scaling the field for
    // the duration of the call and restoring it afterwards.
    ShouldContinueLoadingFingerprint.hookMethod {
        before {
            // Setting reads are memory-cached; this keeps the hot path allocation-free.
            if (Settings.PLAYBACK_BUFFER_SIZE.get() == PlaybackBufferSize.DEFAULT) return@before
            val state = it.args[0] ?: return@before
            val bufferedUs = bufferedField.getLong(state)
            val scaledUs = PlaybackBufferPatch.scaleBufferedDurationUs(bufferedUs)
            if (scaledUs == bufferedUs) return@before
            originalBufferedUs.set(bufferedUs)
            bufferedField.setLong(state, scaledUs)
        }
        after {
            val bufferedUs = originalBufferedUs.get() ?: return@after
            originalBufferedUs.remove()
            it.args[0]?.let { state -> bufferedField.setLong(state, bufferedUs) }
        }
    }

    // The upstream patcher scales the calculated memory limit before it is stored and
    // pushed to the allocator. At runtime the fields are rescaled after the call, and
    // the allocator target is updated with the scaled limit.
    TracksSelectedFingerprint.hookMethod {
        before {
            val host = it.thisObject ?: return@before
            originalByteLimits.set(
                IntArray(limitFields.size) { i -> limitFields[i].getInt(host) }
            )
        }
        after {
            if (it.hasThrowable()) return@after
            val original = originalByteLimits.get() ?: return@after
            originalByteLimits.remove()
            val host = it.thisObject ?: return@after
            if (Settings.PLAYBACK_BUFFER_SIZE.get() == PlaybackBufferSize.DEFAULT) return@after

            var limitChanged = false
            limitFields.forEachIndexed { i, field ->
                val limit = field.getInt(host)
                if (limit != original[i]) {
                    limitChanged = true
                    field.setInt(host, PlaybackBufferPatch.scaleByteLimit(limit))
                }
            }
            if (!limitChanged) return@after

            val limit = limitFields.last().getInt(host)
            val allocator = ::allocatorGetterMethod.method.invoke(host) ?: return@after
            ::allocatorTargetMethod.method.invoke(allocator, limit)
        }
    }

    PreferenceScreen.VIDEO.addPreferences(
        ListPreference("morphe_playback_buffer_size")
    )
}
