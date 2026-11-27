package io.github.nexalloy.morphe.music.interaction.remember.shufflestate

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.literal
import io.github.nexalloy.morphe.methodCall

/**
 * The static initializer of the shuffle mode enum, keeping the constant names as strings.
 * Example: `Lifp;` with SHUFFLE_OFF(0), SHUFFLE_ALL(1), SHUFFLE_DISABLED(2).
 */
internal object ShuffleEnumFingerprint : Fingerprint(
    name = "<clinit>",
    returnType = "V",
    parameters = listOf(),
    strings = listOf("SHUFFLE_OFF", "SHUFFLE_ALL", "SHUFFLE_DISABLED")
)

/**
 * The click listener of the shuffle button, identified by the logging interaction id.
 * Example: `Lhzk;->onClick(Landroid/view/View;)V`
 */
internal object ShuffleOnClickFingerprint : Fingerprint(
    name = "onClick",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
    filters = listOf(literal(45468L))
)

/**
 * The current shuffle state getter on the shuffle controller, derived from the shuffle
 * button click listener: the virtual call that returns the shuffle enum, before the
 * interaction logging. Example: `Ljuf;->b()Lifp;`
 */
internal val shuffleStateGetter = findMethodDirect {
    val onClick = ShuffleOnClickFingerprint.match()
    val enumClass = ShuffleEnumFingerprint.match().method.declaredClass!!.descriptor
    val instructions = onClick.method.instructions!!

    for (i in onClick.instructionMatches.first().index - 1 downTo 0) {
        val methodRef = instructions[i].methodRef
        if (Opcode.fromInt(instructions[i].opcode) == Opcode.INVOKE_VIRTUAL &&
            methodRef?.returnType?.descriptor == enumClass
        ) {
            return@findMethodDirect methodRef
        }
    }
    throw NoSuchMethodException("Shuffle state getter not found")
}

/**
 * The method of the shuffle controller that applies a new shuffle state, identified by the
 * switch over the shuffle enum ordinal and the change notification posted afterwards.
 * Example: `Ljuf;->g(Lifp;)V`
 */
internal object ShuffleApplyFingerprint : Fingerprint(
    definingClass = { shuffleStateGetter().declaredClass!!.descriptor },
    returnType = "V",
    filters = listOf(
        methodCall(
            definingClass = { ShuffleEnumFingerprint.match().method.declaredClass!!.descriptor },
            name = "ordinal",
            opcode = Opcode.INVOKE_VIRTUAL
        ),
        methodCall(name = "post")
    )
)
