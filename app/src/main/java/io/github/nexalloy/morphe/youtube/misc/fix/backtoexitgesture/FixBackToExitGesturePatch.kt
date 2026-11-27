package io.github.nexalloy.morphe.youtube.misc.fix.backtoexitgesture

import app.morphe.extension.youtube.patches.FixBackToExitGesturePatch
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.insertLiteralOverride
import io.github.nexalloy.morphe.youtube.misc.backgesture.addBackPressedHook
import io.github.nexalloy.morphe.youtube.misc.backgesture.addPredictiveBackGestureHook
import io.github.nexalloy.morphe.youtube.misc.backgesture.backGesturePatch
import io.github.nexalloy.morphe.youtube.misc.playertype.PlayerTypeHook
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.morphe.youtube.misc.playservice.is_21_45_or_greater
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.hookMethod
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook

/**
 * Fixes the swipe back to exit gesture.
 *
 * Upstream inserts invoke-static calls into the bytecode. This runtime port installs the same
 * logic with Xposed hooks:
 * - The method that scrolls the feed to the top is hooked. On 21.45+ the scrolling is skipped
 *   entirely when the extension disallows it; on 21.44 and lower the back-to-refresh feature
 *   flag is overridden instead. The top position is marked after the method ran, which matches
 *   the upstream insertion point right after the call that scrolls.
 * - The extension is notified when the first RecyclerView call of the method that restores the
 *   scroll position runs, which matches the upstream insertion point before that call.
 * - The activity back press and the predictive back gesture are hooked through the shared
 *   back gesture patch.
 */
internal val fixBackToExitGesturePatch = patch(
    description = "Fixes the swipe back to exit gesture."
) {
    dependsOn(VersionCheck, PlayerTypeHook, backGesturePatch)

    PreferenceScreen.MISC.addPreferences(
        SwitchPreference("morphe_back_button_always_exits_feed", summary = true)
    )

    val scrollToTopSkipped = ThreadLocal<Boolean>()

    RecyclerViewTopScrollingCallingMethodFingerprint.hookMethod {
        before {
            scrollToTopSkipped.set(false)
            if (is_21_45_or_greater &&
                !FixBackToExitGesturePatch.allowBackButtonToScrollToTopOfFeed(true)
            ) {
                // Returning false skips the scrolling, and back then exits as usual.
                scrollToTopSkipped.set(true)
                it.result = false
            }
        }
        after {
            if (scrollToTopSkipped.get() == false) FixBackToExitGesturePatch.onTopView()
            scrollToTopSkipped.remove()
        }
    }

    if (!is_21_45_or_greater) {
        // 21.44 and lower decide with this feature flag whether back scrolls the feed to the top.
        insertLiteralOverride(
            45359221L,
            FixBackToExitGesturePatch::allowBackButtonToScrollToTopOfFeed
        )
    }

    // The RecyclerView call is only hooked while the caller that restores the scroll position
    // is running, so all other calls of the same RecyclerView method are unaffected.
    ::scrollingViewsCaller.method.hookMethod(
        scopedHook(::scrollingRecyclerViewCall.method) {
            before { FixBackToExitGesturePatch.onScrollingViews() }
        }
    )

    addBackPressedHook({ FixBackToExitGesturePatch.onBackInvoked() }, afterActivityBackPressed = true)
    addPredictiveBackGestureHook({ FixBackToExitGesturePatch.onBackInvoked() })
}
