package io.github.nexalloy.morphe.youtube.misc.fix.pipchatbar

import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.youtube.shared.YOUTUBE_MAIN_ACTIVITY_CLASS_TYPE

internal object PipModeChangedFingerprint : Fingerprint(
    definingClass = YOUTUBE_MAIN_ACTIVITY_CLASS_TYPE,
    name = "onPictureInPictureModeChanged",
    returnType = "V",
    parameters = listOf("Z", "Landroid/content/res/Configuration;"),
)
