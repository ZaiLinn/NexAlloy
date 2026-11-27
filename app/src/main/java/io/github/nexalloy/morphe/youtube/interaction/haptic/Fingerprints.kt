package io.github.nexalloy.morphe.youtube.interaction.haptic

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.checkCast
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.string

internal object MarkerHapticsFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("Failed to execute markers haptics vibrate.")
)

internal object ScrubbingHapticsFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("Failed to haptics vibrate for fine scrubbing.")
)

internal object SeekUndoHapticsFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("Failed to execute seek undo haptics vibrate.")
)

internal object TapAndHoldHapticsHandlerFingerprint : Fingerprint(
    name = "<init>",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
    parameters = listOf("Landroid/content/Context;", "Landroid/os/Handler;"),
    filters = listOf(
        string("vibrator"),
        checkCast("Landroid/os/Vibrator;"),
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            type = "Ljava/lang/Object;",
            location = MatchAfterImmediately()
        )
    )
)

internal object ZoomHapticsFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("Failed to haptics vibrate for video zoom")
)
