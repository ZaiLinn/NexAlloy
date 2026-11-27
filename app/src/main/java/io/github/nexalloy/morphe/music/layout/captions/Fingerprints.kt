package io.github.nexalloy.morphe.music.layout.captions

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchFirst
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findFieldListDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.string
import org.luckypray.dexkit.result.FieldData

/**
 * The caption controller class, identified by its static method that checks if
 * the system captions are enabled.
 *
 * YT Music 9.46.51: acft (CaptionController),
 * static boolean q(accl, CaptioningManager).
 */
private object SubtitleManagerClassFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.STATIC),
    returnType = "Z",
    parameters = listOf("L", "Landroid/view/accessibility/CaptioningManager;"),
    filters = listOf(
        fieldAccess("Ljava/util/concurrent/TimeUnit;->SECONDS:Ljava/util/concurrent/TimeUnit;"),
        methodCall("Landroid/view/accessibility/CaptioningManager;->isEnabled()Z"),
    )
)

/**
 * Sets the caption track of a caption track event.
 *
 * The event has the reason the track is set, which is 2 when the captions are enabled
 * automatically after the caption tracks of a video are loaded.
 * If the track is the option to disable captions, or if there is no track,
 * the default track of the caption tracks is used if there is one.
 *
 * YT Music 9.46.51: acft.n(Labdk;)V — dexdump verified:
 * iget of the reason field -> "" -> invoke achn.g()Z (is the disable option) ->
 * move-result -> if-eqz -> iget-boolean of the default track flag within 10.
 */
internal object SetCaptionTrackFingerprint : Fingerprint(
    classFingerprint = SubtitleManagerClassFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        // Reason the track is set.
        fieldAccess(
            opcode = Opcode.IGET,
            type = "I",
            location = MatchFirst()
        ),
        string(""),
        // If the track is the option to disable captions.
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            parameters = listOf(),
            returnType = "Z"
        ),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        // If the caption tracks of the video have a default track.
        fieldAccess(
            opcode = Opcode.IGET_BOOLEAN,
            location = MatchAfterWithin(10)
        ),
    )
)

/**
 * The reason the caption track is set.
 *
 * YT Music 9.46.51: Labdk.d.
 */
val captionSelectionReasonField = findFieldDirect {
    SetCaptionTrackFingerprint.instructionMatches[0].instruction.fieldRef!!
}

/**
 * If the caption track is the option to disable captions.
 *
 * YT Music 9.46.51: achn.g() — "DISABLE_CAPTIONS_OPTION".equals(this.a).
 */
val subtitleTrackIsDisableOptionMethod = findMethodDirect {
    SetCaptionTrackFingerprint.instructionMatches[2].instruction.methodRef!!
}

/**
 * The flag of the caption tracks that tells if they have a default track.
 *
 * YT Music 9.46.51: Lapaz.h.
 */
val hasDefaultTrackField = findFieldDirect {
    SetCaptionTrackFingerprint.instructionMatches.last().instruction.fieldRef!!
}

/**
 * The fields that hold the caption tracks the default track flag is read of:
 * this -> o (the caption tracks) -> b (the track selection state).
 *
 * Resolved by walking the field reads that produce the receiver register of the
 * default track flag read.
 */
val hasDefaultTrackOwnerFields = findFieldListDirect {
    val match = SetCaptionTrackFingerprint.match()
    val defaultTrack = match.instructionMatches.last()
    val instructions = match.method.instructions!!
    var receiver = defaultTrack.instruction.registers[1]
    val thisRegister = match.instructionMatches[0].instruction.registers[1] - 1
    var before = defaultTrack.index
    val fields = mutableListOf<FieldData>()
    while (receiver != thisRegister) {
        val producer = instructions.take(before).last {
            it.opcode == Opcode.IGET_OBJECT.ordinal && it.registers[0] == receiver
        }
        fields += producer.fieldRef!!
        receiver = producer.registers[1]
        before = producer.index
    }
    fields.reversed()
}
