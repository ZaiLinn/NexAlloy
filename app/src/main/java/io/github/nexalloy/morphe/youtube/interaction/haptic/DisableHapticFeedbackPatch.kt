package io.github.nexalloy.morphe.youtube.interaction.haptic

import android.os.VibrationEffect
import app.morphe.extension.youtube.patches.DisableHapticFeedbackPatch
import app.morphe.extension.youtube.settings.Settings
import io.github.nexalloy.findClass
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch

/**
 * The field of the tap and hold haptics handler that stores the vibrator.
 */
internal val tapAndHoldVibratorField = findFieldDirect {
    TapAndHoldHapticsHandlerFingerprint.instructionMatches.last().instruction.fieldRef!!
}

private fun allHapticFeedbackDisabled() =
    Settings.DISABLE_HAPTIC_FEEDBACK_CHAPTERS.get() &&
            Settings.DISABLE_HAPTIC_FEEDBACK_PRECISE_SEEKING.get() &&
            Settings.DISABLE_HAPTIC_FEEDBACK_SEEK_UNDO.get() &&
            Settings.DISABLE_HAPTIC_FEEDBACK_TAP_AND_HOLD.get() &&
            Settings.DISABLE_HAPTIC_FEEDBACK_ZOOM.get()

val DisableHapticFeedback = patch(
    name = "Disable haptic feedback",
    description = "Adds an option to disable haptic feedback in the player for various actions.",
) {
    PreferenceScreen.PLAYER.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_disable_haptic_feedback",
            preferences = setOf(
                SwitchPreference("morphe_disable_haptic_feedback_chapters"),
                SwitchPreference("morphe_disable_haptic_feedback_precise_seeking"),
                SwitchPreference("morphe_disable_haptic_feedback_seek_undo"),
                SwitchPreference("morphe_disable_haptic_feedback_tap_and_hold"),
                SwitchPreference("morphe_disable_haptic_feedback_zoom"),
            )
        )
    )

    // Upstream injects an early return at the start of the four haptics methods.
    // At runtime the same is achieved by skipping the method when it is disabled.
    MarkerHapticsFingerprint.hookMethod {
        before { if (DisableHapticFeedbackPatch.disableChapterVibrate()) it.result = null }
    }

    ScrubbingHapticsFingerprint.hookMethod {
        before { if (DisableHapticFeedbackPatch.disablePreciseSeekingVibrate()) it.result = null }
    }

    SeekUndoHapticsFingerprint.hookMethod {
        before { if (DisableHapticFeedbackPatch.disableSeekUndoVibrate()) it.result = null }
    }

    ZoomHapticsFingerprint.hookMethod {
        before { if (DisableHapticFeedbackPatch.disableZoomVibrate()) it.result = null }
    }

    // Upstream transforms the vibrator read by the tap and hold haptics handler's run()
    // into null when it is disabled. At runtime the stored vibrator is replaced right
    // after the handler stores it, which run() then reads.
    val vibratorField = ::tapAndHoldVibratorField.field

    TapAndHoldHapticsHandlerFingerprint.constructor.hookMethod {
        after {
            vibratorField.set(
                it.thisObject,
                DisableHapticFeedbackPatch.disableTapAndHoldVibrate(vibratorField.get(it.thisObject))
            )
        }
    }

    // Upstream replaces every call to Vibrator.vibrate(VibrationEffect) and
    // Vibrator.vibrate(long) in the app with an extension call that suppresses the
    // vibration when all haptic feedback settings are enabled. At runtime the
    // equivalent is a process scoped hook of the two framework methods.
    val vibratorClass = "android.os.Vibrator".findClass(classLoader)

    vibratorClass.getDeclaredMethod("vibrate", VibrationEffect::class.java).hookMethod {
        before { if (allHapticFeedbackDisabled()) it.result = null }
    }

    @Suppress("DEPRECATION")
    vibratorClass.getDeclaredMethod("vibrate", Long::class.javaPrimitiveType).hookMethod {
        before { if (allHapticFeedbackDisabled()) it.result = null }
    }
}
