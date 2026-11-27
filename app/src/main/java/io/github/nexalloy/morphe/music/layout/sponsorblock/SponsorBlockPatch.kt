package io.github.nexalloy.morphe.music.layout.sponsorblock

import android.graphics.Canvas
import android.graphics.Rect
import app.morphe.extension.music.sponsorblock.MusicSponsorBlockConfig
import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.sponsorblock.ui.SponsorBlockAboutPreference
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.music.interaction.downloads.musicSaveOfflineVideoMethod
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.music.video.information.musicVideoIdHook
import io.github.nexalloy.morphe.music.video.information.musicVideoInformationPatch
import io.github.nexalloy.morphe.music.video.information.musicVideoTimeHook
import io.github.nexalloy.morphe.music.video.information.onMusicCreateHook
import io.github.nexalloy.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.PreferenceCategory
import io.github.nexalloy.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.TextPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.screenInfoPreferenceCategory
import io.github.nexalloy.morphe.youtube.layout.sponsorblock.categoryPreference
import io.github.nexalloy.patch

/**
 * The SponsorBlock screen of the music settings. The key, icon and sorting match the upstream
 * music settings patch, so the screen and its preferences keep their translations.
 */
private val sponsorBlockScreen = PreferenceScreen.Screen(
    key = "morphe_settings_music_screen_09_sponsorblock",
    summaryKey = null,
    icon = "@drawable/morphe_settings_screen_10_sponsorblock",
    iconBold = "@drawable/morphe_settings_screen_10_sponsorblock_bold",
    layout = "@layout/morphe_preference_with_icon",
    sorting = PreferenceScreenPreference.Sorting.UNSORTED,
)

/**
 * Runtime port of the music SponsorBlock patch. The shared SponsorBlockApi is configured by
 * the extension before the video information initialize hook runs, the segment skips are
 * driven with the playback position and the segments of downloads are kept in sync.
 */
val musicSponsorBlockPatch = patch(
    name = "SponsorBlock",
    description = "Adds options to enable and configure SponsorBlock, which can skip non-music segments.",
) {
    dependsOn(musicVideoInformationPatch)

    sponsorBlockScreen.addPreferences(
        SwitchPreference("morphe_sb_enabled", summary = true),
        SwitchPreference("morphe_sb_toast_on_skip", summary = true),

        PreferenceCategory(
            key = "morphe_sb_diff_segments",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = setOf(
                categoryPreference("morphe_sb_sponsor_color"),
                categoryPreference("morphe_sb_selfpromo_color"),
                categoryPreference("morphe_sb_interaction_color"),
                categoryPreference("morphe_sb_intro_color"),
                categoryPreference("morphe_sb_outro_color"),
                categoryPreference("morphe_sb_preview_color"),
                categoryPreference("morphe_sb_hook_color"),
                categoryPreference("morphe_sb_filler_color"),
                categoryPreference("morphe_sb_music_offtopic_color")
            )
        ),

        PreferenceCategory(
            key = "morphe_sb_general",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference("morphe_sb_offline_segments", summary = true),
                SwitchPreference("morphe_sb_toast_on_connection_error", summary = true),
                TextPreference("morphe_sb_api_url")
            )
        ),

        screenInfoPreferenceCategory(
            key = "morphe_sb_about",
            preferences = setOf(
                NonInteractivePreference(
                    key = "morphe_sb_about_api",
                    titleKey = null,
                    tag = SponsorBlockAboutPreference::class.java,
                    selectable = true
                )
            )
        )
    )

    // region Fail-closed helper.

    fun hookRegion(name: String, block: () -> Unit) {
        runCatching(block).onFailure { Logger.printException({ "SponsorBlock hook failed: $name" }, it) }
    }

    // endregion

    // region Player state.

    // Configure the shared SponsorBlockApi and clear per-track state. Registered after the
    // video information initialize hook so player state is ready before SponsorBlock initializes.
    onMusicCreateHook(MusicSponsorBlockConfig::initialize)

    // Drives the skip-segment logic (~1 Hz).
    musicVideoTimeHook(MusicSponsorBlockConfig::setVideoTime)

    // Triggers the SponsorBlock API fetch when a new track loads.
    musicVideoIdHook(MusicSponsorBlockConfig::setCurrentVideoId)

    // endregion

    // region Compact-player seekbar.

    // Hook draw() to paint the segment markers over the timeline. The upstream injects right
    // after super.draw() so the markers are drawn every frame regardless of the scrubber thumb;
    // the runtime hook runs after the whole method instead, on every frame alike.
    hookRegion("segment time bars") {
        val rectField = ::musicTimeBarRectField.field
        MusicTimeBarDrawFingerprint.hookMethod {
            after {
                if (it.hasThrowable()) return@after
                val timeBar = it.thisObject ?: return@after
                val canvas = it.args[0] as? Canvas ?: return@after
                MusicSponsorBlockConfig.setSeekbarRectangle(rectField.get(timeBar) as? Rect)
                MusicSponsorBlockConfig.drawSegmentTimeBars(canvas)
            }
        }
    }

    // endregion

    // region Segments of downloads.

    // Save the segments of the songs and videos the offline video endpoint saves.
    hookRegion("offline video added") {
        ::musicSaveOfflineVideoMethod.method.hookMethod {
            before {
                MusicSponsorBlockConfig.offlineVideoAdded(it.args[0] as? String ?: return@before)
            }
        }
    }

    // Remove the segments of songs and videos that are removed from the downloads.
    hookRegion("offline video removed") {
        MusicRemoveOfflineTrackFingerprint.hookMethod {
            before {
                MusicSponsorBlockConfig.offlineVideoRemoved(it.args[0] as? String ?: return@before)
            }
        }

        // The action method removes the track with its own copy of the code, and returns
        // early without removing it if the app cannot remove it right now. The guard of the
        // early return is checked before the segments are deleted, like the upstream that
        // injects the call after the early return.
        val guardMethod = ::offlineTrackActionGuardMethod.method
        val guardField = ::offlineTrackActionGuardField.field
        MusicRemoveOfflineTrackActionFingerprint.hookMethod {
            before {
                val guard = guardField.get(it.thisObject) ?: return@before
                if (guardMethod.invoke(guard) as? Boolean != true) return@before
                MusicSponsorBlockConfig.offlineVideoRemoved(it.args[0] as? String ?: return@before)
            }
        }
    }

    // endregion
}
