package io.github.nexalloy.morphe.music.interaction.remember.repeatstate

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.RequireAppVersion
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.OpcodesFilter
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect

@RequireAppVersion(minVersion = "9.32.00")
internal object RepeatTrackFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "L"),
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.IGET_OBJECT,
        Opcode.SGET_OBJECT,
        Opcode.CHECK_CAST,
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT,
        Opcode.IF_NEZ
    ),
    strings = listOf("w_st")
)

@RequireAppVersion(maxVersion = "9.31.99")
internal object RepeatTrackLegacyFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "L"),
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.IGET_OBJECT,
        Opcode.IGET_OBJECT,
        Opcode.SGET_OBJECT,
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT,
        Opcode.IF_NEZ
    ),
    strings = listOf("w_st")
)

/**
 * The call that resets the repeat mode to off when a new track starts, right after the
 * repeat mode comparison that gates it. Example: `Laeme;->w(Liek;Z)V`.
 *
 * Identified as the first method call after the comparison whose parameters include
 * the repeat mode enum class.
 */
internal val repeatResetMethod = findMethodDirect {
    val match = RepeatTrackFingerprint.match()
    val enumClass = match.instructionMatches[1].instruction.fieldRef!!.declaredClass!!.descriptor
    val fromIndex = match.instructionMatches.last().index + 1
    match.method.instructions!!.drop(fromIndex).firstOrNull {
        val methodRef = it.methodRef ?: return@firstOrNull false
        methodRef.paramTypes.any { parameter -> parameter.descriptor == enumClass }
    }?.methodRef ?: throw NoSuchMethodException("Repeat reset call not found")
}

/**
 * The repeat-off constant of the repeat mode enum, loaded from the constant read right
 * before the reset call. Example: `Liek;->a` (LOOP_OFF).
 */
internal val repeatOffField = findFieldDirect {
    val match = RepeatTrackFingerprint.match()
    val enumClass = match.instructionMatches[1].instruction.fieldRef!!.declaredClass!!.descriptor
    val fromIndex = match.instructionMatches.last().index + 1
    match.method.instructions!!.drop(fromIndex).firstOrNull {
        it.fieldRef?.declaredClass?.descriptor == enumClass
    }?.fieldRef ?: throw NoSuchFieldException("Repeat off constant not found")
}
