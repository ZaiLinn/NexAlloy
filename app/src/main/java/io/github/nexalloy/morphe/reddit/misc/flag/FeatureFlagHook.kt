package io.github.nexalloy.morphe.reddit.misc.flag

import io.github.nexalloy.PatchExecutor

internal fun PatchExecutor.hookRedditFeatureFlag(transform: (String, Boolean) -> Boolean) {
    RedditFeatureFlagReader.hookMethod {
        after {
            if (!it.hasThrowable()) {
                val experiment = it.args[0] as? String ?: return@after
                val original = it.result as Boolean
                val updated = transform(experiment, original)
                if (original != updated) it.result = updated
            }
        }
    }
}
