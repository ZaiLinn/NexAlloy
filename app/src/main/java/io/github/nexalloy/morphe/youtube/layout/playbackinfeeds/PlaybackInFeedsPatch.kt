package io.github.nexalloy.morphe.youtube.layout.playbackinfeeds

import android.view.View
import android.view.ViewStub
import app.morphe.extension.youtube.patches.PlaybackInFeedsPatch
import app.morphe.extension.youtube.patches.PlaybackInFeedsPatch.PlaybackInFeedsController
import app.morphe.extension.youtube.settings.preference.PlaybackInFeedsListPreference
import io.github.nexalloy.createProxy
import io.github.nexalloy.morphe.ResourceType
import io.github.nexalloy.morphe.getResourceId
import io.github.nexalloy.morphe.shared.misc.settings.preference.ListPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.morphe.youtube.misc.playservice.is_21_06_or_greater
import io.github.nexalloy.morphe.youtube.misc.playservice.is_21_24_or_greater
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook

internal val playbackInFeedsPatch = patch(
    name = "Playback in feeds",
    description = "Adds controls for autoplay and seeking in YouTube feeds.",
) {
    dependsOn(VersionCheck)

    val getMode = PlaybackInFeedsGetModeFingerprint.method
    val setMode = PlaybackInFeedsSetModeFingerprint.method
    PlaybackInFeedsControllerConstructorFingerprint.hookMethod {
        after {
            if (it.hasThrowable()) return@after
            PlaybackInFeedsPatch.setController(it.thisObject.createProxy { reference ->
                object : PlaybackInFeedsController {
                    override fun patch_getPlaybackInFeedsMode(): Int =
                        reference.get()?.let { controller -> getMode.invoke(controller) as Int }
                            ?: PlaybackInFeedsPatch.MODE_ALWAYS_ON

                    override fun patch_setPlaybackInFeedsMode(mode: Int) {
                        reference.get()?.let { controller -> setMode.invoke(controller, mode) }
                    }
                }
            })
        }
    }

    PreferenceScreen.FEED.addPreferences(
        ListPreference(key = "morphe_playback_in_feeds", tag = PlaybackInFeedsListPreference::class.java)
    )

    if (is_21_06_or_greater) {
        ::enableSeekingUiGetter.hookMethod {
            after {
                if (!it.hasThrowable()) it.result = PlaybackInFeedsPatch.showSeekButtons(it.result as Boolean)
            }
        }
        if (is_21_24_or_greater) {
            val stubId = getResourceId(ResourceType.ID, "seek_backwards_toggle_container_stub")
            val inflate = ViewStub::class.java.getMethod("inflate")
            SeekBackwardsToggleInflateFingerprint.hookMethod(scopedHook(inflate) {
                after {
                    if (innerDepth == 0 && !it.hasThrowable() && (it.thisObject as ViewStub).id == stubId) {
                        PlaybackInFeedsPatch.hideEmptySeekBackwardsButton(it.result as View)
                    }
                }
            })
        }
        PreferenceScreen.FEED.addPreferences(SwitchPreference("morphe_playback_in_feeds_seek_buttons", summary = true))
    }
}
