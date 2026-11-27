/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/2695
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package io.github.nexalloy.morphe.youtube.misc.refreshrate

import android.app.Activity
import android.os.Bundle
import app.morphe.extension.shared.patches.BaseAppRefreshRatePatch
import app.morphe.extension.shared.settings.preference.AppRefreshRateListPreference
import app.morphe.extension.youtube.patches.AppRefreshRatePatch
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.shared.misc.settings.preference.ListPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.youtube.shared.YOUTUBE_MAIN_ACTIVITY_CLASS_TYPE
import io.github.nexalloy.patch
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import org.luckypray.dexkit.wrap.DexMethod

internal val appRefreshRatePatch = patch(
    name = "App refresh rate",
    description = "Adds an option to change the app refresh rate.",
) {
    // The patcher rewrites isPatchIncluded() to return true. Emulate that at runtime.
    setExtensionIsPatchIncluded(BaseAppRefreshRatePatch::class.java)

    // Every activity: apply the preferred refresh rate to its window.
    // The two-argument overload is hooked as well, because activities that only
    // override it would otherwise be missed. The call is idempotent.
    Activity::class.java.getDeclaredMethod("onCreate", Bundle::class.java).hookMethod {
        after { BaseAppRefreshRatePatch.setActivityRefreshRate(it.thisObject as Activity) }
    }
    Activity::class.java.getDeclaredMethod(
        "onCreate", Bundle::class.java, android.os.PersistableBundle::class.java
    ).hookMethod {
        after { BaseAppRefreshRatePatch.setActivityRefreshRate(it.thisObject as Activity) }
    }

    // Main activity: observe the player state for the "playback only" override types.
    DexMethod("$YOUTUBE_MAIN_ACTIVITY_CLASS_TYPE->onCreate(Landroid/os/Bundle;)V").hookMethod {
        before { AppRefreshRatePatch.initialize(it.thisObject as Activity) }
    }

    PreferenceScreen.MISC.addPreferences(
        noTitleUnsortedPreferenceCategory(
            NonInteractivePreference(
                key = "morphe_app_refresh_rate",
                summaryKey = null,
                tag = AppRefreshRateListPreference::class.java,
                selectable = true,
            ),
            ListPreference("morphe_app_refresh_rate_type"),
        )
    )
}
