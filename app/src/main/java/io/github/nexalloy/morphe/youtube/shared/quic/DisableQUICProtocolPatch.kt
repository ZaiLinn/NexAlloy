package io.github.nexalloy.morphe.youtube.shared.quic

import app.morphe.extension.shared.patches.DisableQUICProtocolPatch
import io.github.nexalloy.PatchExecutor
import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.shared.misc.settings.preference.BasePreferenceScreen
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference

internal object CronetEngineBuilderFingerprint : Fingerprint(
    definingClass = "/CronetEngine\$Builder;",
    name = "enableQuic",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "L",
    parameters = listOf("Z"),
)

internal object ExperimentalCronetEngineBuilderFingerprint : Fingerprint(
    definingClass = "/ExperimentalCronetEngine\$Builder;",
    name = "enableQuic",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "L",
    parameters = listOf("Z"),
)

/**
 * Disables the QUIC (Quick UDP Internet Connections) network protocol.
 */
fun PatchExecutor.disableQUICProtocolPatch(preferenceScreen: BasePreferenceScreen.Screen) {
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
