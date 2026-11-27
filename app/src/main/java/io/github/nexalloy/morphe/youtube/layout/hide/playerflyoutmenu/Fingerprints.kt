package io.github.nexalloy.morphe.youtube.layout.hide.playerflyoutmenu

import io.github.nexalloy.FindMethodListFunc
import io.github.nexalloy.RequireAppVersion
import io.github.nexalloy.SkipTest
import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.ResourceType
import io.github.nexalloy.morphe.findMethodListDirect
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.resourceLiteral

/**
 * 21.44+ captions bottom sheet body, without the header. The header of the sheet is set up
 * in a separate static method matched by [CaptionsOldBottomSheetHeaderFingerprint].
 *
 * 21.45.82: defpackage.qud.O and defpackage.quq.O (2 matches).
 */
@SkipTest
internal object CaptionsOldBottomSheetFooterFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Landroid/view/View;",
    parameters = listOf(
        "Landroid/view/LayoutInflater;",
        "Landroid/view/ViewGroup;",
        "Landroid/os/Bundle;"
    ),
    filters = listOf(
        resourceLiteral(ResourceType.STRING, "subtitle_menu_settings_footer_info"),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = "Landroid/widget/ListView;->addFooterView(Landroid/view/View;Ljava/lang/Object;Z)V"
        )
    )
)

val captionsOldBottomSheetFooterMethods = findMethodListDirect {
    CaptionsOldBottomSheetFooterFingerprint.matchAll(1..2).map { it.method }
}

/**
 * 21.44+ static method that sets up the header of the captions bottom sheet.
 *
 * 21.45.82: defpackage.rsw.V (unique match).
 */
internal object CaptionsOldBottomSheetHeaderFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "V",
    parameters = listOf("Landroid/view/View;", "Landroid/content/Context;", "L", "L"),
    filters = listOf(
        resourceLiteral(ResourceType.ID, "bottom_sheet_title"),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            name = "findViewById",
            location = MatchAfterWithin(3)
        )
    )
)

/**
 * Pre-21.44 captions bottom sheet, which sets up both the footer and the header of the sheet
 * in the layout inflater method. Does not exist in 21.45.82.
 */
@RequireAppVersion(maxVersion = "21.43.000")
internal object CaptionsOldBottomSheetLayoutInflaterFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Landroid/view/View;",
    parameters = listOf(
        "Landroid/view/LayoutInflater;",
        "Landroid/view/ViewGroup;",
        "Landroid/os/Bundle;"
    ),
    filters = listOf(
        resourceLiteral(ResourceType.ID, "bottom_sheet_title"),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            name = "findViewById",
            location = MatchAfterWithin(3)
        ),
        resourceLiteral(ResourceType.STRING, "subtitle_menu_settings_footer_info"),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = "Landroid/widget/ListView;->addFooterView(Landroid/view/View;Ljava/lang/Object;Z)V"
        )
    )
)

@get:RequireAppVersion(maxVersion = "21.43.000")
val captionsOldBottomSheetLayoutInflaterMethods: FindMethodListFunc = findMethodListDirect {
    CaptionsOldBottomSheetLayoutInflaterFingerprint.matchAll(1..2).map { it.method }
}

/**
 * Advanced video quality bottom sheet, which sets up its header and footer in the
 * layout inflater method.
 *
 * 21.45.82: defpackage.quf.O, defpackage.quw.O and defpackage.qut.a (3 matches).
 */
@SkipTest
internal object QualityOldBottomSheetLayoutInflaterFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Landroid/view/View;",
    parameters = listOf(
        "Landroid/view/LayoutInflater;",
        "Landroid/view/ViewGroup;",
        "Landroid/os/Bundle;"
    ),
    filters = listOf(
        resourceLiteral(ResourceType.LAYOUT, "video_quality_bottom_sheet_list_fragment_title"),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = "Landroid/widget/ListView;->addHeaderView(Landroid/view/View;Ljava/lang/Object;Z)V"
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = "Landroid/widget/ListView;->addFooterView(Landroid/view/View;Ljava/lang/Object;Z)V"
        )
    )
)

val qualityOldBottomSheetLayoutInflaterMethods = findMethodListDirect {
    QualityOldBottomSheetLayoutInflaterFingerprint.matchAll(2..3).map { it.method }
}
