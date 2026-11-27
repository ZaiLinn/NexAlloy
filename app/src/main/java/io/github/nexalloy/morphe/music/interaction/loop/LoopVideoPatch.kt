package io.github.nexalloy.morphe.music.interaction.loop

import android.graphics.Canvas
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import app.morphe.extension.music.patches.LoopVideoPatch
import io.github.nexalloy.morphe.music.video.information.musicVideoIdHook
import io.github.nexalloy.morphe.music.video.information.musicVideoInformationPatch
import io.github.nexalloy.morphe.music.video.information.musicVideoTimeHook
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.hookMethod
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook

/**
 * Runtime port of "A-B loop".
 *
 * Upstream injects calls into the seek method, the scrub position, the time bar draw and
 * the time labels, and adds an onTouchEvent override to the time bar. The runtime module
 * hooks the same methods instead; the touch events go through the onTouchEvent of the
 * time bar base class, which the time bar does not override.
 */
val loopVideoPatch = patch(
    name = "A-B loop",
    description = "Adds an option to loop part of a song by long-pressing the seekbar " +
            "to set the loop start and end.",
) {
    dependsOn(musicVideoInformationPatch)

    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_music_ab_loop", summary = true),
    )

    // Clear the loop when another track starts.
    musicVideoIdHook(LoopVideoPatch::videoIdChanged)

    // Pull the playback back to the loop start, about once a second.
    musicVideoTimeHook(LoopVideoPatch::videoTimeChanged)

    // Keep seeks from the app and from the media session inside the loop.
    MedialibPlayerSeekToFingerprint.hookMethod {
        before {
            it.args[0] = LoopVideoPatch.clampSeekPosition(it.args[0] as Long)
        }
    }

    // Keep the dragged thumb inside the loop.
    TimeBarSetScrubPositionFingerprint.hookMethod {
        before {
            it.args[0] = LoopVideoPatch.clampSeekX(it.args[0] as Float)
        }
    }

    // Draw the loop markers and keep the seekbar bounds for the touch handling.
    val seekbarRectField = ::timeBarSeekbarRectField.field
    seekbarRectField.isAccessible = true
    TimeBarDrawFingerprint.hookMethod {
        after {
            val canvas = it.args[0] as Canvas
            val seekbarRect = seekbarRectField.get(it.thisObject) as? Rect ?: return@after
            LoopVideoPatch.drawLoopMarkers(canvas, seekbarRect)
        }
    }

    // Show the time labels with milliseconds while dragging. Only the label calls of
    // the time bar draw are changed: the remaining label is formatted from a negative
    // value, the elapsed label from a positive one.
    TimeBarDrawFingerprint.hookMethod(scopedHook(::timeLabelFormatter.method) {
        after {
            val formatted = it.result?.toString() ?: return@after
            it.result = if ((it.args[0] as Long) < 0) {
                LoopVideoPatch.formatRemainingTime(formatted)
            } else {
                LoopVideoPatch.formatElapsedTime(formatted)
            }
        }
    })

    // Track long presses on the seekbar. The time bar does not override onTouchEvent,
    // so the base class of the time bar is hooked instead.
    runCatching {
        val timeBarClass = Class.forName(
            TIME_BAR_CLASS.removePrefix("L").removeSuffix(";").replace('/', '.')
        )
        val baseOnTouchEvent = timeBarClass.superclass
            .getMethod("onTouchEvent", MotionEvent::class.java)
        baseOnTouchEvent.hookMethod {
            before {
                if (timeBarClass.isInstance(it.thisObject)) {
                    LoopVideoPatch.onTouchEvent(it.thisObject as View, it.args[0] as MotionEvent)
                }
            }
        }
    }
}
