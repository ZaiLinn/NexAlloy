package io.github.nexalloy.morphe.youtube.interaction.viewerdiscretion

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.string
import org.luckypray.dexkit.result.FieldData

internal object AdultContentRunnableFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        opcode(Opcode.IGET_OBJECT),
        string("allowControversialContent"),
        methodCall(
            parameters = listOf(),
            returnType = "Z",
            location = MatchAfterWithin(1)
        ),
        string("allowAdultContent")
    )
)

internal object AdultContentSetPropertiesFingerprint : Fingerprint(
    // Private in YouTube 21.47+.
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    strings = listOf(
        "lastAudioTurnedOnInlinePlaybackId",
        "lastAudioTurnedOffInlinePlaybackId",
        "captionsRequested",
    ),
    filters = listOf(
        opcode(Opcode.IGET_BOOLEAN),
        string("allowAdultContent", MatchAfterImmediately()),
        fieldAccess(opcode = Opcode.IGET_BOOLEAN, location = MatchAfterWithin(2)),
        string("allowControversialContent", MatchAfterImmediately()),
    )
)

/**
 * The method that decides if the viewer discretion dialog is skipped and the adult
 * content runnable is constructed. Its two boolean checks are forced to true by the patch.
 */
internal fun skipDiscretionDialogFingerprint(adultContentRunnableClass: String) = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        methodCall(smali = "Ljava/lang/Boolean;->booleanValue()Z"),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
        opcode(Opcode.INVOKE_VIRTUAL, MatchAfterWithin(4)),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
        methodCall(
            opcode = Opcode.INVOKE_DIRECT,
            name = "<init>",
            definingClass = adultContentRunnableClass,
            location = MatchAfterWithin(5)
        )
    )
)

/**
 * The method that stores the adult content properties of the video, which are forced
 * to true by the patch to unlock related videos for restricted videos.
 */
internal fun writeAdultContentPropertiesFingerprint(
    skipDiscretionDialogClass: String,
    adultContentField: FieldData,
    controversialContentField: FieldData,
) = Fingerprint(
    definingClass = skipDiscretionDialogClass,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        fieldAccess(reference = adultContentField, opcode = Opcode.IPUT_BOOLEAN),
        fieldAccess(
            reference = controversialContentField,
            opcode = Opcode.IPUT_BOOLEAN,
            location = MatchAfterWithin(3)
        ),
    )
)
