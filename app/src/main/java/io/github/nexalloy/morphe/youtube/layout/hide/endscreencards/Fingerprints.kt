package io.github.nexalloy.morphe.youtube.layout.hide.endscreencards

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.literal
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.resourceLiteral
import io.github.nexalloy.morphe.ResourceType
import org.luckypray.dexkit.DexAccessFlags

internal object LayoutCircleFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = emptyList(),
    returnType = "Landroid/view/View;",
    filters = listOf(
        resourceLiteral(ResourceType.LAYOUT, "endscreen_element_layout_circle"),
        opcode(Opcode.INVOKE_VIRTUAL, location = MatchAfterWithin(10)),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        opcode(Opcode.CHECK_CAST, location = MatchAfterImmediately())
    )
)

internal object LayoutIconFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = emptyList(),
    returnType = "Landroid/view/View;",
    filters = listOf(
        resourceLiteral(ResourceType.LAYOUT, "endscreen_element_layout_icon"),
        opcode(Opcode.INVOKE_VIRTUAL, location = MatchAfterWithin(10)),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        opcode(Opcode.CHECK_CAST, location = MatchAfterImmediately())
    )
)

internal object LayoutVideoFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC),
    parameters = emptyList(),
    returnType = "Landroid/view/View;",
    filters = listOf(
        resourceLiteral(ResourceType.LAYOUT, "endscreen_element_layout_video"),
        opcode(Opcode.INVOKE_VIRTUAL, location = MatchAfterWithin(10)),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        opcode(Opcode.CHECK_CAST, location = MatchAfterImmediately())
    )
)

internal object ShowEndscreenCardsFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            type = "Ljava/lang/String;"
        ),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = "Ljava/lang/String;",
            location = MatchAfterImmediately()
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            name = "ordinal",
            location = MatchAfterWithin(7)
        ),
        literal(5),
        literal(8),
        literal(9)
    ),
    custom = {
        accessFlags(DexAccessFlags.FINAL)
        declaredClass { methodCount(5) }
    }
)

// The inflate call that produces the end screen element views.
// Upstream inserts a call to the extension with the inflated view right after the check-cast
// of the inflate result. At runtime the inflate call is scoped-hooked instead.
internal val endscreenCircleInflateMethod = findMethodDirect {
    LayoutCircleFingerprint.instructionMatches[1].instruction.methodRef!!
}

internal val endscreenIconInflateMethod = findMethodDirect {
    LayoutIconFingerprint.instructionMatches[1].instruction.methodRef!!
}

internal val endscreenVideoInflateMethod = findMethodDirect {
    LayoutVideoFingerprint.instructionMatches[1].instruction.methodRef!!
}
