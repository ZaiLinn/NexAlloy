package io.github.nexalloy.morphe.music.interaction.remember.shufflestate

import app.morphe.extension.music.settings.Settings
import app.morphe.extension.music.patches.RememberShuffleStatePatch
import app.morphe.extension.shared.Utils
import io.github.nexalloy.morphe.music.video.information.musicVideoIdHook
import io.github.nexalloy.morphe.music.video.information.musicVideoInformationPatch
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.hookMethod
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook
import java.lang.ref.WeakReference

/**
 * The shuffle controller instance, captured whenever the app reads the shuffle state
 * (the player controls re-read it on every update). Upstream instead captures the
 * controller from the button provider when the button is constructed.
 */
private var shuffleController: WeakReference<Any?> = WeakReference(null)

/**
 * Runtime port of "Remember shuffle state".
 *
 * Upstream saves the state the click listener reads, and applies the saved state to each
 * new track with a copy of the controller's apply method injected into the extension.
 * The runtime module saves through the extension's own save method, and applies the
 * saved state by calling the controller's getter and apply methods through reflection.
 */
val rememberShuffleStatePatch = patch(
    name = "Remember shuffle state",
    description = "Adds an option to remember the shuffle state when playing a new track or playlist.",
) {
    dependsOn(musicVideoInformationPatch)

    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_music_remember_shuffle_state"),
    )

    val stateGetter = ::shuffleStateGetter.method
    val applyMethod = ShuffleApplyFingerprint.method
    val shuffleAll = ShuffleEnumFingerprint.declaredClass.enumConstants
        .filterIsInstance<Enum<*>>()
        .first { it.name == "SHUFFLE_ALL" }

    // Keep the shuffle controller at hand: the player controls read the shuffle
    // state on every update, before and while any track plays.
    stateGetter.hookMethod {
        after {
            shuffleController = WeakReference(it.thisObject)
        }
    }

    // Save the state whenever the user taps the shuffle button. The click listener
    // toggles the state first and then reads the new state, which the scoped hook sees.
    ShuffleOnClickFingerprint.hookMethod(scopedHook(stateGetter) {
        after {
            val state = it.result as? Enum<*> ?: return@after
            RememberShuffleStatePatch.saveShuffleState(state)
        }
    })

    // Apply the saved shuffle-all state to each new track, like the injected
    // extension method upstream: turn shuffle on unless it already is.
    musicVideoIdHook {
        if (Settings.REMEMBER_SHUFFLE_STATE.get() && Settings.SAVED_SHUFFLE_STATE.get()) {
            Utils.runOnMainThreadDelayed({
                val controller = shuffleController.get() ?: return@runOnMainThreadDelayed
                runCatching {
                    val state = stateGetter(controller) as? Enum<*> ?: return@runCatching
                    if (state != shuffleAll) {
                        applyMethod(controller, shuffleAll)
                    }
                }
            }, 500)
        }
    }
}
