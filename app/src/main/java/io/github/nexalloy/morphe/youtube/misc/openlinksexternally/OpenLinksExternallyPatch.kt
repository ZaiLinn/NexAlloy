package io.github.nexalloy.morphe.youtube.misc.openlinksexternally

import android.content.Intent
import android.net.Uri
import app.morphe.extension.youtube.patches.OpenLinksExternallyPatch
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch

private const val CUSTOM_TABS_SERVICE_ACTION = "android.support.customtabs.action.CustomTabsService"

/**
 * The upstream patch rewrites every use of the CustomTabsService action string in the host,
 * replacing it with an empty string when the setting is enabled. At runtime the same string
 * can only enter the host through an intent action, which is rewritten here.
 */
private fun replaceAction(action: Any?): Any? {
    if (action == CUSTOM_TABS_SERVICE_ACTION) {
        return OpenLinksExternallyPatch.getIntent(CUSTOM_TABS_SERVICE_ACTION)
    }

    return action
}

val OpenLinksExternally = patch(
    name = "Open links externally",
    description = "Adds an option to always open links in your browser instead of with the in-app browser.",
) {
    PreferenceScreen.MISC.addPreferences(
        SwitchPreference("morphe_external_browser", summary = true),
    )

    // The in-app browser binds the CustomTabsService with an intent built from the action
    // string. An empty action makes the binding fail, and the link opens in the browser.
    listOf(
        Intent::class.java.getConstructor(String::class.java),
        Intent::class.java.getConstructor(String::class.java, Uri::class.java),
        Intent::class.java.getDeclaredMethod("setAction", String::class.java),
    ).forEach { member ->
        member.hookMethod {
            before { it.args[0] = replaceAction(it.args[0]) }
        }
    }
}
