/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3337
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Runtime port of the Morphe patcher patch of the same name
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/youtube/layout/statusbar/HideStatusBarPatch.kt).
 * The instruction level injections of the patcher are realized with method hooks:
 * the feature flag reads go through the global literal override, and the injected calls
 * that follow a framework call are realized as scoped hooks of that framework method.
 */

package io.github.nexalloy.morphe.youtube.layout.statusbar

import android.app.Activity
import android.graphics.Rect
import android.view.View
import app.morphe.extension.shared.Utils
import app.morphe.extension.youtube.patches.HideStatusBarPatch
import app.morphe.extension.youtube.patches.NavigationBarPatch
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.insertLiteralOverride
import io.github.nexalloy.morphe.youtube.misc.playertype.PlayerTypeHook
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.youtube.shared.YOUTUBE_MAIN_ACTIVITY_CLASS_TYPE
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook
import org.luckypray.dexkit.wrap.DexMethod

val hideStatusBarPatch = patch(
    name = "Hide status bar",
    description = "Adds an option to hide the system status bar. " +
            "Swipe down from the top edge to show it for a moment.",
) {
    dependsOn(PlayerTypeHook)

    PreferenceScreen.GENERAL.addPreferences(
        SwitchPreference("morphe_hide_status_bar", summary = true),
        SwitchPreference("morphe_hide_status_bar_avoid_cutout", summary = true),
        SwitchPreference("morphe_fix_status_bar_layout", summary = true)
    )

    setExtensionIsPatchIncluded(HideStatusBarPatch::class.java)

    // The flags are read through the experiment flag accessors by every class that uses them,
    // so the runtime literal override of the flag value covers every read site, the same as
    // the instruction level overrides of the patcher patch.
    // The flag draws the window behind the status bar and gives the top bars the status bar inset.
    insertLiteralOverride(45400535L, HideStatusBarPatch::useEdgeToEdgeTopBar)
    // The status bar color that follows the scrolling top bar needs the edge to edge top bar.
    insertLiteralOverride(45830993L, HideStatusBarPatch::useStatusBarScrollColor)

    // The flags of the translucent navigation bar must be turned off with the edge to edge top
    // bar, also if the Navigation bar patch is not enabled. These are the same overrides the
    // Navigation bar patch registers, so a double registration is harmless.
    insertLiteralOverride(45630927L, NavigationBarPatch::useTranslucentNavigationBarBackground)
    insertLiteralOverride(45632194L, NavigationBarPatch::useTranslucentNavigationBar)
    insertLiteralOverride(45631257L, NavigationBarPatch::useFeedBehindNavigationBar)
    insertLiteralOverride(45633329L, NavigationBarPatch::useTranslucentNavigationBarStyle)

    // Called at the start of the main activity onCreate, as the patcher patch inserts
    // the call at instruction 0.
    DexMethod("$YOUTUBE_MAIN_ACTIVITY_CLASS_TYPE->onCreate(Landroid/os/Bundle;)V").hookMethod {
        before { HideStatusBarPatch.initialize(it.thisObject as Activity) }
    }

    // The status bar background view keeps the height of the hidden status bar
    // and is drawn over the top bars, making them invisible or clipped.
    // The patcher patch inserts the call after View.setBackgroundColor(int).
    StatusBarBackgroundShowFingerprint.hookMethod(scopedHook(
        View::class.java.getDeclaredMethod("setBackgroundColor", Int::class.javaPrimitiveType)
    ) {
        after { HideStatusBarPatch.hideStatusBarBackground(it.thisObject as View) }
    })

    // Without the edge to edge top bar, the player can keep the top inset of the window
    // and is laid out below the status bar twice. The patcher patch replaces the stable
    // insets Rect that the method creates with Rect(int, int, int, int) before it is
    // returned; the runtime port intercepts that constructor while the method runs and
    // replaces the method result when it is the created Rect.
    val inStableInsetsMethod = ThreadLocal.withInitial { false }
    val stableInsets = ThreadLocal<Rect?>()
    PlayerStableInsetsFingerprint.hookMethod {
        before {
            inStableInsetsMethod.set(true)
            stableInsets.set(null)
        }
        after {
            inStableInsetsMethod.set(false)
            val insets = stableInsets.get() ?: return@after
            if (!it.hasThrowable() && it.result === insets) {
                it.result = HideStatusBarPatch.getPlayerStableInsets(insets)
            }
        }
    }
    Rect::class.java.getConstructor(
        Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType
    ).hookMethod {
        after {
            if (inStableInsetsMethod.get()) stableInsets.set(it.thisObject as Rect)
        }
    }

    // Without the edge to edge top bar, the top bars are laid out with the stable insets,
    // which keep the height of the hidden status bar. The patcher patch passes the copy of
    // the insets and the view they are read from to the extension. The extension only reads
    // the root view of the window, so the copy is intercepted at the Rect(Rect) constructor
    // while this method runs and the decor view of the current activity is passed instead.
    val inInsetsChangedMethod = ThreadLocal.withInitial { false }
    // Guards a recursive call: the extension reads the window insets,
    // which can copy Rects through the same constructor.
    val handlingInsetsCopy = ThreadLocal.withInitial { false }
    SystemWindowInsetsChangedFingerprint.hookMethod {
        before { inInsetsChangedMethod.set(true) }
        after { inInsetsChangedMethod.set(false) }
    }
    Rect::class.java.getConstructor(Rect::class.java).hookMethod {
        after {
            if (!inInsetsChangedMethod.get() || handlingInsetsCopy.get()) return@after
            handlingInsetsCopy.set(true)
            try {
                val view = Utils.getActivity()?.window?.decorView
                HideStatusBarPatch.setSystemWindowInsetTop(it.thisObject as Rect, view)
            } finally {
                handlingInsetsCopy.set(false)
            }
        }
    }
}
