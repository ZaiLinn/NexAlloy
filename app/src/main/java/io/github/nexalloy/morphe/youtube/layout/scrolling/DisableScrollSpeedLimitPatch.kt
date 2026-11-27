package io.github.nexalloy.morphe.youtube.layout.scrolling

import android.support.v7.widget.RecyclerView
import app.morphe.extension.youtube.settings.Settings
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.morphe.youtube.misc.playservice.is_20_35_or_greater
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch

internal val disableScrollSpeedLimitPatch = patch(
    name = "Disable scrolling speed limit",
    description = "Adds an option to remove limits of how fast the home and subscription feeds can be scrolled.",
) {
    dependsOn(VersionCheck)
    if (!is_20_35_or_greater) return@patch

    val constructor = SnappyRecyclerViewSetFlingLimitFingerprint.constructor
    val limitEnabled = ::snappyRecyclerViewFlingLimitField.field
    val recyclerViewIndex = constructor.parameterTypes.indices.single { index ->
        val type = constructor.parameterTypes[index]
        RecyclerView::class.java.isAssignableFrom(type) && type.isAssignableFrom(limitEnabled.declaringClass)
    }
    constructor.hookMethod {
        after {
            if (it.hasThrowable() || !Settings.DISABLE_SCROLLING_SPEED.get()) return@after
            val recyclerView = it.args[recyclerViewIndex]
            if (limitEnabled.declaringClass.isInstance(recyclerView)) {
                limitEnabled.setBoolean(recyclerView, false)
            }
        }
    }

    PreferenceScreen.MISC.addPreferences(SwitchPreference("morphe_disable_scrolling_speed_limit"))
}
