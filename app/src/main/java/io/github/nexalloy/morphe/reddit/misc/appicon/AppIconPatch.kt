/*
 * Adapted from Morphe's App icon patch.
 * https://github.com/MorpheApp/morphe-patches/pull/2937
 */

package io.github.nexalloy.morphe.reddit.misc.appicon

import app.morphe.extension.reddit.patches.AppIconPatch
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch

internal val RedditAppIcon = patch(
    name = "App icon",
    description = "Adds an option to select from the Reddit app icons available in the manifest.",
) {
    // The extension switches the launcher aliases with PackageManager on its own;
    // the runtime patch only registers the icon picker in the Morphe settings.
    setExtensionIsPatchIncluded(AppIconPatch::class.java)
}
