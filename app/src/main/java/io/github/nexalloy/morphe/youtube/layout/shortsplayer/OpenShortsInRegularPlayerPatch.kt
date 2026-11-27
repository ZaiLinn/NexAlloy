package io.github.nexalloy.morphe.youtube.layout.shortsplayer

import app.morphe.extension.youtube.patches.OpenShortsInRegularPlayerPatch
import app.morphe.extension.youtube.patches.OpenShortsInRegularPlayerPatch.ShortsPlayerType
import app.morphe.extension.youtube.patches.OpenVideosFullscreenHookPatch
import app.morphe.extension.youtube.settings.Settings
import io.github.nexalloy.atLast
import io.github.nexalloy.morphe.shared.misc.settings.preference.ListPreference
import io.github.nexalloy.morphe.youtube.misc.loadvideo.loadVideoHookPatch
import io.github.nexalloy.morphe.youtube.misc.navigation.NavigationBarHook
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.youtube.shared.interceptShortsIntent
import io.github.nexalloy.morphe.youtube.shared.openVideoIntentPatch
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook

@Volatile
private var forceNonFullscreenForShort = false

internal val openShortsInRegularPlayerPatch = patch(
    name = "Open Shorts in regular player",
    description = "Adds options to open Shorts in the regular video player.",
) {
    dependsOn(openVideoIntentPatch, loadVideoHookPatch, NavigationBarHook)

    OpenVideosFullscreenPortraitFingerprint.hookMethod(
        scopedHook(
            ::fullscreenPortraitCondition.method to {
                after {
                    if (innerDepth == 0 && !it.hasThrowable()) {
                        val original = it.result as Boolean
                        val updated = OpenVideosFullscreenHookPatch.doNotOpenVideoFullscreenPortrait(original)
                        it.result = updated
                        val suppressFlag = forceNonFullscreenForShort || (updated && !original)
                        forceNonFullscreenForShort = false
                        outerParam.setObjectExtra("nexalloyShortsFullscreenCondition", suppressFlag)
                    }
                }
            },
            ::fullscreenPortraitFlagAccessor.method to {
                after {
                    if (!it.hasThrowable() && it.args.atLast(2) == 45666112L &&
                        outerParam.getObjectExtra("nexalloyShortsFullscreenCondition") == true
                    ) {
                        it.result = false
                    }
                }
            },
        )
    )

    interceptShortsIntent { map, videoId ->
        forceNonFullscreenForShort = Settings.SHORTS_PLAYER_TYPE.get() == ShortsPlayerType.REGULAR_PLAYER
        val handled = OpenShortsInRegularPlayerPatch.onVideoIntentLoaded(map, videoId)
        if (!handled) {
            forceNonFullscreenForShort = false
        }
        handled
    }
    PreferenceScreen.SHORTS.addPreferences(ListPreference("morphe_shorts_player_type"))
}
