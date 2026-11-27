package io.github.nexalloy.morphe.music.misc.quic

import app.morphe.extension.shared.patches.DisableQUICProtocolPatch
import io.github.nexalloy.PatchExecutor
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.shared.misc.settings.preference.BasePreferenceScreen
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.patch

/**
 * Disables the QUIC (Quick UDP Internet Connections) network protocol.
 *
 * Upstream injects a call to the extension at the start of the builder setters; at runtime
 * the same is achieved by transforming the setter parameter before the original method runs.
 */
private fun PatchExecutor.disableQUICProtocolPatch(preferenceScreen: BasePreferenceScreen.Screen) {
    preferenceScreen.addPreferences(
        SwitchPreference("morphe_disable_quic_protocol"),
    )

    listOf(
        CronetEngineBuilderFingerprint,
        ExperimentalCronetEngineBuilderFingerprint,
    ).forEach { fingerprint ->
        fingerprint.hookMethod {
            before {
                // The extension returns false when the setting is enabled,
                // and the original value otherwise.
                it.args[0] = DisableQUICProtocolPatch.disableQUICProtocol(it.args[0] as Boolean)
            }
        }
    }
}

val DisableQUICProtocol = patch(
    name = "Disable QUIC protocol",
    description = "Adds an option to disable QUIC (Quick UDP Internet Connections) network protocol.",
) {
    disableQUICProtocolPatch(PreferenceScreen.MISC)
}
