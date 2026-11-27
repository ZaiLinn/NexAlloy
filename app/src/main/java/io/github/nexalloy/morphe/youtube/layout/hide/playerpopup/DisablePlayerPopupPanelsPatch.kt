package io.github.nexalloy.morphe.youtube.layout.hide.playerpopup

import app.morphe.extension.youtube.patches.DisablePlayerPopupPanelsPatch
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch

val DisablePlayerPopupPanels = patch(
    name = "Disable player popup panels",
    description = "Adds an option to disable panels (such as live chat) from opening automatically.",
) {
    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_disable_player_popup_panels", summary = true),
    )

    // Upstream inserts a conditional return-void at the start of the method. At runtime the
    // method body is skipped by setting the result of the void method.
    PlayerPopupPanelsFingerprint.hookMethod {
        before {
            if (DisablePlayerPopupPanelsPatch.disablePlayerPopupPanels()) {
                it.result = null
            }
        }
    }
}
