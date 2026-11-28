/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Runtime port of "Change miniplayer color"
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/music/layout/miniplayer/ChangeMiniplayerColorPatch.kt).
 *
 * The upstream patch injects the extension calls into the miniplayer and adds the
 * MiniplayerAccess interface to the miniplayer class. At runtime the interface is replaced by
 * an adapter over the same members (the color field and the reset method) the upstream
 * interface methods are generated from, and the injected calls become before/after hooks.
 */

package io.github.nexalloy.morphe.music.layout.miniplayer

import android.view.View
import app.morphe.extension.music.patches.ChangeMiniplayerColorPatch
import app.morphe.extension.shared.Logger
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.ResourceType
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.getResourceId
import io.github.nexalloy.morphe.indexOfFirstInstructionOrThrow
import io.github.nexalloy.morphe.indexOfFirstInstructionReversedOrThrow
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.music.misc.playservice.is_9_32_or_greater
import io.github.nexalloy.morphe.music.misc.playservice.versionCheckPatch
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.music.video.information.musicVideoIdHook
import io.github.nexalloy.morphe.music.video.information.musicVideoInformationPatch
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * The color field of the miniplayer, which the miniplayer animates to when it resets its
 * colors. Resolved the same way as upstream: the first int field put after the last
 * getColor call of the miniplayer constructor.
 * Anchor (9.46.51): `Ljsi;->aZ`, put from the theme's player page default background color.
 */
internal val miniplayerColorField = findFieldDirect {
    val constructor = MiniPlayerConstructorFingerprint.run()
    val colorGreyIndex = constructor.indexOfFirstInstructionReversedOrThrow(
        methodCall(name = "getColor")
    )
    val colorPutIndex = constructor.indexOfFirstInstructionOrThrow(colorGreyIndex, opcode(Opcode.IPUT))
    constructor.instructions[colorPutIndex].fieldRef!!
}

/**
 * The placeholder color math object the app gives the miniplayer before the colors of the
 * album art are known, such as on a cold start. The miniplayer resets to it, which is how it
 * is found: the first static field read of the reset method.
 * Anchor (9.46.51): `Lizp;->a:Lizp;`, read by `Ljsi;->E()V`.
 */
internal val miniplayerPlaceholderColorsField = findFieldDirect {
    val method = MiniplayerResetColorsFingerprint.run()
    val index = method.indexOfFirstInstructionOrThrow(opcode(Opcode.SGET_OBJECT))
    method.instructions[index].fieldRef!!
}

/**
 * The color math object field of the toggle parameter (9.32+) and the color int field of it.
 * Anchor (9.46.51): `Lizp;->f:Lizq;` and `Lizq;->d:I`, read by `Ljsi;->G(Lizp;J)V`.
 */
internal val miniplayerColorMathField = findFieldDirect {
    SwitchToggleColorFingerprint.instructionMatches.last().instruction.fieldRef!!
}

internal val miniplayerColorMathIntField = findFieldDirect {
    SwitchToggleColorFingerprint.instructionMatches[4].instruction.fieldRef!!
}

/**
 * The runtime replacement of the MiniplayerAccess interface the upstream patch adds to the
 * miniplayer class. Implements the same two members the patch-generated interface methods
 * forward to: the color field write and the reset method call.
 */
private class RuntimeMiniplayerAccess(
    miniplayer: Any,
    private val colorField: Field,
    private val resetColors: Method,
) : ChangeMiniplayerColorPatch.MiniplayerAccess {

    private val miniplayerRef = WeakReference(miniplayer)

    override fun patch_setColor(color: Int) {
        miniplayerRef.get()?.let { miniplayer ->
            runCatching { colorField.setInt(miniplayer, color) }
        }
    }

    override fun patch_resetColors() {
        miniplayerRef.get()?.let { miniplayer ->
            runCatching { resetColors.invoke(miniplayer) }
        }
    }
}

/**
 * Runtime port of "Change miniplayer color".
 */
val changeMiniplayerColorPatch = patch(
    name = "Change miniplayer color",
    description = "Adds an option to change the miniplayer background color to match " +
            "the fullscreen player.",
) {
    dependsOn(
        versionCheckPatch,
        musicVideoInformationPatch,
    )

    PreferenceScreen.addMiniplayerPreferences(
        SwitchPreference("morphe_music_change_miniplayer_color", summary = true),
        SwitchPreference("morphe_music_change_navigation_bar_color", summary = true)
    )

    val colorField: Field = ::miniplayerColorField.field
    val placeholderColorsField: Field = ::miniplayerPlaceholderColorsField.field
    val resetColorsMethod: Method = MiniplayerResetColorsFingerprint.method

    fun failClosed(hook: String, block: () -> Unit) {
        runCatching(block).onFailure {
            Logger.printException({ "Change miniplayer color hook failed: $hook" }, it)
        }
    }

    // The color the miniplayer gets from the theme, put in the field that holds its color.
    // Upstream injects the extension call before the field put of the constructor; at
    // runtime the field is read after the constructor, which has the same effect: the
    // extension remembers the miniplayer and the theme color it was made with.
    MiniPlayerConstructorFingerprint.hookMethod {
        after {
            val miniplayer = it.thisObject ?: return@after
            failClosed("setDefaultMiniplayerColor") {
                ChangeMiniplayerColorPatch.setDefaultMiniplayerColor(
                    RuntimeMiniplayerAccess(miniplayer, colorField, resetColorsMethod),
                    colorField.getInt(miniplayer)
                )
            }
        }
    }

    // The color of the album art is taken from the object passed to the switch-toggle color
    // setter, unless it is the placeholder object of a cold start. The extension decides the
    // color of the miniplayer and keeps the color of the album art for the navigation bar and
    // the glass. Upstream injects this at the start of the setter; a before hook is the same.
    if (is_9_32_or_greater) {
        val colorMathField: Field = ::miniplayerColorMathField.field
        val colorMathIntField: Field = ::miniplayerColorMathIntField.field

        SwitchToggleColorParentFingerprint.hookMethod {
            before {
                failClosed("setLastMiniplayerColor") {
                    if (!ChangeMiniplayerColorPatch.changeMiniplayerColor()) return@failClosed

                    val colorMath = it.args[0] ?: return@failClosed
                    val placeholder = placeholderColorsField.get(null)
                    if (colorMath.equals(placeholder)) {
                        return@failClosed
                    }

                    val colorContainer = colorMathField.get(colorMath)
                    if (!colorMathIntField.declaringClass.isInstance(colorContainer)) {
                        return@failClosed
                    }
                    val albumColor = colorMathIntField.getInt(colorContainer)

                    val newColor = ChangeMiniplayerColorPatch.setLastMiniplayerColor(albumColor)
                    colorField.setInt(it.thisObject, newColor)
                }
            }
        }
    } else {
        Logger.printDebug { "Change miniplayer color: color math of this host version is not supported yet" }
    }

    // Only the 'ytm_color_grey_12' branch (visible tab bar) is patched; the hidden branch
    // uses a different color. The view is published so the extension can repaint without
    // waiting for a relayout. Upstream rewrites the color argument of the matching
    // setBackgroundColor call; at runtime the call is hooked for the duration of the
    // navigation bar method and the grey_12 call site is recognized by its color value.
    val grey12ColorId = getResourceId(ResourceType.COLOR, "ytm_color_grey_12")

    NavigationBarTabLayoutFingerprint.hookMethod(
        scopedHook(View::class.java.getMethod("setBackgroundColor", Int::class.javaPrimitiveType)) {
            before {
                val themeColor = it.args[0] as? Int ?: return@before
                val context = (it.thisObject as? View)?.context ?: return@before
                val grey12 = runCatching { context.getColor(grey12ColorId) }.getOrNull() ?: return@before
                if (themeColor != grey12) return@before

                ChangeMiniplayerColorPatch.registerNavigationBar(it.thisObject as View, themeColor)
                it.args[0] = ChangeMiniplayerColorPatch.overrideNavigationBarColor(themeColor)
            }
        }
    )

    // The miniplayer shows "Nothing is playing" (for example after the queue is dismissed).
    MiniPlayerDefaultTextFingerprint.hookMethod {
        before {
            failClosed("onMiniplayerEmpty") { ChangeMiniplayerColorPatch.onMiniplayerEmpty() }
        }
    }

    // Hook the watch-while dismiss callback to drop the cached tint.
    WatchWhileDismissedFingerprint.hookMethod {
        before {
            failClosed("onMiniplayerDismissed") { ChangeMiniplayerColorPatch.onMiniplayerDismissed() }
        }
    }

    // A new song is loaded, so its album color can be used again.
    musicVideoIdHook(ChangeMiniplayerColorPatch::newVideoLoaded)
}
