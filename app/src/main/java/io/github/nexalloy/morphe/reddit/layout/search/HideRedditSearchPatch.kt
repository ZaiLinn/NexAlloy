/*
 * Adapted from Morphe's Hide Reddit search patch.
 * https://github.com/MorpheApp/morphe-patches
 */

package io.github.nexalloy.morphe.reddit.layout.search

import android.content.ComponentName
import android.content.pm.PackageManager
import app.morphe.extension.reddit.patches.HideRedditSearchPatch
import app.morphe.extension.reddit.settings.Settings
import io.github.nexalloy.morphe.reddit.misc.settings.SettingsHook
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch

/**
 * Activity of the Answers text selection entry of the contextual menu,
 * verified from the manifest of Reddit 2026.39.1. The activity is declared
 * disabled and its intent filter matches PROCESS_TEXT with text/plain;
 * Reddit enables the component at runtime when its experiment is on.
 */
private const val ANSWERS_TEXT_SELECTION_ACTIVITY =
    "com.reddit.answers.sharing.AnswersTextSelectionActivity"

internal val RedditHideRedditSearch = patch(
    name = "Hide Reddit search",
    description = "Adds an option to hide Reddit search from the text selection contextual menu.",
) {
    dependsOn(SettingsHook)
    val activity = ComponentName(appContext.packageName, ANSWERS_TEXT_SELECTION_ACTIVITY)

    fun setComponentState(state: Int) {
        appContext.packageManager.setComponentEnabledSetting(
            activity, state, PackageManager.DONT_KILL_APP,
        )
    }

    // The compiled patch changes the MIME type of the activity in the manifest.
    // The manifest cannot be changed at runtime, so instead keep the component
    // disabled: disabled components do not match intent filters, which removes
    // the entry from the text selection menus of other apps.
    val packageManagerClass = Class.forName("android.app.ApplicationPackageManager")
    packageManagerClass.getDeclaredMethod(
        "setComponentEnabledSetting", ComponentName::class.java, Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType,
    ).hookMethod {
        before {
            if (!Settings.HIDE_REDDIT_SEARCH.get()) return@before
            val component = it.args[0] as? ComponentName ?: return@before
            if (component.className != ANSWERS_TEXT_SELECTION_ACTIVITY) return@before
            // Block Reddit from re-enabling the component for its experiment.
            it.args[1] = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
    }

    if (Settings.HIDE_REDDIT_SEARCH.get() &&
        appContext.packageManager.getComponentEnabledSetting(activity)
        != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    ) {
        setComponentState(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
    }
    // When the setting is off, nothing is written: Reddit's own initializer rewrites the
    // component state on every launch, so it takes the component back without this
    // patch fighting it and no module-owned state is left behind.

    setExtensionIsPatchIncluded(HideRedditSearchPatch::class.java)
}
