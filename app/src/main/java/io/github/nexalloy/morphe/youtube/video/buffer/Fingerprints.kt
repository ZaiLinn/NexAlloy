/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3386
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package io.github.nexalloy.morphe.youtube.video.buffer

import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.anyInstruction
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findFieldListDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.literal
import io.github.nexalloy.morphe.opcode

/**
 * The player load control's shouldContinueLoading.
 *
 * Anchor (21.45.82): `Laplc;->e(Ldqh;)Z`.
 */
@StrictQuery
internal object ShouldContinueLoadingFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("L"),
    filters = listOf(
        // The buffered duration. 21.45+ reads it more than once.
        fieldAccess(opcode = Opcode.IGET_WIDE, type = "J"),
        opcode(Opcode.CMP_LONG, location = MatchAfterWithin(3)),
        literal(120000),
        literal(15000L),
    ),
)

/**
 * The player load control's onTracksSelected, where the memory limit is calculated.
 *
 * Anchor (21.45.82): `Laplc;->h(Ldqh;[Leem;)V`.
 */
@StrictQuery
internal object TracksSelectedFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L", "[L"),
    filters = listOf(
        literal(389),
        literal(38),
        anyInstruction(
            literal(1024, listOf(Opcode.MUL_INT_LIT16)),
            literal(10, listOf(Opcode.SHL_INT_LIT8)), // 21.40+
        ),
    ),
)

/**
 * The buffered duration field of the loading state passed to [ShouldContinueLoadingFingerprint].
 * Anchor (21.45.82): `Ldqh;->e:J`.
 */
internal val bufferedDurationField = findFieldDirect {
    ShouldContinueLoadingFingerprint.match().instructionMatches!!.first().instruction.fieldRef!!
}

/**
 * The int fields the calculated memory limit is stored to, before the allocator target
 * is updated. Anchor (21.45.82): `Laplc;->m:I` and `Laplc;->l:I`.
 */
internal val byteLimitFields = findFieldListDirect {
    val match = TracksSelectedFingerprint.match()
    // The calculated limit is stored to two adjacent int fields right after the shift,
    // before the allocator target is updated.
    match.method.instructions
        .drop(match.instructionMatches!!.last().index + 1)
        .filter { instruction -> Opcode.fromInt(instruction.opcode) == Opcode.IPUT }
        .take(2)
        .map { it.fieldRef!! }
}

/**
 * The allocator getter the memory limit is passed to.
 * Anchor (21.45.82): `Laplc;->o()Lefa;`.
 */
internal val allocatorGetterMethod = findMethodDirect {
    val match = TracksSelectedFingerprint.match()
    val invokes = match.method.instructions
        .mapIndexed { index, instruction -> index to instruction }
        .filter { (index, instruction) ->
            index > match.instructionMatches!!.last().index &&
                Opcode.fromInt(instruction.opcode) == Opcode.INVOKE_VIRTUAL &&
                instruction.methodRef?.paramTypeNames == listOf("int") &&
                instruction.methodRef?.returnTypeName == "void"
        }
    // The getter is the parameter-less virtual call right before the allocator target setter.
    val setterIndex = invokes.first().first
    match.method.instructions
        .take(setterIndex)
        .last { instruction ->
            Opcode.fromInt(instruction.opcode) == Opcode.INVOKE_VIRTUAL &&
                instruction.methodRef?.paramTypeNames?.isEmpty() == true &&
                instruction.methodRef?.declaredClass == match.method.declaredClass
        }
        .methodRef!!
}

/**
 * The allocator target setter invoked with the memory limit.
 * Anchor (21.45.82): `Lefa;->i(I)V`.
 */
internal val allocatorTargetMethod = findMethodDirect {
    val match = TracksSelectedFingerprint.match()
    match.method.instructions
        .drop(match.instructionMatches!!.last().index + 1)
        .first { instruction ->
            Opcode.fromInt(instruction.opcode) == Opcode.INVOKE_VIRTUAL &&
                instruction.methodRef?.paramTypeNames == listOf("int") &&
                instruction.methodRef?.returnTypeName == "void"
        }
        .methodRef!!
}
