package io.github.nexalloy.morphe.youtube.layout.hide.endscreencards

import android.view.View
import app.morphe.extension.youtube.patches.HideEndScreenCardsPatch
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook

val HideEndScreenCards = patch(
    name = "Hide end screen cards",
    description = "Adds an option to hide suggested video cards at the end of videos.",
) {
    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_hide_end_screen_cards"),
    )

    // Upstream inserts a call to the extension with the inflated end screen element view
    // of each element layout. At runtime the inflate call of each layout is scoped-hooked
    // and the extension is called with its result.
    LayoutCircleFingerprint.hookMethod(scopedHook(::endscreenCircleInflateMethod.method) {
        after {
            if (innerDepth == 0) {
                HideEndScreenCardsPatch.hideEndScreenCardView(it.result as? View)
            }
        }
    })
    LayoutIconFingerprint.hookMethod(scopedHook(::endscreenIconInflateMethod.method) {
        after {
            if (innerDepth == 0) {
                HideEndScreenCardsPatch.hideEndScreenCardView(it.result as? View)
            }
        }
    })
    LayoutVideoFingerprint.hookMethod(scopedHook(::endscreenVideoInflateMethod.method) {
        after {
            if (innerDepth == 0) {
                HideEndScreenCardsPatch.hideEndScreenCardView(it.result as? View)
            }
        }
    })

    // Upstream inserts a conditional return-void at the start of the method that shows
    // the end screen cards. At runtime the method body is skipped instead.
    ShowEndscreenCardsFingerprint.hookMethod {
        before {
            if (HideEndScreenCardsPatch.hideEndScreenCards()) {
                it.result = null
            }
        }
    }
}
