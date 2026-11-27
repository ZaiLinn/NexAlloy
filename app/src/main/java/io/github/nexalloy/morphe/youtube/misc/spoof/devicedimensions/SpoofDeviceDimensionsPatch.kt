package io.github.nexalloy.morphe.youtube.misc.spoof.devicedimensions

import app.morphe.extension.youtube.patches.spoof.SpoofDeviceDimensionsPatch
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch

val SpoofDeviceDimensions = patch(
    name = "Spoof device dimensions",
    description = "Adds an option to spoof the device dimensions which can unlock higher video qualities.",
) {
    PreferenceScreen.MISC.addPreferences(
        SwitchPreference("morphe_spoof_device_dimensions", summary = true),
    )

    // Override the parameters containing the dimensions,
    // before they are assigned to the model fields.
    DeviceDimensionsModelConstructorFingerprint.hookMethod {
        before {
            it.args[0] = SpoofDeviceDimensionsPatch.getMinHeightOrWidth(it.args[0] as Int) // Min height.
            it.args[1] = SpoofDeviceDimensionsPatch.getMaxHeightOrWidth(it.args[1] as Int) // Max height.
            it.args[2] = SpoofDeviceDimensionsPatch.getMinHeightOrWidth(it.args[2] as Int) // Min width.
            it.args[3] = SpoofDeviceDimensionsPatch.getMaxHeightOrWidth(it.args[3] as Int) // Max width.
        }
    }
}
