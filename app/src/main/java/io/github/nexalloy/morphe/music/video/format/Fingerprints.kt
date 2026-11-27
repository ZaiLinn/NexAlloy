package io.github.nexalloy.morphe.music.video.format

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.findMethodFromToString
import io.github.nexalloy.morphe.newInstance
import io.github.nexalloy.morphe.shared.FormatStreamModelToStringFingerprint
import io.github.nexalloy.morphe.string
import io.github.nexalloy.morphe.opcode
import org.luckypray.dexkit.DexKitBridge

/**
 * YT Music 9.46.51: defpackage.wkf.toString().
 */
internal object VideoStreamingDataToStringFingerprint : Fingerprint(
    name = "toString",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/String;",
    filters = listOf(string("VideoStreamingData(itags="))
)

/**
 * YT Music 9.46.51: wkf(StreamingData, amfz, byte[], aowz, long, long, wjz, String, int,
 * boolean, boolean, emp, boolean, boolean) — reads the adaptive formats of the StreamingData
 * proto, the video id field of this, and stores them in a new list.
 */
internal object VideoStreamingDataConstructorFingerprint : Fingerprint(
    classFingerprint = VideoStreamingDataToStringFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT, definingClass = "Lcom/google/protos/youtube/api/innertube/StreamingDataOuterClass\$StreamingData;"),
        fieldAccess(opcode = Opcode.IGET_OBJECT, definingClass = "this", type = "Ljava/lang/String;"),
        newInstance("Ljava/util/ArrayList;"),
        fieldAccess(opcode = Opcode.IGET_OBJECT, definingClass = "Lcom/google/protos/youtube/api/innertube/StreamingDataOuterClass\$StreamingData;")
    )
)

context(dexkit: DexKitBridge)
private fun formatField(name: String) = FormatStreamModelToStringFingerprint().let { model ->
    val method = model.findMethodFromToString(name)
    Fingerprint(
        classFingerprint = FormatStreamModelToStringFingerprint,
        name = method.name,
        returnType = method.returnType!!.descriptor,
        filters = listOf(
            fieldAccess(opcode = Opcode.IGET_OBJECT, reference = FormatStreamModelToStringFingerprint.instructionMatches[5].instruction.fieldRef!!),
            fieldAccess(type = method.returnType!!.descriptor, location = MatchAfterImmediately())
        )
    ).instructionMatches[1].instruction.fieldRef!!
}

/**
 * The width, height, mimeType and itag fields of the format stream model,
 * resolved from the format getters the toString of the model calls.
 */
internal val formatWidthField = findFieldDirect { formatField("width") }
internal val formatHeightField = findFieldDirect { formatField("height") }
internal val formatMimeTypeField = findFieldDirect { formatField("mimeType") }
internal val formatItagField = findFieldDirect { formatField("itag") }

/**
 * The video id field of the video streaming data.
 */
internal val streamingVideoIdField = findFieldDirect {
    VideoStreamingDataConstructorFingerprint.instructionMatches[1].instruction.fieldRef!!
}

/**
 * The adaptive formats field of the StreamingData proto, read by the video streaming data constructor.
 */
internal val adaptiveFormatsField = findFieldDirect {
    VideoStreamingDataConstructorFingerprint.instructionMatches.last().instruction.fieldRef!!
}
