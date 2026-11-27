package io.github.nexalloy.morphe.youtube.layout.captions.core

import android.view.View
import de.robv.android.xposed.XposedHelpers
import io.github.nexalloy.hookMethod
import io.github.nexalloy.patch

/**
 * Runtime port of the upstream captionWindowMeasurePatch.
 *
 * The subtitle window keeps the height of its measure spec if the width mode is EXACTLY,
 * instead of the height mode. Use the height mode for the height.
 *
 * Upstream rewrites the branch that compares the width measure mode, so it compares the height
 * measure mode. Bytecode cannot be rewritten at runtime, so the decision is emulated with
 * the measure spec arguments instead:
 * - If the height mode is UNSPECIFIED, the original keeps the spec height only when the width
 *   mode is EXACTLY. Rewriting the height spec to AT_MOST with a very large size makes the
 *   original wrap the content height, which is the fixed behavior. The row measurement of the
 *   original is not affected, because the rows are measured with an UNSPECIFIED height spec
 *   that ignores its size.
 * - If the height mode is EXACTLY and the width mode is not, the original wraps the content
 *   height, but the fixed behavior keeps the spec height. The measured height is corrected
 *   after the original measurement.
 * All other mode combinations already behave the same with and without the fix.
 */
val CaptionWindowMeasure = patch(
    name = "Caption window measure",
    description = "Fixes the caption window height using the width measure mode.",
) {
    val onMeasure = ::captionWindowMeasureMethod.method

    onMeasure.hookMethod {
        before { param ->
            val heightSpec = param.args[1] as Int
            if (View.MeasureSpec.getMode(heightSpec) == View.MeasureSpec.UNSPECIFIED) {
                // Wrap the content height instead of keeping the spec height.
                param.args[1] = View.MeasureSpec.makeMeasureSpec(
                    Int.MAX_VALUE, View.MeasureSpec.AT_MOST
                )
            }
        }

        after { param ->
            val widthSpec = param.args[0] as Int
            val heightSpec = param.args[1] as Int
            if (View.MeasureSpec.getMode(heightSpec) == View.MeasureSpec.EXACTLY &&
                View.MeasureSpec.getMode(widthSpec) != View.MeasureSpec.EXACTLY
            ) {
                val view = param.thisObject as View
                // Protected in View, so called through the runtime.
                XposedHelpers.callMethod(
                    view, "setMeasuredDimension",
                    view.measuredWidth, View.MeasureSpec.getSize(heightSpec)
                )
            }
        }
    }
}
