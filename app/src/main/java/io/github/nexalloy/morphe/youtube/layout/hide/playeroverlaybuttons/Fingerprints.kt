package io.github.nexalloy.morphe.youtube.layout.hide.playeroverlaybuttons

import io.github.nexalloy.RequireAppVersion
import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.anyInstruction
import io.github.nexalloy.morphe.checkCast
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.literal
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.opcodeEnum
import io.github.nexalloy.morphe.resourceLiteral
import io.github.nexalloy.morphe.ResourceType
import io.github.nexalloy.morphe.indexOfFirstInstructionOrThrow
import io.github.nexalloy.morphe.Match
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData

/**
 * The constructor of the player overlays layout.
 */
internal object LayoutConstructorFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    filters = listOf(
        literal(159962),
        resourceLiteral(ResourceType.ID, "player_control_previous_button_touch_area"),
        resourceLiteral(ResourceType.ID, "player_control_next_button_touch_area"),
        methodCall(parameters = listOf("Landroid/view/View;", "I"))
    )
)

/**
 * 21.42+
 */
internal object OverlayCastButtonVisibilityFingerprint : Fingerprint(
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        checkCast("Landroidx/mediarouter/app/MediaRouteButton;"),
        methodCall(
            smali = "Landroid/view/View;->setVisibility(I)V",
            location = InstructionLocation.MatchAfterImmediately()
        ),
        checkCast("Lcom/google/android/libraries/youtube/mdx/mediaroute/entrypoint/MdxEntryPointButton;"),
        methodCall(
            smali = "Landroid/view/View;->setVisibility(I)V",
            location = InstructionLocation.MatchAfterImmediately()
        ),
        literal(11208L)
    )
)

/**
 * 21.35 - 21.41
 */
@RequireAppVersion("21.35.000", "21.42.000")
internal object OverlayCastButtonVisibilityLegacy2135Fingerprint : Fingerprint(
    classFingerprint = OverlayCastButtonVisibilityLegacyFingerprint,
    returnType = "V",
    parameters = listOf("Landroid/view/View;", "Z"),
    filters = listOf(
        methodCall(smali = "Landroid/view/View;->setVisibility(I)V"),
        methodCall(smali = "Landroid/view/View;->setEnabled(Z)V")
    )
)

/**
 * 21.34 and lower.
 */
internal object OverlayCastButtonVisibilityLegacyFingerprint : Fingerprint(
    isStatic = false,
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        anyInstruction(
            methodCall(
                definingClass = "this",
                parameters = listOf("Landroid/view/View;", "Z")
            ),
            methodCall(name = "setVisibility")
        ),
        literal(11208L)
    )
)

internal object InflateControlsGroupLayoutStubFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = emptyList(),
    returnType = "V",
    filters = listOf(
        resourceLiteral(ResourceType.ID, "youtube_controls_button_group_layout_stub"),
        methodCall(name = "inflate")
    )
)

/**
 * The method that produces the fullscreen button view. Other methods also look up the
 * fullscreen button; strict instruction matching disambiguates them.
 */
@StrictQuery
internal object FullscreenButtonFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Landroid/view/View;"),
    returnType = "V",
    filters = listOf(
        resourceLiteral(ResourceType.ID, "fullscreen_button"),
        opcode(Opcode.CHECK_CAST, InstructionLocation.MatchAfterWithin(10))
    )
)

internal object TitleAnchorFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        resourceLiteral(ResourceType.ID, "player_collapse_button"),
        opcode(Opcode.CHECK_CAST),

        resourceLiteral(ResourceType.ID, "title_anchor"),
        opcode(Opcode.MOVE_RESULT_OBJECT)
    )
)

internal object SubtitleButtonControllerFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = "Lcom/google/android/libraries/youtube/common/ui/TouchImageView;"
        ),
        resourceLiteral(ResourceType.STRING, "accessibility_captions_unavailable"),
        resourceLiteral(ResourceType.STRING, "accessibility_captions_button_name"),
    )
)

/**
 * The method of the layout constructor that inflates the view stub with the given resource id.
 * Upstream jumps over the call when the feature is hidden. At runtime the call is scoped-hooked
 * and skipped instead.
 */
context(dexkit: DexKitBridge)
internal fun layoutConstructorViewStubShowMethod(idName: String): MethodData {
    val method = LayoutConstructorFingerprint()
    val constIndex = method.indexOfFirstInstructionOrThrow(
        resourceLiteral(ResourceType.ID, idName)
    )
    val invokeIndex = method.indexOfFirstInstructionOrThrow(constIndex) {
        opcode == Opcode.INVOKE_VIRTUAL.ordinal &&
                methodRef?.paramCount == 2 &&
                methodRef?.paramTypeNames?.first() == "android.view.ViewStub"
    }
    return method.instructions[invokeIndex].methodRef!!
}

val autoplayToggleShowMethod = findMethodDirect {
    layoutConstructorViewStubShowMethod("autonav_toggle")
}

// The call whose receiver view is passed to the extension that hides the cast,
// previous & next, and settings buttons.
val playerControlsFindViewMethod = findMethodDirect {
    LayoutConstructorFingerprint.instructionMatches.last().instruction.methodRef!!
}

val castVisibilitySetMethod = findMethodDirect {
    OverlayCastButtonVisibilityFingerprint.instructionMatches[1].instruction.methodRef!!
}

/**
 * 21.35 - 21.41: the setVisibility call of the cast button visibility method.
 * Not resolved by the patch on 21.42+ (see [OverlayCastButtonVisibilityFingerprint]).
 */
@get:RequireAppVersion("21.35.000", "21.42.000")
val castVisibilityLegacySetMethod = findMethodDirect {
    OverlayCastButtonVisibilityLegacy2135Fingerprint.instructionMatches.first().instruction.methodRef!!
}

val controlsGroupInflateMethod = findMethodDirect {
    InflateControlsGroupLayoutStubFingerprint.instructionMatches.last().instruction.methodRef!!
}

/**
 * The method that produces the view register that the upstream patch replaces with the
 * return value of the extension.
 */
context(dexkit: DexKitBridge)
private fun producerOf(match: Match, matchIndex: Int): MethodData {
    val method = match.method
    val register = method.instructions[match.instructionMatches[matchIndex].index].register(0)
    val moveResultIndex = method.instructions
        .take(match.instructionMatches[matchIndex].index)
        .indexOfLast { it.opcodeEnum == Opcode.MOVE_RESULT_OBJECT && it.registerOrNull(0) == register }
    check(moveResultIndex > 0) { "Could not find the move-result of the produced view register" }
    // A move-result always immediately follows the invoke.
    return method.instructions[moveResultIndex - 1].methodRef!!
}

val fullscreenButtonViewMethod = findMethodDirect {
    producerOf(FullscreenButtonFingerprint.match(), 1)
}

val titleAnchorViewMethod = findMethodDirect {
    val match = TitleAnchorFingerprint.match()
    val moveResultIndex = match.instructionMatches.last().index
    // A move-result always immediately follows the invoke.
    match.method.instructions[moveResultIndex - 1].methodRef!!
}

val collapseButtonViewMethod = findMethodDirect {
    producerOf(TitleAnchorFingerprint.match(), 1)
}

val subtitleButtonViewField = findFieldDirect {
    SubtitleButtonControllerFingerprint.instructionMatches.first().instruction.fieldRef!!
}
