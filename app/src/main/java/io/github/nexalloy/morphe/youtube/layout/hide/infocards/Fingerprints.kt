package io.github.nexalloy.morphe.youtube.layout.hide.infocards

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.ResourceType
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.resourceLiteral
import io.github.nexalloy.morphe.string
import io.github.nexalloy.morphe.indexOfFirstInstructionOrThrow

private object InfoCardsIncognitoParentFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string("player_overlay_info_card_teaser")
    )
)

internal object InfoCardsIncognitoFingerprint : Fingerprint(
    classFingerprint = InfoCardsIncognitoParentFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/Boolean;",
    parameters = listOf("L", "J"),
    filters = listOf(
        string("vibrator")
    )
)

internal object InfoCardsMethodCallFingerprint : Fingerprint(
    filters = listOf(
        opcode(Opcode.INVOKE_VIRTUAL),
        opcode(Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
        opcode(Opcode.INVOKE_INTERFACE, location = MatchAfterImmediately()),
        resourceLiteral(ResourceType.ID, "info_cards_drawer_header")
    ),
    strings = listOf("Missing ControlsOverlayPresenter for InfoCards to work.")
)

// The setVisibility call of the info card teaser view inside the incognito method.
internal val infoCardsTeaserSetVisibilityMethod = findMethodDirect {
    val method = InfoCardsIncognitoFingerprint()
    val invokeInstructionIndex = method.indexOfFirstInstructionOrThrow(
        methodCall(smali = "Landroid/view/View;->setVisibility(I)V")
    )
    method.instructions[invokeInstructionIndex].methodRef!!
}

// The interface call that is skipped when info cards are hidden. The call target is an
// interface method without an implementation, which cannot be hooked at runtime. The
// interface has a single implementation, which is the runtime hook target instead.
internal val infoCardsMethodCallTargetMethod = findMethodDirect {
    val reference = InfoCardsMethodCallFingerprint.instructionMatches[2].instruction.methodRef!!

    Fingerprint(
        interfaces = listOf(reference.declaredClass!!.descriptor),
        name = reference.name,
        accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
        returnType = "V",
        parameters = listOf(),
    ).run()
}
