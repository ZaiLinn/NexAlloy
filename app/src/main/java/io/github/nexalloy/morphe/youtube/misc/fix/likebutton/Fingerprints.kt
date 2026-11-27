package io.github.nexalloy.morphe.youtube.misc.fix.likebutton

import io.github.nexalloy.RequireAppVersion
import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.findMethodListDirect
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode

internal object LottieAnimationViewTagFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PROTECTED, AccessFlags.FINAL),
    returnType = "V",
    filters = listOf(
        methodCall(
            opcodes = listOf(Opcode.INVOKE_INTERFACE, Opcode.INVOKE_INTERFACE_RANGE),
            parameters = listOf(),
            returnType = "Ljava/lang/String;"
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        methodCall(
            smali = "Lcom/airbnb/lottie/LottieAnimationView;->getTag(I)Ljava/lang/Object;",
            location = MatchAfterWithin(5)
        )
    )
)

/**
 * The implementations of the Lottie animation URL interface method, found by the interface and
 * the method name of the call in [LottieAnimationViewTagFingerprint]. The model backed by
 * native memory has more than 7 instructions.
 */
internal val lottieAnimationUrlMethods = findMethodListDirect {
    val reference =
        LottieAnimationViewTagFingerprint.instructionMatches.first().instruction.methodRef!!

    Fingerprint(
        interfaces = listOf(reference.declaredClass!!.descriptor),
        name = reference.name,
        accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
        returnType = "Ljava/lang/String;",
        parameters = listOf(),
    ).matchAllOrNull()?.map { it.method }?.filter { method ->
        val instructionCount = method.instructions?.size ?: 0
        instructionCount > 7
    } ?: emptyList()
}

/**
 * The implementations of the Lottie animation URL interface method that wrap a Java proto,
 * which has less than 7 instructions. Removed in 21.46.
 */
@get:RequireAppVersion(maxVersion = "21.46.000")
internal val lottieAnimationUrlProtoMethods = findMethodListDirect {
    val reference =
        LottieAnimationViewTagFingerprint.instructionMatches.first().instruction.methodRef!!

    Fingerprint(
        interfaces = listOf(reference.declaredClass!!.descriptor),
        name = reference.name,
        accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
        returnType = "Ljava/lang/String;",
        parameters = listOf(),
    ).matchAllOrNull()?.map { it.method }?.filter { method ->
        val instructionCount = method.instructions?.size ?: 0
        instructionCount < 7
    } ?: emptyList()
}
