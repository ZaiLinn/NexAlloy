package io.github.nexalloy.morphe.reddit.font

import android.content.res.Resources
import app.morphe.extension.reddit.patches.CustomFontPatch
import app.morphe.extension.reddit.patches.ForceSystemFontPatch
import de.robv.android.xposed.callbacks.XCallback
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch
import java.lang.reflect.Method
import java.lang.reflect.Modifier

private val Method.fontResourceArgument: Int
    get() = if (Modifier.isStatic(modifiers)) 0 else 1

private val Method.fontStyleArgument: Int
    get() = parameterCount - 1

val CustomFont = patch(
    name = "Custom font",
    description = "Replaces Reddit Sans or Roboto with a selected TTF or OTF font.",
) {
    val loader = ::redditTypefaceLoader.method
    val resources = loader.fontResourceArgument
    val style = loader.fontStyleArgument
    loader.hookMethod {
        priority = XCallback.PRIORITY_HIGHEST
        before {
            val path = it.args[resources + 2] as? String ?: return@before
            CustomFontPatch.getCustomTypeface(
                it.args[resources] as Resources, path, it.args[style] as Int,
            )?.let { typeface -> it.result = typeface }
        }
    }
    setExtensionIsPatchIncluded(CustomFontPatch::class.java)
}

val ForceSystemFont = patch(
    name = "Force system font",
    description = "Replaces Reddit Sans or Roboto with the device system font.",
) {
    val loader = ::redditTypefaceLoader.method
    val path = loader.fontResourceArgument + 2
    val style = loader.fontStyleArgument
    loader.hookMethod {
        before {
            val originalPath = it.args[path] as? String ?: return@before
            ForceSystemFontPatch.getSystemTypeface(
                originalPath, it.args[style] as Int,
            )?.let { typeface -> it.result = typeface }
        }
    }
    setExtensionIsPatchIncluded(ForceSystemFontPatch::class.java)
}
