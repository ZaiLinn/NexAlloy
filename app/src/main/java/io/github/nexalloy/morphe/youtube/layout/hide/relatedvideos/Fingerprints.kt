package io.github.nexalloy.morphe.youtube.layout.hide.relatedvideos

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.string
import io.github.nexalloy.morphe.youtube.misc.fix.videoactionbar.RelatedItemSectionFingerprint
import io.github.nexalloy.morphe.youtube.misc.fix.videoactionbar.WatchNextResponseModelClassResolverFingerprint
import io.github.nexalloy.morphe.youtube.misc.fix.videoactionbar.protobufListIsModifiableMethod
import io.github.nexalloy.morphe.youtube.shared.WatchNextResponseParserFingerprint
import org.luckypray.dexkit.DexKitBridge

/**
 * The method of the watch next response model that reads the sections of the watch page.
 * This is the same method the upstream patch adds its helper method to.
 */
context(dexkit: DexKitBridge)
private fun watchNextResultsMatch() =
    WatchNextResponseParserFingerprint.instructionMatches[2]
        .instruction.fieldRef!!.declaredClass!!.descriptor.let { resultsType ->
            Fingerprint(
                definingClass = WatchNextResponseModelClassResolverFingerprint.instructionMatches
                    .last().instruction.classRef!!.descriptor,
                accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.FINAL),
                returnType = "V",
                parameters = listOf(resultsType),
                filters = listOf(
                    fieldAccess(
                        opcode = Opcode.IGET_OBJECT,
                        definingClass = resultsType,
                        type = protobufListIsModifiableMethod(dexkit).declaredClass!!.descriptor
                    ),
                    methodCall(
                        opcode = Opcode.INVOKE_INTERFACE,
                        name = "iterator",
                        location = MatchAfterImmediately()
                    ),
                    fieldAccess(
                        opcode = Opcode.IGET_OBJECT,
                        type = RelatedItemSectionFingerprint.instructionMatches[1]
                            .instruction.fieldRef!!.declaredClass!!.descriptor
                    )
                )
            ).match()
        }

/** The field of a section that holds its ItemSectionRenderer. */
val relatedItemSectionRendererField = findFieldDirect {
    watchNextResultsMatch().instructionMatches.last().instruction.fieldRef!!
}

/** The continuations field of the watch next results. */
val relatedContinuationsField = findFieldDirect {
    WatchNextResponseParserFingerprint.instructionMatches[2].instruction.fieldRef!!
}

context(dexkit: DexKitBridge)
private fun shelfRendererMatch() = Fingerprint(
    returnType = "Ljava/util/List;",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        string("hint=%s,(%s=%s,cheatsheet=%b,key1=%s,w=%d,h=%d)"),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = relatedItemSectionRendererField(dexkit).declaredClass.descriptor
        ),
        fieldAccess(
            reference = relatedItemSectionRendererField(dexkit),
            opcode = Opcode.IGET_OBJECT
        )
    )
).match()

/** The field of a section that holds its ShelfRenderer (tablet related videos). */
val relatedShelfRendererField = findFieldDirect {
    shelfRendererMatch().instructionMatches[1].instruction.fieldRef!!
}

/** The static method that returns an empty immutable ProtoList. */
val emptyProtobufListMethod = findMethodDirect {
    val resultsClass = WatchNextResponseParserFingerprint.instructionMatches[2]
        .instruction.fieldRef!!.declaredClass!!.descriptor
    Fingerprint(
        definingClass = resultsClass,
        name = "<init>",
        returnType = "V",
        parameters = emptyList(),
        filters = listOf(
            methodCall(opcode = Opcode.INVOKE_STATIC, name = "emptyProtobufList")
        )
    ).instructionMatches.last().instruction.methodRef!!
}
