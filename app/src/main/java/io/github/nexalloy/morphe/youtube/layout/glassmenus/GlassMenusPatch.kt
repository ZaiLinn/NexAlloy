/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/5107
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Runtime port of the Morphe patcher patch of the same names
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/shared/layout/glassmenus/GlassMenusPatch.kt
 * and morphe-patches/patches/src/main/kotlin/app/morphe/patches/youtube/layout/glassmenus/GlassMenusPatch.kt).
 * The popup menus are passed on by the flyout menu hooks of the extension itself
 * (FlyoutUtils calls GlassMenusPatch.onPopupWindow when the patch is included).
 */

package io.github.nexalloy.morphe.youtube.layout.glassmenus

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ListPopupWindow
import app.morphe.extension.shared.patches.glassmenus.GlassMenusPatch
import app.morphe.extension.shared.settings.preference.SeekBarPreference
import app.morphe.extension.shared.ui.SheetBottomDialog
import io.github.nexalloy.getObjectFieldOrNull
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.morphe.shared.layout.theme.lithoColorHookPatch
import io.github.nexalloy.morphe.shared.layout.theme.lithoDrawableDrawnColorHook
import io.github.nexalloy.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.layout.flyout.flyoutPatch
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.morphe.youtube.misc.playservice.is_21_30_or_greater
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook

val glassMenusPatch = patch(
    name = "Glass menus",
    description = "Adds an option to show the menus as frosted glass, with the content behind " +
            "the menu blurred. This patch works on Android 12 and newer.",
) {
    dependsOn(
        VersionCheck,
        // The popup menus are passed on by the flyout menu hooks.
        flyoutPatch,
        // The backgrounds Litho draws inside the menus are passed on by the Litho color hook.
        lithoColorHookPatch { is_21_30_or_greater },
    )

    PreferenceScreen.GENERAL.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_glass_menus_screen",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference("morphe_glass_menus", summary = true),
                NonInteractivePreference(
                    key = "morphe_glass_menus_opacity",
                    tag = SeekBarPreference::class.java,
                    selectable = true
                ),
                NonInteractivePreference(
                    key = "morphe_glass_menus_blur",
                    tag = SeekBarPreference::class.java,
                    selectable = true
                ),
                NonInteractivePreference(
                    key = "morphe_glass_menus_dim",
                    tag = SeekBarPreference::class.java,
                    selectable = true
                ),
            ),
        )
    )

    setExtensionIsPatchIncluded(GlassMenusPatch::class.java)

    // Bottom sheets: every bottom sheet dialog of the app, when it is attached to its window.
    BottomSheetDialogOnAttachedToWindowFingerprint.hookMethod {
        after { GlassMenusPatch.onBottomSheetDialog(it.thisObject as? Dialog) }
    }

    // Dialogs of dialog fragments, right after they are shown: the panels at the bottom of the
    // screen, such as the video quality and the download quality. The patcher patch inserts the
    // call after Dialog.show(), which subclasses such as BottomSheetDialog override, so the
    // runtime port instead reads the dialog of the fragment after the fragment started.
    DialogFragmentOnStartFingerprint.hookMethod {
        after {
            val dialog = it.thisObject.getObjectFieldOrNull("mDialog") as? Dialog
            GlassMenusPatch.onDialogShown(dialog)
        }
    }

    // The sheets of Morphe, such as the playback speed and the voice over translation.
    // The patcher patch passes the dialog at every return of the createSlideDialog method.
    SheetBottomDialog::class.java.getDeclaredMethod(
        "createSlideDialog", Context::class.java, View::class.java, Int::class.javaPrimitiveType
    ).hookMethod {
        after { GlassMenusPatch.onSlideDialog(it.result as? Dialog) }
    }

    // The backgrounds Litho draws inside the menus, with the color they are drawn with.
    lithoDrawableDrawnColorHook(GlassMenusPatch::onLithoBackgroundColor)

    // The sheet to save a video to a playlist, which is a dialog of its own.
    AddToPlaylistCreateViewFingerprint.hookMethod {
        after { GlassMenusPatch.onAddToPlaylistView(it.result as? View) }
    }

    // The touch feedback drawable sets the callback of the background it wraps before it has a
    // callback itself, so the background has none, and the view that draws it cannot be found
    // from it. The patcher patch inserts the call after the constructor of the superclass,
    // which an after hook of the constructor covers.
    TouchFeedbackDrawableConstructorFingerprint.hookMethod {
        after {
            GlassMenusPatch.onWrappedDrawable(it.thisObject as Drawable, it.args[0] as? Drawable)
        }
    }

    // The menus shown as a list popup, which is not a popup window of the flyout menus.
    ::menuListPopupWindowShowMethods.dexMethodList.forEach { method ->
        method.hookMethod(scopedHook(
            ListPopupWindow::class.java.getDeclaredMethod("show")
        ) {
            before { GlassMenusPatch.onListPopupWindow(it.thisObject as? ListPopupWindow) }
        })
    }

    // The "Please wait" progress dialog before the download quality menu,
    // which is not a dialog fragment.
    ::downloadProgressDialogShowMethods.dexMethodList.forEach { method ->
        method.hookMethod(scopedHook(
            Dialog::class.java.getDeclaredMethod("show")
        ) {
            before { GlassMenusPatch.onProgressDialogShown(it.thisObject as? Dialog) }
        })
    }
}
