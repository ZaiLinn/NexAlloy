/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/2221
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package io.github.nexalloy.morphe.youtube.layout.widesearchbar

import app.morphe.extension.shared.ResourceUtils
import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.ResourceType
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.getResourceId
import io.github.nexalloy.morphe.hasResourceId
import io.github.nexalloy.morphe.literal
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode

/**
 * Resolves a host resource identifier at runtime, the same way the upstream patcher
 * resolves resource literals when patching an apk.
 */
private fun hostResourceId(type: ResourceType, name: String): Long? =
    if (hasResourceId(type, name)) getResourceId(type, name).toLong() else null

/**
 * The toolbar image view, where the header logo and the search magnifier button live.
 *
 * Anchor (21.45.82): `Lhxt;->f(Lcfv;Landroid/view/View;)Landroid/view/View;`
 */
@StrictQuery
internal object YouTubeDoodlesImageViewFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Landroid/view/View;",
    parameters = listOf("L", "L"),
    filters = listOf(
        literal({ hostResourceId(io.github.nexalloy.morphe.ResourceType.ID, "youtube_logo") }),
    ),
)

/**
 * 21.42+
 *
 * Anchor (21.45.82): `Lhxt;->f(Lcfv;Landroid/view/View;)Landroid/view/View;`
 */
@StrictQuery
internal object ActionbarRingoViewFingerprint : Fingerprint(
    classFingerprint = YouTubeDoodlesImageViewFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Landroid/view/View;",
    parameters = listOf("L", "L"),
    filters = listOf(
        methodCall(
            definingClass = "Landroid/widget/ImageView;",
            name = "setContentDescription",
            parameters = listOf("Ljava/lang/CharSequence;"),
            opcodes = listOf(Opcode.INVOKE_VIRTUAL),
        ),
        methodCall(
            definingClass = "Landroid/widget/ImageView;",
            name = "setOnClickListener",
            parameters = listOf("Landroid/view/View\$OnClickListener;"),
            opcodes = listOf(Opcode.INVOKE_VIRTUAL),
        ),
        methodCall(
            definingClass = "Landroid/widget/ImageView;",
            name = "setClickable",
            parameters = listOf("Z"),
            opcodes = listOf(Opcode.INVOKE_VIRTUAL),
        ),
        opcode(Opcode.RETURN_OBJECT, location = MatchAfterImmediately()),
    ),
)

/**
 * Fills the toolbar leading accessories container, such as the "Accounts" button of the You tab.
 *
 * Anchor (21.45.82): `Lpvi;->b(Lkgc;Lkfx;ILkfx;ILkfx;)V`
 */
@StrictQuery
internal object ToolbarLeadingAccessoriesFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    filters = listOf(
        literal({ hostResourceId(io.github.nexalloy.morphe.ResourceType.ID, "leading_accessories_container") }),
        literal({ hostResourceId(io.github.nexalloy.morphe.ResourceType.ID, "leading_accessories_container_stub") }),
        literal({ hostResourceId(io.github.nexalloy.morphe.ResourceType.DIMEN, "actionbar_leading_accessories_padding") }),
        methodCall(
            definingClass = "Landroid/view/ViewGroup;",
            name = "setPadding",
            parameters = listOf("I", "I", "I", "I"),
            opcodes = listOf(Opcode.INVOKE_VIRTUAL),
        ),
        methodCall(
            definingClass = "Landroid/view/ViewGroup;",
            name = "addView",
            parameters = listOf("Landroid/view/View;"),
            opcodes = listOf(Opcode.INVOKE_VIRTUAL),
        ),
    ),
)
