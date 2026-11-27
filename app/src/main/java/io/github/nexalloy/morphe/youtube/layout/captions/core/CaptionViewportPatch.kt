package io.github.nexalloy.morphe.youtube.layout.captions.core

import android.view.View
import app.morphe.extension.youtube.patches.CaptionViewportPatch
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.morphe.youtube.misc.playservice.is_21_10_or_greater
import io.github.nexalloy.morphe.youtube.misc.playertype.PlayerTypeHook
import io.github.nexalloy.morphe.youtube.video.format.hookAdaptiveFormat
import io.github.nexalloy.morphe.youtube.video.format.videoFormatPatch
import io.github.nexalloy.morphe.youtube.video.videoid.VideoId
import io.github.nexalloy.morphe.youtube.video.videoid.hookVideoId
import io.github.nexalloy.morphe.shared.misc.settings.preference.NonInteractivePreference
import app.morphe.extension.shared.settings.preference.SeekBarPreference
import io.github.nexalloy.patch

/**
 * Runtime port of the upstream captionViewportPatch.
 *
 * Keeps fullscreen captions proportional to the fitted video on near-square screens.
 *
 * Upstream prepends calls that rewrite the height parameter of the native caption font size
 * calculation, and the font size of the native font size setter. At runtime the arguments are
 * rewritten in a before hook instead, which is equivalent.
 */
val CaptionViewport = patch(
    name = "Caption viewport",
    description = "Keeps fullscreen captions proportional to the fitted video on near-square screens.",
) {
    dependsOn(VersionCheck, videoFormatPatch, VideoId, PlayerTypeHook)

    if (!is_21_10_or_greater) {
        return@patch
    }

    hookVideoId(CaptionViewportPatch::onVideoId)
    hookAdaptiveFormat { videoId, formats -> CaptionViewportPatch.onFormats(videoId, formats) }

    settingsMenuCaptionGroup.add(
        NonInteractivePreference(
            "morphe_caption_scale",
            tag = SeekBarPreference::class.java,
            selectable = true
        )
    )

    // Font size is calculated from the overlay size. Use the height of the fitted video instead.
    ::captionFontSizeMethod.hookMethod {
        before { param ->
            param.args[2] = CaptionViewportPatch.normalizeHeight(
                param.thisObject as View,
                param.args[0] as Float,
                param.args[1] as Int,
                param.args[2] as Int,
            )
        }
    }

    // All native font paths end at this setter, including overrides of the base formula.
    ::captionWindowFontSizeMethod.hookMethod {
        before { param ->
            param.args[0] = CaptionViewportPatch.scaleFontSize(param.args[0] as Float)
        }
    }
}
