/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Runtime port of "Enable forced miniplayer"
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/music/layout/miniplayer/EnableForcedMiniplayerPatch.kt).
 */

package io.github.nexalloy.morphe.music.layout.miniplayer

import app.morphe.extension.music.patches.EnableForcedMiniplayerPatch
import de.robv.android.xposed.XC_MethodHook
import io.github.nexalloy.ScopedHook
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import org.luckypray.dexkit.DexKitBridge
import io.github.nexalloy.morphe.indexOfFirstInstructionOrThrow
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.patch
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * The miniplayer flag setter the queue transition calls with the forced-miniplayer boolean.
 * Anchor (9.46.51): `Lacay;->I(Z)V`, called by `Lijp;->i(Labsr;Lhzn;)V` as
 * `I(watchWhileLayout.ah() && minimizedHint)`.
 */
internal val minimizedPlayerSetMethod = findMethodDirect {
    val method = MinimizedPlayerFingerprint.run()
    val booleanValueIndex = method.indexOfFirstInstructionOrThrow(
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, name = "booleanValue")
    )
    val invokeIndex = method.indexOfFirstInstructionOrThrow(
        booleanValueIndex + 1,
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, parameters = listOf("Z"), returnType = "V")
    )
    method.instructions[invokeIndex].methodRef!!
}

/** The instruction index of the watch-while layout cast of the queue transition. */
context(dexkit: DexKitBridge)
private fun minimizedPlayerCastIndex() = MinimizedPlayerFingerprint.run().instructions.indexOfFirst {
    it.opcode == Opcode.CHECK_CAST.opCode && it.classRef?.descriptor == WATCH_WHILE_LAYOUT_CLASS
}

/**
 * The provider field of the queue transition that holds the watch-while layout.
 * Anchor (9.46.51): `Lijp;->p`, read as `((WatchWhileLayout) this.p.ix()).ah()` in the
 * transition method.
 */
internal val transitionWatchWhileLayoutField = findFieldDirect {
    val castIndex = minimizedPlayerCastIndex()
    MinimizedPlayerFingerprint.run().instructions.take(castIndex).asReversed()
        .first { it.opcode == Opcode.IGET_OBJECT.opCode }.fieldRef!!
}

/**
 * The provider getter of the transition layout field.
 * Anchor (9.46.51): the `ix()` provider interface call between the field read and the cast.
 */
internal val transitionWatchWhileLayoutGetMethod = findMethodDirect {
    val castIndex = minimizedPlayerCastIndex()
    MinimizedPlayerFingerprint.run().instructions.take(castIndex).asReversed()
        .first { it.opcode == Opcode.INVOKE_INTERFACE.opCode }.methodRef!!
}

/**
 * The minimized-capable state check of the watch-while layout, the left operand of the
 * `&&` the upstream injection sits in.
 * Anchor (9.46.51): `Lcom/google/android/apps/youtube/music/watchpage/ui/WatchWhileLayout;->ah()Z`.
 */
internal val watchWhileLayoutMinimizedStateMethod = findMethodDirect {
    MinimizedPlayerFingerprint.run().instructions.drop(minimizedPlayerCastIndex() + 1)
        .first { it.opcode == Opcode.INVOKE_VIRTUAL.opCode }.methodRef!!
}

/**
 * Runtime port of "Enable forced miniplayer".
 *
 * Upstream injects `enableForcedMiniplayerPatch(Z)Z` right after the `booleanValue()` of the
 * transition hint, rewriting only the hint before it is combined with the layout state check
 * (`I(ah && hint)`). At runtime the combined boolean only exists inside the method, so the
 * same result is achieved by rewriting the argument of the flag setter for the duration of
 * the transition: `I(hint || (setting && ah))` is identical to `I(ah && (setting || hint))`.
 */
val enableForcedMiniplayerPatch = patch(
    name = "Enable forced miniplayer",
    description = "Adds an option to enable forced miniplayer when switching between " +
            "music videos, podcasts, or songs.",
) {
    PreferenceScreen.addMiniplayerPreferences(
        SwitchPreference("morphe_music_enable_forced_miniplayer", summary = true)
    )

    val layoutField: Field = ::transitionWatchWhileLayoutField.field.apply { isAccessible = true }
    val layoutGet: Method = ::transitionWatchWhileLayoutGetMethod.method
    val minimizedState: Method = ::watchWhileLayoutMinimizedStateMethod.method

    // Only the transition of [MinimizedPlayerFingerprint] is affected. The watch-while layout
    // is captured while it runs, because the flag setter receives only the combined boolean.
    val transitionLayout = ThreadLocal<Any?>()

    val transitionHook = object : ScopedHook() {
        override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
            super.beforeHookedMethod(param)
            transitionLayout.set(
                runCatching { layoutGet.invoke(layoutField.get(param.thisObject)) }.getOrNull()
            )
        }

        override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
            transitionLayout.set(null)
            super.afterHookedMethod(param)
        }
    }

    transitionHook.hookInnerMethod(
        ::minimizedPlayerSetMethod.method,
        { param ->
            if (EnableForcedMiniplayerPatch.enableForcedMiniplayerPatch(false)) {
                val layout = transitionLayout.get()
                if (layout != null) {
                    val minimizedCapable = runCatching {
                        minimizedState.invoke(layout) as Boolean
                    }.getOrDefault(true)
                    // The upstream injection only rewrites the hint, so the layout state
                    // check is preserved when the layout is not in a minimized-capable state.
                    if (minimizedCapable) param.args[0] = true
                }
            }
        },
        { }
    )

    MinimizedPlayerFingerprint.hookMethod(transitionHook)
}
