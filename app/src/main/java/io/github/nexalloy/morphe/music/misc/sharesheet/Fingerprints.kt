package io.github.nexalloy.morphe.music.misc.sharesheet

import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.findClassDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode

/**
 * Command resolver of the share entity service endpoint.
 * With a network connection it opens the in-app share sheet (UnifiedSharePanelFragment).
 * Without one, it builds the music.youtube.com link of the song, playlist or artist
 * from the serialized share entity and opens the system share sheet.
 *
 * YT Music 9.46.51: gzf.c(akgm, Map) — dexdump verified instruction shape:
 * check-cast of the endpoint proto -> network check (invoke-virtual ()Z) ->
 * move-result -> if -> iget-object of the serialized share entity string.
 */
internal object ShareEntityServiceEndpointCommandFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L", "Ljava/util/Map;"),
    strings = listOf(
        "music.youtube.com",
        "navigation_endpoint",
        "UnifiedSharePanelFragment"
    ),
    filters = listOf(
        opcode(Opcode.CHECK_CAST),
        methodCall(
            returnType = "Z",
            parameters = listOf(),
            location = MatchAfterImmediately()
        ),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = "Ljava/lang/String;",
            location = MatchAfterWithin(5)
        )
    )
)

/**
 * The share entity service endpoint proto, the check cast target of the command resolver.
 *
 * YT Music 9.46.51: aqer.
 */
val shareEntityServiceEndpointClass = findClassDirect {
    ShareEntityServiceEndpointCommandFingerprint.instructionMatches[0].instruction.classRef!!
}

/**
 * The network check of the command resolver.
 *
 * YT Music 9.46.51: teh.k().
 */
val shareNetworkConnectedMethod = findMethodDirect {
    ShareEntityServiceEndpointCommandFingerprint.instructionMatches[1].instruction.methodRef!!
}

/**
 * The method that converts an extension value to the endpoint proto, called by the
 * command resolver right before it casts the result. The first conversion in the
 * method produces the endpoint the extension reads.
 *
 * YT Music 9.46.51: airn.c(Object)Object at instruction 0016, the only conversion of
 * the method.
 */
val shareEntityFromExtensionValueMethod = findMethodDirect {
    val match = ShareEntityServiceEndpointCommandFingerprint.match()
    match.method.instructions!!.first {
        it.opcode == Opcode.INVOKE_VIRTUAL.ordinal &&
                it.methodRef.let { ref ->
                    ref != null &&
                            ref.paramTypes.map { p -> p.descriptor } == listOf("Ljava/lang/Object;") &&
                            ref.returnType?.descriptor == "Ljava/lang/Object;"
                }
    }.methodRef!!
}

/**
 * The parser the offline path of the command resolver uses to parse the serialized share
 * entity, called with the serialized share entity string of the endpoint.
 *
 * YT Music 9.46.51: zkw.cj(String, aisz)MessageLite at instruction 0030, the only call
 * with a String first parameter returning a protobuf message.
 */
val parseSerializedEntityMethod = findMethodDirect {
    val match = ShareEntityServiceEndpointCommandFingerprint.match()
    match.method.instructions!!.first {
        it.opcode == Opcode.INVOKE_STATIC.ordinal &&
                it.methodRef.let { ref ->
                    ref != null &&
                            ref.paramTypes.firstOrNull()?.descriptor == "Ljava/lang/String;" &&
                            ref.returnType?.descriptor == "Lcom/google/protobuf/MessageLite;"
                }
    }.methodRef!!
}
