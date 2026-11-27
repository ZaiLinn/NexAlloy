/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package io.github.nexalloy.morphe.youtube.layout.shortsplayer

import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.literal
import io.github.nexalloy.morphe.opcode

@StrictQuery
internal object OpenVideosFullscreenPortraitFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L", "Lj$/util/Optional;"),
    filters = listOf(
        opcode(Opcode.MOVE_RESULT),
        literal(45666112L, location = MatchAfterWithin(5)),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterWithin(10)),
    ),
)

internal val fullscreenPortraitCondition = findMethodDirect {
    val match = OpenVideosFullscreenPortraitFingerprint.match()
    match.method.instructions[match.instructionMatches!!.first().index - 1].methodRef!!
}

internal val fullscreenPortraitFlagAccessor = findMethodDirect {
    val match = OpenVideosFullscreenPortraitFingerprint.match()
    match.method.instructions[match.instructionMatches!!.last().index - 1].methodRef!!
}
