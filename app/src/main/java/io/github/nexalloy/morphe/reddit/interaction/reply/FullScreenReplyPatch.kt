package io.github.nexalloy.morphe.reddit.interaction.reply

import app.morphe.extension.reddit.patches.FullScreenReplyPatch
import io.github.nexalloy.morphe.reddit.misc.flag.hookRedditFeatureFlag
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch

internal val FullScreenReply = patch(
    name = "Full-screen reply",
    description = "Adds an option to use the full-screen reply composer.",
) {
    hookRedditFeatureFlag(FullScreenReplyPatch::useFullScreenReply)
    setExtensionIsPatchIncluded(FullScreenReplyPatch::class.java)
}
