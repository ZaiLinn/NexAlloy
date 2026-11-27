package io.github.nexalloy.morphe.youtube.layout.updatescreen

import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.morphe.youtube.misc.playservice.is_20_28_or_greater
import io.github.nexalloy.patch

/**
 * Hides the YouTube undismissable 'Update your app' dialog.
 *
 * Upstream nulls the intent parameter of the app blocking check result constructor for 20.26
 * and lower (the check of is_20_27_or_greater is not available in the module; the closest
 * constant is is_20_28_or_greater, which only adds 20.27). The same is done here by changing
 * the constructor argument before it runs.
 */
internal val hideUpdateScreenPatch = patch(
    description = "Hides the YouTube undismissable 'Update your app' dialog."
) {
    dependsOn(VersionCheck)

    if (!is_20_28_or_greater) {
        AppBlockingCheckResultConstructorFingerprint.hookMethod {
            before { it.args[0] = null }
        }
    }
}
