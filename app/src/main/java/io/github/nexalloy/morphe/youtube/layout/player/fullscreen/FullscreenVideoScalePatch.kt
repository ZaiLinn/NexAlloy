/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/2616
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Runtime port of the Morphe patcher patch of the same name
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/youtube/layout/player/fullscreen/FullscreenVideoScalePatch.kt).
 *
 * Deviations from the patcher patch:
 * - The player buttons (Show video scale button, Show video alignment button and their settings)
 *   are not ported: their icons and the legacy bottom control layout are copied into the
 *   resources of the patched app by the patcher, and the runtime module does not package them
 *   (a CopyResourcesTask entry would be needed, see app/build.gradle.kts).
 * - The PlayerView interface that gives the extension the video view of the player is not added,
 *   a runtime hook cannot implement an interface on a host class. The extension falls back to
 *   the video aspect ratio recorded from the adaptive formats.
 */

package io.github.nexalloy.morphe.youtube.layout.player.fullscreen

import android.view.View
import app.morphe.extension.youtube.patches.FullscreenVideoScalePatch
import io.github.nexalloy.morphe.shared.misc.settings.preference.ListPreference
import io.github.nexalloy.morphe.youtube.misc.playertype.PlayerTypeHook
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.youtube.video.format.hookAdaptiveFormat
import io.github.nexalloy.morphe.youtube.video.format.videoFormatPatch
import io.github.nexalloy.patch

val fullscreenVideoScalePatch = patch(
    name = "Fullscreen video scale",
    description = "Adds options to stretch or zoom videos to fill the screen in fullscreen mode, " +
            "or to align videos to the top or bottom of the screen.",
) {
    dependsOn(
        VersionCheck,
        PlayerTypeHook,
        videoFormatPatch,
    )

    PreferenceScreen.VIDEO.addPreferences(
        ListPreference("morphe_fullscreen_video_scale"),
        ListPreference("morphe_fullscreen_video_alignment")
    )

    // Records the encoded video size so Stretch/Zoom can map non 16:9 content.
    hookAdaptiveFormat(FullscreenVideoScalePatch::setVideoAspectRatio)

    // Called with YouTubePlayerOverlaysLayout, which the patcher patch inserts at the
    // start of every constructor of the class.
    ::youTubePlayerOverlaysLayoutConstructors.dexMethodList.forEach { constructor ->
        constructor.hookMethod {
            after { FullscreenVideoScalePatch.attachPlayerOverlay(it.thisObject as View) }
        }
    }

    // Called at the end of YouTubePlayerViewNotForReflection / the 16:9 PlayerView onLayout.
    YouTubePlayerViewOnLayoutFingerprint.hookMethod {
        after { FullscreenVideoScalePatch.onPlayerViewLayout(it.thisObject as View) }
    }
}
