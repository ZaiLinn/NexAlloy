/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.nexalloy.morphe.reddit.interaction.dialogs

import io.github.nexalloy.RequireAppVersion
import io.github.nexalloy.SkipTest
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.anyInstruction
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.findMethodListDirect
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.newInstance
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.string
import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Opcode

@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object FrequentUpdatesHandlerFingerprint : Fingerprint(
    definingClass = $$"Lcom/reddit/screens/pager/FrequentUpdatesHandler$handleFrequentUpdates$",
    name = "invokeSuspend",
    returnType = "Ljava/lang/Object;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            parameters = listOf(),
            returnType = "Z"
        ),
        opcode(
            Opcode.MOVE_RESULT,
            location = MatchAfterImmediately()
        ),
        opcode(
            Opcode.IF_NEZ,
            location = MatchAfterWithin(3)
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = "Lcom/reddit/domain/model/Subreddit;->getUserIsSubscriber()Ljava/lang/Boolean;",
            location = MatchAfterWithin(3)
        )
    )
)

@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object NSFWAlertEmitFingerprint : Fingerprint(
    filters = listOf(
        anyInstruction(
            // Many classes have a method named getOver18()
            methodCall(
                opcode = Opcode.INVOKE_VIRTUAL,
                smali = "Lcom/reddit/domain/model/Subreddit;->getOver18()Ljava/lang/Boolean;"
            ),
            methodCall( // 2026.17.0+
                opcode = Opcode.INVOKE_VIRTUAL,
                smali = "Lcom/reddit/domain/model/UserSubreddit;->getOver18()Ljava/lang/Boolean;"
            )
        ),
        anyInstruction(
            // Many classes have a method named getHasBeenVisited()
            methodCall(
                opcode = Opcode.INVOKE_VIRTUAL,
                smali = "Lcom/reddit/domain/model/Subreddit;->getHasBeenVisited()Z"
            ),
            methodCall( // 2026.17.0+
                opcode = Opcode.INVOKE_VIRTUAL,
                smali = "Lcom/reddit/domain/model/UserSubreddit;->getHasBeenVisited()Z"
            )
        ),
        opcode(
            Opcode.IF_NEZ,
            location = MatchAfterWithin(8)
        ),
        string("nsfwAlertDelegate"),
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            smali = "Lcom/reddit/session/Session;->isIncognito()Z"
        )
    )
)

@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object NSFWAlertDialogClassFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        string("NsfwAlertDialogScreenDelegate")
    )
)

@SkipTest
@StrictQuery
internal object NSFWAlertShowDialogFingerprint : Fingerprint(
    classFingerprint = NSFWAlertDialogClassFingerprint,
    returnType = "V",
    filters = listOf(
        anyInstruction(
            methodCall(
                opcode = Opcode.INVOKE_STATIC,
                returnType = "L",
                parameters = listOf(
                    "Landroid/content/Context;",
                    $$"Landroid/content/DialogInterface$OnClickListener;",
                    $$"Landroid/content/DialogInterface$OnClickListener;"
                )
            ),
            methodCall( // 2026.46.0+
                opcode = Opcode.INVOKE_STATIC,
                returnType = "L",
                parameters = listOf(
                    "Landroid/content/Context;",
                    $$"Landroid/content/DialogInterface$OnClickListener;",
                    $$"Landroid/content/DialogInterface$OnClickListener;",
                    "Lcom/reddit/nsfw/NsfwWarningTarget;"
                )
            )
        ),
        opcode(
            opcode = Opcode.MOVE_RESULT_OBJECT,
            location = MatchAfterImmediately()
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            returnType = "L",
            location = MatchAfterWithin(5)
        ),
        opcode(
            opcode = Opcode.MOVE_RESULT_OBJECT,
            location = MatchAfterImmediately()
        ),
        newInstance(
            type = "Ljava/lang/ref/WeakReference;",
            location = MatchAfterWithin(5)
        )
    )
)

internal val frequentUpdatesLoginCheck = findMethodDirect {
    FrequentUpdatesHandlerFingerprint.instructionMatches.first().instruction.methodRef!!
}

internal val nsfwVisitedCheck = findMethodDirect {
    NSFWAlertEmitFingerprint.instructionMatches[1].instruction.methodRef!!
}

internal val nsfwAlertDialogMethods = findMethodListDirect {
    NSFWAlertShowDialogFingerprint.matchAll(1..2).map { it.method }
}

internal val nsfwAlertCreateMethods = findMethodListDirect {
    NSFWAlertShowDialogFingerprint.matchAll(1..2)
        .map { it.instructionMatches[2].instruction.methodRef!! }
}
