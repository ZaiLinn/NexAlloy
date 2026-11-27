/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/5107
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Runtime port of the Morphe patcher fingerprints of the same names
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/shared/layout/glassmenus/Fingerprints.kt
 * and morphe-patches/patches/src/main/kotlin/app/morphe/patches/youtube/layout/glassmenus/Fingerprints.kt).
 */

package io.github.nexalloy.morphe.youtube.layout.glassmenus

import io.github.nexalloy.SkipTest
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.ResourceType
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.findMethodListDirect
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.resourceLiteral

/**
 * Methods that show a menu as a list popup window.
 */
internal val menuListPopupWindowShowMethods = findMethodListDirect {
    MenuListPopupWindowShowFingerprint.matchAll().map { it.method }
}

/**
 * Methods that show the "Please wait" progress dialog before the download quality menu.
 */
internal val downloadProgressDialogShowMethods = findMethodListDirect {
    DownloadProgressDialogFingerprint.matchAll().map { it.method }
}

/**
 * The bottom sheet dialog of the Material components, which every bottom sheet of the app uses.
 */
@StrictQuery
internal object BottomSheetDialogClassFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        resourceLiteral(ResourceType.LAYOUT, "design_bottom_sheet_dialog"),
    )
)

/**
 * onAttachedToWindow of the bottom sheet dialog, which the patcher patch passes
 * to the extension at the start of the method. The runtime port hooks it with an after hook.
 */
@StrictQuery
internal object BottomSheetDialogOnAttachedToWindowFingerprint : Fingerprint(
    classFingerprint = BottomSheetDialogClassFingerprint,
    name = "onAttachedToWindow",
    returnType = "V",
    parameters = listOf(),
)

/**
 * onStart of the dialog fragment of AndroidX, which shows the dialog of every dialog
 * fragment of the app. The patcher patch inserts the call after Dialog.show(), which
 * the runtime port realizes with a scoped hook of Dialog.show().
 */
@StrictQuery
internal object DialogFragmentOnStartFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        methodCall(definingClass = "Landroid/app/Dialog;", name = "show"),
        methodCall(
            definingClass = "Landroid/app/Dialog;",
            name = "getWindow",
            location = MatchAfterWithin(5)
        ),
        methodCall(
            definingClass = "Landroid/view/Window;",
            name = "getDecorView",
            location = MatchAfterWithin(5)
        )
    )
)

/**
 * The sheet to save a video to a playlist, a dialog of its own.
 *
 * YouTube 21.45.82 (jadx ground truth): `myi.O(LayoutInflater, ViewGroup, Bundle)`,
 * which inflates playlist_add_to_fragment. The patcher patch passes the returned view
 * to the extension at every return, which an after hook covers.
 */
@StrictQuery
internal object AddToPlaylistCreateViewFingerprint : Fingerprint(
    returnType = "Landroid/view/View;",
    filters = listOf(
        resourceLiteral(ResourceType.LAYOUT, "playlist_add_to_fragment"),
    )
)

/**
 * The touch feedback drawable of the app, which wraps the background of a Litho component,
 * such as a row of a menu. The first instruction match is the constructor of the superclass.
 * The constructor is hooked with an after hook, passing the wrapper and the wrapped drawable.
 */
@StrictQuery
internal object TouchFeedbackDrawableConstructorFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/libraries/youtube/rendering/ui/spec/motion/TouchFeedbackDrawable;",
    name = "<init>",
    parameters = listOf("Landroid/graphics/drawable/Drawable;"),
    filters = listOf(
        methodCall(definingClass = "Landroid/graphics/drawable/Drawable;", name = "<init>"),
        methodCall(
            smali = "Landroid/graphics/drawable/Drawable;->setCallback(Landroid/graphics/drawable/Drawable\$Callback;)V",
            location = MatchAfterWithin(10)
        )
    )
)

/**
 * Shows the "Please wait" progress dialog before the download quality menu. Matches 2 methods:
 * the download button of a video and the download option of a menu. The patcher patch inserts
 * the call after ProgressDialog.show(), which the runtime port realizes with a scoped hook.
 */
@SkipTest
@StrictQuery
internal object DownloadProgressDialogFingerprint : Fingerprint(
    filters = listOf(
        resourceLiteral(ResourceType.STRING, "offline_stream_selection_waiting"),
        methodCall(smali = "Landroid/app/ProgressDialog;->show()V"),
    )
)

/**
 * Shows a menu of a command as a list popup anchored to the view that opened it, such as the
 * dropdown of the Shorts header, or the menus of the feed on the accounts of an A/B test.
 * Matches 1 or 2 methods. The patcher patch inserts the call after ListPopupWindow.show(),
 * which the runtime port realizes with a scoped hook.
 */
@SkipTest
@StrictQuery
internal object MenuListPopupWindowShowFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        methodCall(smali = "Landroid/widget/ListPopupWindow;->setDropDownGravity(I)V"),
        methodCall(
            smali = "Landroid/widget/ListPopupWindow;->setAnchorView(Landroid/view/View;)V",
            location = MatchAfterWithin(5)
        ),
        methodCall(
            smali = "Landroid/widget/ListPopupWindow;->show()V",
            location = MatchAfterWithin(5)
        ),
    )
)
