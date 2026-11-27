/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 *
 * Runtime port of the Morphe patcher fingerprints of the same names
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/youtube/layout/snackbar/Fingerprints.kt).
 * The LithoSnackbarLayoutFingerprint is not ported: in YouTube 21.45.82 the Litho snackbar is
 * held by the BottomUiContainer itself (kiu passes it to kiy), and the method the fingerprint
 * matches would pass an unrelated container of the main layout to the extension.
 */

package io.github.nexalloy.morphe.youtube.layout.snackbar

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.ResourceType
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.resourceLiteral

/**
 * The BottomUiContainer display call the snackbar display method makes.
 */
internal val snackbarDisplayCall = findMethodDirect {
    SnackbarDisplayFingerprint.match().instructionMatches[1].instruction.methodRef!!
}

/**
 * Shows a snackbar in the bottom UI container, unless the message asks to skip it.
 *
 * YouTube 21.45.82 (jadx ground truth): `khv.i(avua)`. The skip check is the interface call
 * `kim.o(kin)`, and the display call is `BottomUiContainer.r(kin, kio, olh)`. The patcher patch
 * overrides the skip check result; the runtime port skips the display call instead, which keeps
 * the callbacks of the method running.
 */
@StrictQuery
internal object SnackbarDisplayFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            parameters = emptyList(),
            returnType = "Z"
        ),
        methodCall(
            definingClass = "Lcom/google/android/apps/youtube/app/common/ui/bottomui/BottomUiContainer;",
            parameters = listOf("L", "L", "L"),
            returnType = "V"
        ),
        fieldAccess(
            opcode = Opcode.IPUT_BOOLEAN,
            definingClass = "Lcom/google/android/apps/youtube/app/common/ui/bottomui/BottomUiContainer;",
            type = "Z"
        )
    )
)

/**
 * Creates the view of a Litho snackbar shown in the bottom UI container.
 * Both layouts of the snackbar return the view at the same instruction.
 *
 * YouTube 21.45.82 (jadx ground truth): `kiy.a(kin, olh)`, which inflates
 * inset_elements_wrapper for the shown layout.
 */
@StrictQuery
internal object LithoSnackbarFingerprint : Fingerprint(
    returnType = "Landroid/view/View;",
    parameters = listOf("L", "L"),
    filters = listOf(
        resourceLiteral(ResourceType.LAYOUT, "inset_elements_wrapper"),
        methodCall(
            definingClass = "Landroid/widget/FrameLayout;",
            name = "setOutlineProvider"
        ),
        methodCall(
            definingClass = "Landroid/widget/FrameLayout;",
            name = "setBackgroundColor",
            location = MatchAfterWithin(6)
        ),
        opcode(Opcode.RETURN_OBJECT, location = MatchAfterWithin(6))
    )
)

@StrictQuery
internal object QuantumSnackbarFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/libraries/quantum/snackbar/Snackbar;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    parameters = listOf("Landroid/content/Context;", "Landroid/util/AttributeSet;"),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_DIRECT,
            name = "<init>"
        )
    )
)

@StrictQuery
internal object MaterialSnackbarFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/material/snackbar/Snackbar\$SnackbarLayout;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    parameters = listOf("Landroid/content/Context;", "Landroid/util/AttributeSet;"),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_DIRECT,
            name = "<init>"
        )
    )
)

@StrictQuery
internal object AppSnackbarFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/apps/youtube/app/common/ui/bottomui/AppSnackbar;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    parameters = listOf("Landroid/content/Context;"),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_DIRECT,
            name = "<init>"
        )
    )
)

@StrictQuery
internal object YouTubeSnackbarFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/apps/youtube/app/common/ui/bottomui/YouTubeSnackbar;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    parameters = listOf("Landroid/content/Context;", "Landroid/util/AttributeSet;", "I"),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_DIRECT,
            name = "<init>"
        )
    )
)

/**
 * A bar with a message and a button that promotes a feature, shown in place of a snackbar.
 */
@StrictQuery
internal object MealbarFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/apps/youtube/app/common/ui/bottomui/Mealbar;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    parameters = listOf("Landroid/content/Context;", "Landroid/util/AttributeSet;", "I"),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_DIRECT,
            name = "<init>"
        )
    )
)
