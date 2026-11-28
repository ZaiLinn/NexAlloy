/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3291
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Runtime port of "Force portrait orientation"
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/music/layout/orientation/ForcePortraitOrientationPatch.kt).
 *
 * The upstream patch injects the orientation call at the start of the main activity's
 * onCreate; the runtime port calls the same extension entry point before onCreate runs.
 */

package io.github.nexalloy.morphe.music.layout.orientation

import android.app.Activity
import app.morphe.extension.music.patches.ForcePortraitOrientationPatch
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.music.shared.MusicActivityOnCreateFingerprint
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.patch

val forcePortraitOrientationPatch = patch(
    name = "Force portrait orientation",
    description = "Adds an option to keep the app in portrait orientation when the device is rotated.",
) {
    PreferenceScreen.GENERAL.addPreferences(
        SwitchPreference("morphe_music_force_portrait_orientation", summary = true)
    )

    MusicActivityOnCreateFingerprint.hookMethod {
        before {
            ForcePortraitOrientationPatch.setRequestedOrientation(it.thisObject as Activity)
        }
    }
}
