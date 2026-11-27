package io.github.nexalloy.morphe.youtube.layout.hide.endscreensuggested

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.methodCall

/**
 * The constructor of the autoplay ('autonav') controller, which reads the
 * 'main_app_autonav' setting.
 *
 * Anchor (21.45.82): `odg.<init>(afqe, afqe, Executor, asfn)`.
 */
private object AutoNavConstructorFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
    strings = listOf("main_app_autonav"),
)

/**
 * The method that returns whether autoplay is turned on.
 *
 * Anchor (21.45.82): `odg.j()Z`, the only public final ()Z method of the class.
 */
internal object AutoNavStatusFingerprint : Fingerprint(
    classFingerprint = AutoNavConstructorFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(),
)

/**
 * The teardown method of the player overlay that hides the end screen and resets the
 * layout change listener state. The second matched call is the method that shows the
 * suggested video end screen.
 *
 * Anchor (21.45.82): `och.d()V` stores the end screen state int, calls the end screen
 * method within 3 instructions and then removes the layout change listener of
 * `YouTubePlayerOverlaysLayout`.
 */
@StrictQuery
internal object RemoveOnLayoutChangeListenerFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        fieldAccess(opcode = Opcode.IPUT, type = "I"),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            parameters = listOf(),
            returnType = "V",
            location = MatchAfterWithin(3),
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            definingClass = "Lcom/google/android/apps/youtube/app/common/player/overlay/YouTubePlayerOverlaysLayout;",
            name = "removeOnLayoutChangeListener",
            returnType = "V",
        ),
    ),
)

/** The method that returns whether autoplay is turned on. */
internal val autoNavStatusMethod = findMethodDirect {
    AutoNavStatusFingerprint.run()
}

/**
 * The method that shows the suggested video end screen. It reads the autoplay controller
 * from a field of its declaring class and asks it whether autoplay is turned on.
 *
 * Anchor (21.45.82): `aroa.O()V`, which reads `this.D` (odg) and calls `odg.j()`.
 * The teardown method references it as `ode.O()`, the declaring class is walked up when
 * the method is resolved.
 */
internal val endScreenShowMethod = findMethodDirect {
    RemoveOnLayoutChangeListenerFingerprint.instructionMatches[1].instruction.methodRef!!
}
