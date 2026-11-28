/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/5082
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Runtime port of "Apple Music animated artwork"
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/music/layout/applemusicartwork/AppleMusicArtworkPatch.kt).
 *
 * The upstream patch injects the thumbnail capture right after the constructor of the player
 * controls overlay stores the artwork view; the runtime port reads the same field after the
 * constructor ran and hands the view to the extension.
 */

package io.github.nexalloy.morphe.music.layout.applemusicartwork

import android.widget.ImageView
import app.morphe.extension.music.patches.applemusicartwork.AppleMusicArtworkPatch
import io.github.nexalloy.morphe.music.misc.mediasession.addSetMetadataHook
import io.github.nexalloy.morphe.music.misc.mediasession.addSetPlaybackStateHook
import io.github.nexalloy.morphe.music.misc.mediasession.mediaSessionHookPatch
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.patch

val appleMusicArtworkPatch = patch(
    name = "Apple Music animated artwork",
    description = "Adds an option to show the animated album artwork of Apple Music in the player.",
) {
    dependsOn(mediaSessionHookPatch)

    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_music_apple_music_artwork", summary = true)
    )

    val thumbnailField = ::playerThumbnailField.field.apply { isAccessible = true }
    PlayerThumbnailFingerprint.hookMethod {
        after {
            if (it.hasThrowable()) return@after
            val thumbnail = thumbnailField.get(it.thisObject) as? ImageView ?: return@after
            AppleMusicArtworkPatch.setPlayerThumbnail(thumbnail)
        }
    }

    addSetMetadataHook(AppleMusicArtworkPatch::onSetMetadata)
    addSetPlaybackStateHook(AppleMusicArtworkPatch::onSetPlaybackState)
}
