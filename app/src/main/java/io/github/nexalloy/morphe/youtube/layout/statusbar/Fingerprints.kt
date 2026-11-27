/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3337
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Runtime port of the Morphe patcher fingerprints of the same names
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/youtube/layout/statusbar/Fingerprints.kt).
 */

package io.github.nexalloy.morphe.youtube.layout.statusbar

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.methodCall

/**
 * Shows the view drawn behind the status bar and sets its color.
 *
 * YouTube 21.45.82 (jadx ground truth): `kfs.i(int)` of the status bar background controller.
 * The class is identified by `kfs.a()`, which brings the status bar background and its parent
 * to the front. The patcher patch inserts the hook after `View.setBackgroundColor(int)`,
 * which the runtime port realizes with a scoped hook of the same framework method.
 */
@StrictQuery
internal object StatusBarBackgroundShowClassFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        methodCall(smali = "Landroid/view/View;->bringToFront()V"),
        methodCall(smali = "Landroid/view/View;->getParent()Landroid/view/ViewParent;"),
        methodCall(smali = "Landroid/view/View;->bringToFront()V")
    )
)

@StrictQuery
internal object StatusBarBackgroundShowFingerprint : Fingerprint(
    classFingerprint = StatusBarBackgroundShowClassFingerprint,
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("I"),
    filters = listOf(
        methodCall(smali = "Landroid/view/View;->setVisibility(I)V"),
        methodCall(smali = "Landroid/view/View;->setBackgroundColor(I)V")
    )
)

/**
 * Maps the window insets to the insets the player is laid out with.
 * Reads the stable insets from the window insets.
 *
 * The patcher patch replaces the `Rect` created with `Rect(int, int, int, int)` before it is
 * returned. The runtime port replaces the method result instead, which is the same `Rect`.
 */
@StrictQuery
internal object PlayerStableInsetsFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/Object;",
    parameters = listOf("L", "L"),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = "Landroid/graphics/Rect;",
            location = MatchAfterImmediately()
        ),
        methodCall(name = "isInMultiWindowMode"),
        methodCall(smali = "Landroid/graphics/Rect;-><init>(IIII)V")
    )
)

/**
 * Sends a copy of the system window insets to the views that lay out below the status bar,
 * together with the view the insets are read from.
 *
 * The patcher patch passes the copied `Rect` and the view to the extension right after the
 * copy. The runtime port intercepts the `Rect(Rect)` copy constructor while this method runs.
 */
@StrictQuery
internal object SystemWindowInsetsChangedFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        methodCall(smali = "Landroid/graphics/Rect;-><init>(Landroid/graphics/Rect;)V"),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = "Landroid/view/View;",
            location = MatchAfterImmediately()
        )
    )
)
