/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3387
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package io.github.nexalloy.morphe.youtube.layout.feedrefresh

import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.anyInstruction
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findFieldListDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.string

@StrictQuery
internal object FeedExpirationFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        string("FEmemberships_and_purchases"),
        string("FEmembership_detail"),
        opcode(Opcode.ADD_LONG),
        fieldAccess(opcode = Opcode.IPUT_WIDE, definingClass = "this", type = "J", location = MatchAfterImmediately()),
        anyInstruction(opcode(Opcode.ADD_LONG), opcode(Opcode.ADD_LONG_2ADDR), location = MatchAfterWithin(10)),
        fieldAccess(opcode = Opcode.IPUT_WIDE, definingClass = "this", type = "J", location = MatchAfterImmediately()),
    ),
)

internal val feedExpirationFields = findFieldListDirect {
    listOf(3, 5).map { FeedExpirationFingerprint.instructionMatches[it].instruction.fieldRef!! }.distinct()
}

internal val feedRefreshScheduleMethod = findMethodDirect {
    val match = FeedExpirationFingerprint.match()
    val afterSecondField = match.instructionMatches!!.last().index + 1
    match.method.instructions.drop(afterSecondField).first {
        Opcode.fromInt(it.opcode) == Opcode.INVOKE_VIRTUAL &&
            it.methodRef?.declaredClass == match.method.declaredClass &&
            it.methodRef?.paramTypeNames == listOf("boolean") &&
            it.methodRef?.returnTypeName == "void"
    }.methodRef!!
}
