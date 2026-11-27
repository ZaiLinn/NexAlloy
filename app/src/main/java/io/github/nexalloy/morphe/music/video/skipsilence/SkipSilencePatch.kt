package io.github.nexalloy.morphe.music.video.skipsilence

import app.morphe.extension.shared.patches.SkipSilencePatch
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.shared.misc.settings.preference.ListPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import io.github.nexalloy.patch
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.nio.ByteBuffer

private fun Field.getBooleanOf(instance: Any?): Boolean =
    if (Modifier.isStatic(modifiers)) getBoolean(null) else getBoolean(instance)

private fun Field.setBooleanOf(instance: Any?, value: Boolean) {
    if (Modifier.isStatic(modifiers)) setBoolean(null, value) else setBoolean(instance, value)
}

private fun Field.getShortOf(instance: Any?): Short =
    if (Modifier.isStatic(modifiers)) getShort(null) else getShort(instance)

private fun Field.setShortOf(instance: Any?, value: Short) {
    if (Modifier.isStatic(modifiers)) setShort(null, value) else setShort(instance, value)
}

val SkipSilence = patch(
    name = "Skip silence",
    description = "Adds an option to automatically skip silent pauses in audio playback."
) {
    // Upstream does not gate this patch by app version for YT Music:
    // the Media3 classes are compiled into the app and are not version specific.
    PreferenceScreen.PLAYER.addPreferences(
        noTitleUnsortedPreferenceCategory(
            SwitchPreference("morphe_skip_silence", summary = true),
            ListPreference("morphe_skip_silence_minimum_pause"),
        )
    )

    val enabledField = ::skipSilenceEnabledField.field
    val thresholdField = ::silenceThresholdLevelField.field

    // Upstream transforms the flag where it is read (applyAudioProcessorPlaybackParameters)
    // and where it is stored (setSkipSilenceEnabled). At runtime the same is achieved by
    // transforming the stored field before the reading method runs, and by transforming
    // the setter parameter.
    ApplySkipSilenceFingerprint.hookMethod {
        before {
            enabledField.setBooleanOf(
                it.thisObject,
                SkipSilencePatch.isSkipSilenceEnabled(enabledField.getBooleanOf(it.thisObject))
            )
        }
    }

    ::setSkipSilenceEnabledMethod.method.hookMethod {
        before {
            it.args[0] = SkipSilencePatch.isSkipSilenceEnabled(it.args[0] as Boolean)
        }
    }

    // Upstream transforms the constructor parameters:
    // p1 (J) minimum silence duration, p2 (F) retention ratio, p3 (J) max silence,
    // p4 (I) min volume percentage, p5 (S) threshold level.
    SilenceSkippingProcessorConstructorFingerprint.constructor.hookMethod {
        before {
            it.args[0] = SkipSilencePatch.minimumSilenceDurationUs(it.args[0] as Long)
            it.args[1] = SkipSilencePatch.silenceRetentionRatio(it.args[1] as Float)
            it.args[4] = SkipSilencePatch.silenceThresholdLevel(it.args[4] as Short)
        }
    }

    // Called with each buffer of decoded audio, before silence is searched in it.
    SilenceSkippingProcessorQueueInputFingerprint.hookMethod {
        before {
            SkipSilencePatch.updateSilenceThresholdLevel(it.args[0] as ByteBuffer)
        }
    }

    // Called when the audio is flushed, such as after seeking or when another video starts.
    SilenceSkippingProcessorFlushFingerprint.hookMethod {
        before {
            SkipSilencePatch.resetSilenceThresholdLevel()
        }
    }

    // Upstream transforms the threshold level where isNoise reads it.
    // At runtime the same is achieved by adapting the stored field before isNoise runs.
    SilenceSkippingProcessorIsNoiseFingerprint.hookMethod {
        before {
            thresholdField.setShortOf(
                it.thisObject,
                SkipSilencePatch.adaptiveSilenceThresholdLevel(thresholdField.getShortOf(it.thisObject))
            )
        }
    }
}
