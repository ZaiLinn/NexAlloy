/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3847
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package io.github.nexalloy.morphe.youtube.interaction.shorts

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.methodCall

/**
 * 21.42+
 *
 * Anchor (21.45.82): `Latox;->b(J)V`
 */
@StrictQuery
internal object ReelPlaybackFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("J"),
    filters = listOf(
        fieldAccess(
            definingClass = "Ljava/util/concurrent/TimeUnit;",
            name = "MILLISECONDS",
        ),
        methodCall(
            name = "<init>",
            parameters = listOf("I", "L", "L", "Z"),
            location = MatchAfterWithin(15),
        ),
        methodCall(
            opcodes = listOf(Opcode.INVOKE_VIRTUAL),
            parameters = listOf("L"),
            returnType = "I",
            location = MatchAfterWithin(5),
        ),
    ),
)

/**
 * The user action constructor the app uses to advance to the next Short.
 */
internal val skipUserActionConstructor = findMethodDirect {
    ReelPlaybackFingerprint.match().instructionMatches!![1].instruction.methodRef!!
}

/**
 * The reel sequence controller method that performs a user action.
 */
internal val skipReelSequenceControllerMethod = findMethodDirect {
    ReelPlaybackFingerprint.match().instructionMatches!![2].instruction.methodRef!!
}

/**
 * 21.24+: the reel sequence controller is a field of the Shorts player.
 */
internal val skipReelSequenceControllerField = findFieldDirect {
    val match = ReelPlaybackFingerprint.match()
    val controllerDescriptor =
        match.instructionMatches!![2].instruction.methodRef!!.declaredClass!!.descriptor
    match.method.instructions.first { instruction ->
        Opcode.fromInt(instruction.opcode) == Opcode.IGET_OBJECT &&
            instruction.fieldRef?.type?.descriptor == controllerDescriptor
    }.fieldRef!!
}
