package io.github.nexalloy.morphe.reddit.misc.debugging

import app.morphe.extension.reddit.patches.EnableDebuggingPatch
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.reddit.misc.flag.hookRedditFeatureFlag
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch

val EnableRedditDebugging = patch(
    name = "Enable debugging",
    description = "Exports Morphe logs and lets the user inspect and override Reddit experiments.",
    use = false,
) {
    val variant = ExperimentVariantFingerprint.method
    hookRedditFeatureFlag(EnableDebuggingPatch::isBooleanExperimentEnabled)
    variant.hookMethod {
        after {
            if (!it.hasThrowable()) {
                it.result = EnableDebuggingPatch.getExperimentVariant(
                    it.args[0] as String, it.result as? String,
                )
            }
        }
    }
    setExtensionIsPatchIncluded(EnableDebuggingPatch::class.java)
}
