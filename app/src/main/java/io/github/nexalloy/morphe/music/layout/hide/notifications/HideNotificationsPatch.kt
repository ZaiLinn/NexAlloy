package io.github.nexalloy.morphe.music.layout.hide.notifications

import android.view.View
import android.view.ViewGroup
import app.morphe.extension.music.patches.HideNotificationsPatch
import app.morphe.extension.shared.ResourceUtils
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.patch

val hideNotificationsPatch = patch(
    name = "Hide notifications button",
    description = "Adds options to hide the notifications bell of the top bar and its unread badge.",
) {
    PreferenceScreen.GENERAL.addPreferences(
        SwitchPreference("morphe_music_hide_notification_dot", summary = true),
        SwitchPreference("morphe_music_hide_activity_button", summary = true),
    )

    val topBarMenuItemClass = TopBarMenuItemViewFactoryFingerprint.declaredClass

    // The bell of the home top bar is a server-driven button and is identified by
    // its icon, so its menu item is never added while the button toggle is on.
    TopBarMenuItemAddFingerprint.hookMethod {
        before {
            if (!HideNotificationsPatch.isNotificationButtonHidden()) return@before
            if (HideNotificationsPatch.isNotificationButton(it.args[1])) {
                it.result = Unit
            }
        }
    }

    // The unread badge views of every top bar item stay hidden while the badge
    // toggle is on. The bell keeps its place, only its badge disappears.
    TopBarMenuItemViewFactoryFingerprint.hookMethod {
        after {
            if (it.hasThrowable()) return@after
            if (!HideNotificationsPatch.isNotificationDotHidden()) return@after
            val view = it.result as? ViewGroup ?: return@after

            val dotId = ResourceUtils.getIdIdentifier("new_content_dot")
            val countId = ResourceUtils.getIdIdentifier("new_content_count")
            view.findViewById<View>(dotId)?.visibility = View.GONE
            view.findViewById<View>(countId)?.visibility = View.GONE
        }
    }

    // Badge state updates would re-show the hidden views, so the unread consumer
    // of a top bar item does not run while the badge is hidden. The consumer is a
    // synthetic lambda that captures its top bar item in its only public field.
    val badgeTargetField = runCatching {
        TopBarBadgeUpdateFingerprint.declaredClass
            .getDeclaredField("a")
            .apply { isAccessible = true }
    }.getOrNull()
    TopBarBadgeUpdateFingerprint.hookMethod {
        before {
            if (!HideNotificationsPatch.isNotificationDotHidden()) return@before
            val target = runCatching {
                badgeTargetField?.get(it.thisObject)
            }.getOrNull()
            if (target != null && target.javaClass == topBarMenuItemClass) {
                it.result = Unit
            }
        }
    }
}
