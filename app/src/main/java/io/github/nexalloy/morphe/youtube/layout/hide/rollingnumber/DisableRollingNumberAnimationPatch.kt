package io.github.nexalloy.morphe.youtube.layout.hide.rollingnumber

import app.morphe.extension.youtube.patches.DisableRollingNumberAnimationsPatch
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook

val DisableRollingNumber = patch(
    name = "Disable rolling number animations",
    description = "Adds an option to disable rolling number animations of video view count, " +
            "user likes, and upload time.",
) {
    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_disable_rolling_number_animations", summary = true),
    )

    // Animations are disabled by preventing an Image from being applied to the text span,
    // which prevents the animations from appearing.
    // Upstream jumps over the span-applying block (keeping the setText call). At runtime the
    // setSpan call is scoped-hooked and skipped instead, so the text is still set but without
    // the animated image span.
    RollingNumberTextViewAnimationUpdateFingerprint.hookMethod(scopedHook(::rollingNumberSetSpanMethod.method) {
        before {
            if (innerDepth == 0 && DisableRollingNumberAnimationsPatch.disableRollingNumberAnimations()) {
                it.result = null
            }
        }
    })
}
