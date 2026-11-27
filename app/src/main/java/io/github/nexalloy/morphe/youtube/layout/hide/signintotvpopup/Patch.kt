package io.github.nexalloy.morphe.youtube.layout.hide.signintotvpopup

import app.morphe.extension.youtube.patches.DisableSignInToTVPopupPatch
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch

val DisableSignInToTVPopup = patch(
    name = "Disable sign in to TV popup",
    description = "Adds options to disable the popups asking to sign into or connect to a TV " +
        "on the same local network.",
) {
    PreferenceScreen.MISC.addPreferences(
        SwitchPreference("morphe_disable_sign_in_to_tv_popup"),
        SwitchPreference("morphe_disable_connect_your_devices_popup"),
    )

    // Upstream inserts a call to the popup's dismiss method right before the popup method
    // returns, skipping the popup. At runtime the dismiss method is invoked after the popup
    // method has built its view instead.
    SignInToTVPopupFingerprint.hookMethod {
        after {
            if (DisableSignInToTVPopupPatch.disableSignInToTVPopup()) {
                runCatching {
                    ::signInToTVPopupDismissMethod.method.invoke(it.thisObject)
                }
            }
        }
    }

    // Upstream inserts a conditional return-void at the start of the command resolver.
    // At runtime the method body is skipped by setting the result of the void method.
    HandoffPromoCommandResolverFingerprint.hookMethod {
        before {
            if (DisableSignInToTVPopupPatch.disableConnectYourDevicesPopup()) {
                it.result = null
            }
        }
    }
}
