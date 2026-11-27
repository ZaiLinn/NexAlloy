package io.github.nexalloy.morphe.music.interaction.trackswipe

import android.view.View
import app.morphe.extension.music.patches.DisableSwipeToChangeTrackPatch
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.hookMethod
import io.github.nexalloy.patch

/**
 * Runtime port of "Disable swipe to change track".
 *
 * Upstream replaces the calls to ViewPager2.setUserInputEnabled from the track pager
 * controller with calls into the extension, which decides from the pager id and the value
 * the app sets. The extension filter already ignores pagers other than the two track
 * pagers, so the runtime module hooks the setter itself and lets the extension decide
 * for every call. A new pager starts with the swipe enabled, so the swipe is also
 * disabled as soon as the controller keeps each pager.
 */
val disableSwipeToChangeTrackPatch = patch(
    name = "Disable swipe to change track",
    description = "Adds options to disable changing the track by swiping left or right " +
            "on the player or the miniplayer.",
) {
    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_music_disable_player_swipe_to_change_track", summary = true),
    )
    PreferenceScreen.addMiniplayerPreferences(
        SwitchPreference(
            "morphe_music_disable_miniplayer_swipe_to_change_track",
            titleKey = "morphe_music_disable_player_swipe_to_change_track_title",
            summary = true
        )
    )

    val viewPager2Class = ViewPagerSetUserInputEnabledFingerprint.declaredClass
    val setUserInputEnabled = ViewPagerSetUserInputEnabledFingerprint.method

    // The controller turns the swipe of each pager on whenever it shows the pager;
    // the extension decides instead, from the pager and the value the app sets.
    setUserInputEnabled.hookMethod {
        before {
            it.args[0] = DisableSwipeToChangeTrackPatch.isUserInputEnabled(
                it.thisObject as View,
                it.args[0] as Boolean
            )
        }
    }

    // The swipe of a new pager is on, so it is also disabled when the controller keeps it.
    TrackPagerControllerConstructorFingerprint.hookMethod {
        after {
            val controller = it.thisObject ?: return@after
            for (field in controller.javaClass.declaredFields) {
                val pager = runCatching {
                    field.isAccessible = true
                    field.get(controller)
                }.getOrNull() ?: continue
                if (viewPager2Class.isInstance(pager)) {
                    runCatching {
                        setUserInputEnabled(
                            pager,
                            DisableSwipeToChangeTrackPatch.isUserInputEnabled(pager as View, true)
                        )
                    }
                }
            }
        }
    }
}
