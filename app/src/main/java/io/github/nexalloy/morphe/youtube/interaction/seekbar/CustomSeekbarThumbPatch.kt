/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4494
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Runtime port of the Morphe patcher patch of the same name
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/youtube/interaction/seekbar/CustomSeekbarThumbPatch.kt).
 *
 * The patcher patch replaces the call that draws the seekbar thumb circle with the extension,
 * and passes the seekbar view at the start of onDraw. A runtime hook cannot replace a single
 * instruction, so the thumb call is identified by its paint instead: the thumb circle is the
 * only circle of onDraw that is drawn with a Paint copied in the same onDraw execution
 * (YouTube 21.45.82: `jta.onDraw`, the thumb paint is `this.q`, the chapter marker circles
 * use the persistent paint `this.c.s`). The Paint and Canvas hooks are scoped to onDraw.
 */

package io.github.nexalloy.morphe.youtube.interaction.seekbar

import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import app.morphe.extension.youtube.patches.CustomSeekbarThumbPatch
import io.github.nexalloy.HookDsl
import io.github.nexalloy.IScopedHookCallback
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.morphe.shared.misc.settings.preference.ListPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import app.morphe.extension.youtube.settings.preference.CustomSeekbarThumbPreference
import io.github.nexalloy.morphe.youtube.layout.sponsorblock.seekbarOnDrawFingerprint
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook

/** Paints copied during the seekbar onDraw execution on this thread, holding the thumb paint. */
private val onDrawCopiedPaints = ThreadLocal<MutableSet<Paint>?>()

/** Guards the recursive Canvas.drawCircle call of the extension fallback. */
private val drawingThumb = ThreadLocal.withInitial { false }

val customSeekbarThumbPatch = patch(
    name = "Custom seekbar thumb",
    description = "Adds an option to customize the seekbar thumb with a custom image or animated GIF.",
) {
    PreferenceScreen.SEEKBAR.addPreferences(
        noTitleUnsortedPreferenceCategory(
            SwitchPreference("morphe_custom_seekbar_thumb", summary = true),
            NonInteractivePreference(
                key = "morphe_custom_seekbar_thumb_file",
                tag = CustomSeekbarThumbPreference::class.java,
                selectable = true
            ),
            ListPreference("morphe_custom_seekbar_thumb_scale_type")
        )
    )

    setExtensionIsPatchIncluded(CustomSeekbarThumbPatch::class.java)

    // The patcher patch inserts setSeekbarView(View) at instruction 0 of onDraw.
    ::seekbarOnDrawFingerprint.hookMethod {
        before {
            onDrawCopiedPaints.set(mutableSetOf())
            CustomSeekbarThumbPatch.setSeekbarView(it.thisObject as View)
        }
        after { onDrawCopiedPaints.set(null) }
    }

    // The thumb circle is drawn with the Paint the seekbar copies while it draws,
    // so paints copied during onDraw are remembered to identify it.
    ::seekbarOnDrawFingerprint.hookMethod(scopedHook(
        Paint::class.java.getConstructor(Paint::class.java)
    ) {
        after {
            onDrawCopiedPaints.get()?.add(it.thisObject as Paint)
        }
    })

    // Replaces the call that draws the thumb circle. The extension draws the thumb,
    // or falls back to drawing the circle itself, which is let through while it runs.
    // Hardware accelerated drawing dispatches to the RecordingCanvas override,
    // so both implementations are hooked.
    val drawCircleMembers = listOfNotNull(
        Canvas::class.java.getDeclaredMethod(
            "drawCircle",
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Paint::class.java
        ),
        runCatching {
            Class.forName("android.graphics.RecordingCanvas").getDeclaredMethod(
                "drawCircle",
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Paint::class.java
            )
        }.getOrNull()
    )
    val drawThumbBlock: HookDsl<IScopedHookCallback>.() -> Unit = {
        before {
            if (drawingThumb.get()) return@before
            if (!onDrawCopiedPaints.get().orEmpty().contains(it.args[3] as Paint)) return@before

            drawingThumb.set(true)
            try {
                CustomSeekbarThumbPatch.drawCircle(
                    it.thisObject as Canvas,
                    it.args[0] as Float,
                    it.args[1] as Float,
                    it.args[2] as Float,
                    it.args[3] as Paint
                )
            } finally {
                drawingThumb.set(false)
            }
            // Skip the original circle, it is either replaced by the thumb or drawn by the extension.
            it.result = null
        }
    }
    ::seekbarOnDrawFingerprint.hookMethod(scopedHook(*drawCircleMembers.map { it to drawThumbBlock }.toTypedArray()))
}
