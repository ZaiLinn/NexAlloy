/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/5004
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.nexalloy.morphe.reddit.misc.sharesheet

import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.findMethodListDirect
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.string
import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Opcode

internal const val SHARE_TRIGGER_CLASS = $$"Lcom/reddit/sharing/SharingNavigator$ShareTrigger;"

/**
 * Sends the event that loads the content of a share sheet, when the share sheet is opened.
 * Matches the event handler of the view model, the event and the call of the event handler.
 */
private fun screenInitFilters(viewModelClass: String) = listOf(
    fieldAccess(
        opcode = Opcode.IGET_OBJECT,
        definingClass = viewModelClass
    ),
    fieldAccess(
        opcode = Opcode.SGET_OBJECT,
        location = MatchAfterWithin(3)
    ),
    methodCall(
        opcode = Opcode.INVOKE_VIRTUAL,
        returnType = "Ljava/lang/Object;",
        location = MatchAfterWithin(5)
    )
)

/**
 * Share sheet that is used for all shares since 2026.44.0,
 * and before that by the "shareSheetOverhaulM2" experiment.
 */
@StrictQuery
internal object ActionSheetScreenInitFingerprint : Fingerprint(
    definingClass = $$"Lcom/reddit/sharing/actions/ActionsViewModel$viewState$2$1;",
    name = "invokeSuspend",
    returnType = "Ljava/lang/Object;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Ljava/lang/Object;"),
    filters = screenInitFilters("Lcom/reddit/sharing/actions/ActionsViewModel;")
)

/**
 * Share sheet that is used without the "shareSheetOverhaulM2" experiment.
 * Before 2026.44.0
 */
@StrictQuery
internal object ShareBottomSheetScreenInitFingerprint : Fingerprint(
    definingClass = $$"Lcom/reddit/sharing/custom/ShareViewModel$viewState$2$1;",
    name = "invokeSuspend",
    returnType = "Ljava/lang/Object;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Ljava/lang/Object;"),
    filters = screenInitFilters("Lcom/reddit/sharing/custom/ShareViewModel;")
)

/**
 * Arguments of the action sheet. Obfuscated before 2026.40.0.
 */
internal object ActionSheetArgsToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("Args(data="),
        string(", actions="),
        string(", showOnlyShareSheet=")
    )
)

/**
 * Arguments of the share bottom sheet. Obfuscated before 2026.40.0.
 */
internal object ShareBottomSheetArgsToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("Args(data="),
        string(", preview="),
        string(", showEducationPrompt=")
    )
)

/**
 * Compares an object with a static object, and returns [text] if they are equal.
 * Matches the static object.
 */
private fun equalsThenReturnStringFilters(text: String) = listOf(
    fieldAccess(opcode = Opcode.SGET_OBJECT),
    methodCall(
        smali = "Lkotlin/jvm/internal/Intrinsics;->areEqual(Ljava/lang/Object;Ljava/lang/Object;)Z",
        location = MatchAfterImmediately()
    ),
    opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
    opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
    string(text, location = MatchAfterImmediately())
)

/**
 * Builds the text to share, in each share sheet handler.
 * Matches the data of share sheets that do not share anything, such as overflow menus.
 */
@StrictQuery
private object NonShareableTextFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    filters = equalsThenReturnStringFilters("NonSharable")
)

/**
 * Click event of a share sheet action. Each share sheet has its own event class.
 */
@StrictQuery
private object ShareSheetActionClickedToStringFingerprint : Fingerprint(
    name = "toString",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("OnActionClicked(action=")
    )
)

/**
 * Analytics name of a share sheet action. Some app versions have more than one of these methods.
 * Matches the "Share via..." action, which opens the system share sheet.
 */
@StrictQuery
private object ShareViaActionNameFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Ljava/lang/String;",
    filters = equalsThenReturnStringFilters("share_via")
)

/**
 * Analytics name of a share sheet action. Some app versions have more than one of these methods.
 * Matches the "Share" action of overflow menus, which shows the share targets in the overflow menu.
 */
@StrictQuery
private object OpenShareSheetActionNameFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Ljava/lang/String;",
    filters = equalsThenReturnStringFilters("open_share_sheet")
)

/**
 * Handles a click on a share sheet action, such as an app shortcut, "Share via..." or the "Share" action
 * of overflow menus. The action is the first parameter.
 */
private val handleShareActionCoroutine = Fingerprint(
    definingClass = $$"Lcom/reddit/sharing/actions/handler/ActionsScreenShareActionHandler$handleAction$1;",
    name = "<init>",
)

internal val handleShareSheetAction = findMethodDirect {
    handleShareActionCoroutine().callers.single { it.returnTypeName == "java.lang.Object" }
}


internal val actionSheetEventHandler = findMethodDirect {
    checkNotNull(ActionSheetScreenInitFingerprint.instructionMatches.last().instruction.methodRef)
}

internal val bottomShareSheetEventHandler = findMethodDirect {
    checkNotNull(ShareBottomSheetScreenInitFingerprint.instructionMatches.last().instruction.methodRef)
}

internal val shareViaAction = findFieldDirect {
    ShareViaActionNameFingerprint.matchAll().map {
        checkNotNull(it.instructionMatches.first().instruction.fieldRef)
    }.distinctBy { it.descriptor }.single()
}

internal val overflowShareAction = findFieldDirect {
    OpenShareSheetActionNameFingerprint.matchAll().map {
        checkNotNull(it.instructionMatches.first().instruction.fieldRef)
    }.distinctBy { it.descriptor }.single()
}

internal val nonShareableData = findFieldDirect {
    NonShareableTextFingerprint.matchAll().map {
        checkNotNull(it.instructionMatches.first().instruction.fieldRef)
    }.distinctBy { it.descriptor }.single()
}

internal val shareActionEventConstructors = findMethodListDirect {
    ShareSheetActionClickedToStringFingerprint.matchAll().map { match ->
        checkNotNull(match.method.declaredClass).methods.single { it.name == "<init>" }
    }
}
