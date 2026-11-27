package io.github.nexalloy.morphe.youtube.layout.hide.infocards

import android.view.View
import app.morphe.extension.youtube.patches.HideInfoCardsPatch
import app.morphe.extension.youtube.patches.components.InfoCardsFilter
import io.github.nexalloy.morphe.shared.misc.litho.filter.addLithoFilter
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.misc.litho.filter.LithoFilter
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook
import java.lang.reflect.Method

val HideInfoCards = patch(
    name = "Hide info cards",
    description = "Adds an option to hide info cards that creators add in the video player.",
) {
    dependsOn(LithoFilter)

    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_hide_info_cards"),
    )

    // Edit: This old non-litho code may be obsolete and no longer used by any supported versions.
    // Upstream inserts the extension call before the setVisibility call of the teaser view.
    // At runtime the setVisibility call is scoped-hooked and the extension is called with its receiver.
    InfoCardsIncognitoFingerprint.hookMethod(scopedHook(::infoCardsTeaserSetVisibilityMethod.method) {
        before {
            if (innerDepth == 0) {
                HideInfoCardsPatch.hideInfoCardsIncognito(it.thisObject as? View)
            }
        }
    })

    // Edit: This old non-litho code may be obsolete and no longer used by any supported versions.
    // Upstream skips the interface call when info cards are hidden. At runtime the call is
    // scoped-hooked and skipped instead.
    InfoCardsMethodCallFingerprint.hookMethod(scopedHook(::infoCardsMethodCallTargetMethod.method) {
        before {
            if (innerDepth == 0 && HideInfoCardsPatch.hideInfoCardsMethodCall()) {
                it.result = skippedResult(it.method as Method)
            }
        }
    })

    // Info cards can also appear as Litho components.
    addLithoFilter(InfoCardsFilter())
}

/** The default value of a method return type, used to skip the original method body. */
private fun skippedResult(method: Method): Any? = when (method.returnType) {
    java.lang.Boolean.TYPE -> false
    java.lang.Byte.TYPE, java.lang.Character.TYPE, java.lang.Short.TYPE, Integer.TYPE -> 0
    java.lang.Long.TYPE -> 0L
    java.lang.Float.TYPE -> 0f
    java.lang.Double.TYPE -> 0.0
    else -> null
}
