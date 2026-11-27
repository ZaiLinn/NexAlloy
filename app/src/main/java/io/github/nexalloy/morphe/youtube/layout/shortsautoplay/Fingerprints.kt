/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */
package io.github.nexalloy.morphe.youtube.layout.shortsautoplay

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionFilter
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterAnywhere
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.anyInstruction
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.string
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.InstructionData
import org.luckypray.dexkit.result.MethodData

/**
 * The Shorts loop behavior enum class initializer.
 *
 * Anchor (21.45.82): `Lbluf;-><clinit>()V`
 */
@StrictQuery
internal object ReelEnumConstructorFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.STATIC, AccessFlags.CONSTRUCTOR),
    filters = listOf(
        string("REEL_LOOP_BEHAVIOR_UNKNOWN"),
        string("REEL_LOOP_BEHAVIOR_SINGLE_PLAY"),
        string("REEL_LOOP_BEHAVIOR_REPEAT"),
        string("REEL_LOOP_BEHAVIOR_END_SCREEN"),
        opcode(Opcode.RETURN_VOID),
    ),
)

/**
 * Anchor method of the Shorts playback class.
 *
 * Anchor (21.45.82): `Latox;`
 */
@StrictQuery
internal object ReelPlaybackRepeatParentFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        methodCall(
            definingClass = "Lj$/time/Instant;",
            name = "toEpochMilli",
            returnType = "J",
        ),
        string("r_tr"),
    ),
)

/**
 * The Shorts playback repeat method, where the loop behavior is looked up.
 *
 * Anchor (21.45.82): `Latox;->a(Larnb;)V`
 */
@StrictQuery
internal object ReelPlaybackRepeatFingerprint : Fingerprint(
    classFingerprint = ReelPlaybackRepeatParentFingerprint,
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        anyInstruction(
            methodCall(
                definingClass = "Lcom/google/common/util/concurrent/ListenableFuture;",
                name = "isDone",
                returnType = "Z",
            ),
            methodCall(
                definingClass = "Lj$/util/Optional;",
                name = "ofNullable",
                parameters = listOf("Ljava/lang/Object;"),
                returnType = "Lj$/util/Optional;",
            ),
        ),
        // The loop behavior lookup, returning the reel enum resolved by
        // [ReelEnumConstructorFingerprint]. The upstream patcher resolves the enum type
        // during patching; at runtime it is resolved lazily by this filter.
        ReelBehaviorLookupFilter(),
    ),
)

/**
 * Matches the call that looks up the Shorts loop behavior enum, which is only known
 * after [ReelEnumConstructorFingerprint] has matched. The call returns the enum and
 * takes two reference parameters.
 */
private class ReelBehaviorLookupFilter : InstructionFilter {
    override val location = MatchAfterAnywhere()

    context(bridge: DexKitBridge)
    override fun matches(enclosingMethod: MethodData, instruction: InstructionData): Boolean {
        val reference = instruction.methodRef ?: return false
        return isReelBehaviorLookup(reference)
    }

    companion object {
        /**
         * The lookup method returns the reel enum and takes exactly two
         * reference parameters.
         */
        context(bridge: DexKitBridge)
        fun isReelBehaviorLookup(reference: MethodData): Boolean {
            val enumDescriptor = runCatching {
                ReelEnumConstructorFingerprint.match().method.declaredClass!!.descriptor
            }.getOrNull() ?: return false

            return reference.returnType?.descriptor == enumDescriptor &&
                reference.paramTypes.size == 2
        }
    }
}

/** The loop behavior lookup method called by [ReelPlaybackRepeatFingerprint]. */
internal val reelBehaviorLookupMethod = findMethodDirect {
    ReelPlaybackRepeatFingerprint.match().method.instructions
        .first { instruction ->
            val reference = instruction.methodRef ?: return@first false
            Opcode.fromInt(instruction.opcode) == Opcode.INVOKE_VIRTUAL &&
                ReelBehaviorLookupFilter.isReelBehaviorLookup(reference)
        }
        .methodRef!!
}
