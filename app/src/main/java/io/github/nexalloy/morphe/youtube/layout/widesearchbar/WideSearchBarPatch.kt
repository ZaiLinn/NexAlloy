/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/2221
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package io.github.nexalloy.morphe.youtube.layout.widesearchbar

import android.view.View
import android.view.ViewGroup
import app.morphe.extension.youtube.patches.WideSearchBarPatch
import io.github.nexalloy.morphe.shared.misc.settings.preference.ListPreference
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.youtube.misc.toolbar.hookToolBar
import io.github.nexalloy.morphe.youtube.misc.toolbar.toolBarHookPatch
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.morphe.youtube.misc.playservice.is_21_42_or_greater
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook
import org.luckypray.dexkit.wrap.DexMethod

internal val wideSearchBarPatch = patch(
    name = "Wide search bar",
    description = "Adds a wide search bar to the top of the home, subscription, notifications and You tabs.",
) {
    dependsOn(toolBarHookPatch, VersionCheck)

    if (!is_21_42_or_greater) {
        // Legacy targets use the app's own wide search bar, which is not ported.
        return@patch
    }

    PreferenceScreen.GENERAL.addPreferences(
        ListPreference("morphe_searchbar_type")
    )

    // Hide the search magnifier button of the toolbar, and remember it for the
    // wide search bar to click when it is tapped.
    hookToolBar(WideSearchBarPatch::setSearchButtonView)

    // Add the wide search bar to the toolbar. The upstream patcher injects the call
    // at every return of the view method; the after hook covers all returns.
    ActionbarRingoViewFingerprint.hookMethod {
        after {
            val toolbar = it.result as? View ?: return@after
            WideSearchBarPatch.initializeWideSearchbar(toolbar)
        }
    }

    // Add the wide search bar after the leading accessories of the You tab.
    // The upstream patcher injects the call after the container addView call.
    // The extension adds its own views, so the hook must not re-enter.
    val addViewMethod = DexMethod("Landroid/view/ViewGroup;->addView(Landroid/view/View;)V").toMethod()
    val inSearchbarCallback = ThreadLocal.withInitial { false }
    ToolbarLeadingAccessoriesFingerprint.hookMethod(scopedHook(addViewMethod) {
        after {
            if (inSearchbarCallback.get()) return@after
            inSearchbarCallback.set(true)
            try {
                WideSearchBarPatch.addLeadingAccessoriesSearchbar(it.thisObject as ViewGroup)
            } finally {
                inSearchbarCallback.set(false)
            }
        }
    })
}
