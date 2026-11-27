package io.github.nexalloy.morphe.reddit.interaction.avatars

import app.morphe.extension.reddit.patches.HighQualityAvatarsPatch
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch

internal val HighQualityAvatars = patch(
    name = "High quality avatars",
    description = "Adds an option to load higher-resolution Reddit avatar images.",
) {
    ::redditGlideLoadGeneric.hookMethod {
        before { it.args[0] = HighQualityAvatarsPatch.getImageModel(it.args[0]) }
    }
    setExtensionIsPatchIncluded(HighQualityAvatarsPatch::class.java)
}
