package io.github.nexalloy.morphe.youtube.misc.fix.backtoexitgesture

import io.github.nexalloy.RequireAppVersion
import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.indexOfFirstInstructionOrThrow
import io.github.nexalloy.morphe.literal
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.string

internal object ScrollPositionFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PROTECTED, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        opcode(Opcode.IF_NEZ),
        methodCall(opcode = Opcode.INVOKE_DIRECT, location = MatchAfterImmediately()),
        string("scroll_position")
    )
)

/**
 * Back pressed in the feed, when there is no pane left to go back to.
 * Scrolls the feed to the top, and returns whether back was handled.
 *
 * The first match is the call that scrolls. Its interface has several implementations
 * that do the same, but this method is the only caller.
 *
 * Another method shares the signature; strict instruction matching disambiguates them.
 */
@StrictQuery
internal object RecyclerViewTopScrollingCallingMethodFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            returnType = "V",
            parameters = listOf(),
        ),
        literal(45359224L, location = MatchAfterWithin(5))
    )
)

/**
 * 21.44 and lower.
 */
@RequireAppVersion(maxVersion = "21.44.000")
internal object BackToRefreshFeatureFlagFingerprint : Fingerprint(
    filters = listOf(
        literal(45359221L)
    )
)

/**
 * The method that the scroll position handling calls directly (the second instruction match of
 * [ScrollPositionFingerprint]). Upstream inserts the extension call before the first
 * [android.support.v7.widget.RecyclerView] call of this method.
 */
internal val scrollingViewsCaller = findMethodDirect {
    ScrollPositionFingerprint.instructionMatches[1].instruction.methodRef!!
}

/**
 * The first RecyclerView call of [scrollingViewsCaller].
 */
internal val scrollingRecyclerViewCall = findMethodDirect {
    val caller = ScrollPositionFingerprint.instructionMatches[1].instruction.methodRef!!
    val index = caller.indexOfFirstInstructionOrThrow(
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            definingClass = "Landroid/support/v7/widget/RecyclerView;"
        )
    )
    caller.instructions?.get(index)?.methodRef!!
}
