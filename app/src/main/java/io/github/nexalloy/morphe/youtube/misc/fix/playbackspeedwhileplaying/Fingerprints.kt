package io.github.nexalloy.morphe.youtube.misc.fix.playbackspeedwhileplaying

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterLast
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.literal
import io.github.nexalloy.morphe.opcode

/**
 * This method is usually used to set the initial speed (1.0x) when playback starts from the feed.
 * For some reason, in the latest YouTube, it is invoked even after the video has already started.
 *
 * Several methods share the signature; strict instruction matching disambiguates them.
 */
@StrictQuery
internal object PlaybackSpeedInFeedsFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        opcode(Opcode.IGET),
        literal(1000, opcodes = listOf(Opcode.MUL_INT_LIT16), location = MatchAfterImmediately()),
        fieldAccess(
            opcode = Opcode.IGET_WIDE,
            definingClass = "this",
            type = "J",
            location = MatchAfterImmediately()
        ),
        literal(-1, opcodes = listOf(Opcode.CONST_WIDE_16), location = MatchAfterImmediately()),
        opcode(Opcode.CMP_LONG, location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        opcode(Opcode.IF_LEZ, location = MatchAfterImmediately()),
        opcode(Opcode.SUB_LONG_2ADDR, location = MatchAfterImmediately()),
        fieldAccess(
            opcode = Opcode.IGET,
            type = "F",
            location = MatchAfterLast()
        )
    )
)

/**
 * The playback speed field that the method reads right before the upstream insertion point.
 */
internal val playbackSpeedField = findFieldDirect {
    PlaybackSpeedInFeedsFingerprint.instructionMatches.last().instruction.fieldRef!!
}
