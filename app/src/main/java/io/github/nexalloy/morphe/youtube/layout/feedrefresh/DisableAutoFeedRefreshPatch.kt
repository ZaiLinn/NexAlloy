/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3387
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package io.github.nexalloy.morphe.youtube.layout.feedrefresh

import app.morphe.extension.youtube.patches.DisableAutoFeedRefreshPatch
import app.morphe.extension.youtube.settings.Settings
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch

private class FeedRefreshState(val initial: LongArray, var applied: Boolean = false)

internal val disableAutoFeedRefreshPatch = patch(
    name = "Disable auto feed refresh",
    description = "Adds an option to stop feeds from refreshing automatically after they become outdated.",
) {
    val deadlines = ::feedExpirationFields.fieldList
    val state = ThreadLocal<FeedRefreshState>()

    FeedExpirationFingerprint.hookMethod {
        before {
            if (!Settings.DISABLE_AUTO_FEED_REFRESH.get()) return@before
            val host = it.thisObject
            state.set(FeedRefreshState(LongArray(deadlines.size) { i -> deadlines[i].getLong(host) }))
        }
        after {
            val current = state.get() ?: return@after
            state.remove()
            if (it.hasThrowable() || current.applied || !Settings.DISABLE_AUTO_FEED_REFRESH.get()) return@after
            val host = it.thisObject
            if (deadlines.indices.any { i -> deadlines[i].getLong(host) != current.initial[i] }) {
                for (deadline in deadlines) {
                    deadline.setLong(host, DisableAutoFeedRefreshPatch.getFeedExpirationTime(deadline.getLong(host)))
                }
            }
        }
    }

    ::feedRefreshScheduleMethod.hookMethod {
        before {
            val current = state.get() ?: return@before
            if (current.applied || !Settings.DISABLE_AUTO_FEED_REFRESH.get()) return@before
            val host = it.thisObject
            for (deadline in deadlines) {
                deadline.setLong(host, DisableAutoFeedRefreshPatch.getFeedExpirationTime(deadline.getLong(host)))
            }
            current.applied = true
        }
    }

    PreferenceScreen.FEED.addPreferences(SwitchPreference("morphe_disable_auto_feed_refresh", summary = true))
}
