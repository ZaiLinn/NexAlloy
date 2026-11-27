/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/2764
 *
 * Original hard forked code:
 * https://github.com/ReVanced/revanced-patches/commit/724e6d61b2ecd868c1a9a37d465a688e83a74799
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 *
 * Runtime port of the Morphe patcher patch of the same name
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/youtube/layout/player/overlay/CustomPlayerOverlayOpacityPatch.kt).
 * The patcher patch inserts the call to the extension after the ImageView of the player scrim
 * overlay is found. The runtime port hooks the same method and applies the opacity to the view
 * returned by the matching findViewById call.
 */

package io.github.nexalloy.morphe.youtube.layout.player.overlay

import android.view.View
import android.widget.ImageView
import app.morphe.extension.shared.ResourceUtils
import app.morphe.extension.shared.settings.preference.SeekBarPreference
import app.morphe.extension.youtube.patches.CustomPlayerOverlayOpacityPatch
import io.github.nexalloy.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.nexalloy.morphe.youtube.layout.buttons.overlay.PlayerOverlayButtonsSettings
import io.github.nexalloy.morphe.youtube.layout.buttons.overlay.addPlayerOverlayPreferences
import io.github.nexalloy.morphe.youtube.layout.sponsorblock.controlsOverlayFingerprint
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook

val customPlayerOverlayOpacityPatch = patch(
    name = "Custom player overlay opacity",
    description = "Adds an option to change the opacity of the video player background " +
            "when player controls are visible.",
) {
    dependsOn(PlayerOverlayButtonsSettings)

    addPlayerOverlayPreferences(
        NonInteractivePreference(
            key = "morphe_player_overlay_opacity",
            tag = SeekBarPreference::class.java,
            selectable = true
        )
    )

    // The method that creates the player overview is the same method the sponsor block patch
    // resolves (upstream CreatePlayerOverviewFingerprint matches the same method as
    // ControlsOverlayFingerprint). YouTube 21.45.82: opf.I().
    val scrimOverlayId = ResourceUtils.getIdIdentifier("scrim_overlay")
    ::controlsOverlayFingerprint.hookMethod(scopedHook(
        View::class.java.getDeclaredMethod("findViewById", Int::class.javaPrimitiveType)
    ) {
        after {
            if (it.args[0] == scrimOverlayId) {
                CustomPlayerOverlayOpacityPatch.changeOpacity(it.result as? ImageView ?: return@after)
            }
        }
    })
}
