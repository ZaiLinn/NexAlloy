package io.github.nexalloy.morphe.youtube.misc.fix.likebutton

import app.morphe.extension.youtube.patches.FixLikeButtonPatch
import io.github.nexalloy.morphe.youtube.misc.playertype.PlayerTypeHook
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.morphe.youtube.misc.playservice.is_21_46_or_greater
import io.github.nexalloy.patch

/**
 * Fixes https://github.com/MorpheApp/morphe-patches/issues/113.
 *
 * Upstream inserts the extension call before the return of the Lottie animation URL methods and
 * moves its result into the return register. The same is done here by changing the result of
 * each method after it runs.
 */
internal val fixLikeButtonPatch = patch(
    description = "Fixes the like button animations."
) {
    dependsOn(VersionCheck, PlayerTypeHook)

    // The model that wraps a Java proto was removed in 21.46.
    (::lottieAnimationUrlMethods.dexMethodList +
            if (!is_21_46_or_greater) ::lottieAnimationUrlProtoMethods.dexMethodList
            else emptyList()).forEach { method ->
        method.hookMethod {
            after {
                it.result = FixLikeButtonPatch.fixThemedLikeAnimations(it.result as String?)
            }
        }
    }
}
