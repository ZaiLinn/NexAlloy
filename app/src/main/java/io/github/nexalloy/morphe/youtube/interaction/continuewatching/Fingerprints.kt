package io.github.nexalloy.morphe.youtube.interaction.continuewatching

import io.github.nexalloy.RequireAppVersion
import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.anyInstruction
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.literal
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode

/**
 * Autoplay overlay method that compares the time since the last user interaction against the
 * server provided inactivity limit (in minutes). Once the limit is exceeded autoplay stops
 * counting down and the next video is not started.
 *
 * Right after the comparison the method checks whether something holds an autoplay block
 * (dialogs, bottom sheets, watch list scrolled past the first item...). A held block also
 * stops the countdown.
 */
internal object AutoplayInactivityLimitFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.SGET_OBJECT,
            definingClass = "Ljava/util/concurrent/TimeUnit;",
            name = "MINUTES"
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            definingClass = "Ljava/util/concurrent/TimeUnit;",
            name = "toMillis",
            location = MatchAfterWithin(5)
        ),
        opcode(Opcode.CMP_LONG, MatchAfterWithin(2)),
        anyInstruction(
            opcode(Opcode.IF_LTZ),
            opcode(Opcode.IF_GEZ), // 21.40+
            location = MatchAfterImmediately()
        ),
        // Autoplay block check.
        methodCall(
            parameters = listOf(),
            returnType = "Z",
            location = MatchAfterWithin(3)
        ),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
        opcode(Opcode.IF_NEZ, MatchAfterImmediately()),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            definingClass = "Landroid/animation/ObjectAnimator;",
            name = "ofObject"
        )
    )
)

// Playback progress can trigger the prompt independently of the autoplay overlay.
internal object InactivityLimitFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        literal(126827209L),
        fieldAccess(
            opcode = Opcode.SGET_OBJECT,
            definingClass = "Ljava/util/concurrent/TimeUnit;",
            name = "SECONDS"
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            definingClass = "Ljava/util/concurrent/TimeUnit;",
            name = "toMillis",
            location = MatchAfterWithin(5)
        ),
        opcode(Opcode.CMP_LONG, MatchAfterWithin(2)),
        opcode(Opcode.IF_LEZ, MatchAfterImmediately()),
        fieldAccess(
            opcode = Opcode.SGET_OBJECT,
            definingClass = "Ljava/util/concurrent/TimeUnit;",
            name = "MILLISECONDS",
            location = MatchAfterImmediately()
        ),
        opcode(Opcode.IF_GTZ, MatchAfterWithin(20))
    )
)

/**
 * Autoplay overlay method that decides if the 'Video paused. Continue watching?' confirm dialog
 * is shown when a video ends.
 */
internal object AutoplayConfirmDialogEnabledFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PROTECTED, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(
        literal(45381478L),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            returnType = "Z",
            location = MatchAfterWithin(2)
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            parameters = listOf(),
            returnType = "I",
            location = MatchAfterWithin(6)
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            definingClass = "Lj$/util/Optional;",
            name = "isPresent",
            location = MatchAfterWithin(6)
        )
    )
)

/**
 * Same as [AutoplayConfirmDialogEnabledFingerprint], but the feature flag is read
 * through a wrapper method. 21.29 and lower.
 */
@RequireAppVersion(maxVersion = "21.30.000")
internal object AutoplayConfirmDialogEnabledLegacyFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PROTECTED, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            parameters = listOf(),
            returnType = "Z"
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            parameters = listOf(),
            returnType = "I",
            location = MatchAfterWithin(6)
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            definingClass = "Lj$/util/Optional;",
            name = "isPresent",
            location = MatchAfterWithin(6)
        )
    )
)
