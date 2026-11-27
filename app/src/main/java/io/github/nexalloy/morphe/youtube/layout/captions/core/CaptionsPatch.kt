package io.github.nexalloy.morphe.youtube.layout.captions.core

import io.github.nexalloy.morphe.shared.misc.settings.preference.BasePreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.PreferenceCategory
import io.github.nexalloy.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.nexalloy.morphe.youtube.layout.captions.AutoCaptions
import io.github.nexalloy.morphe.youtube.layout.captions.language.AutoTranslateCaptions
import io.github.nexalloy.morphe.youtube.layout.captions.language.CaptionCookies
import io.github.nexalloy.morphe.youtube.layout.captions.language.CaptionsInPip
import io.github.nexalloy.morphe.youtube.layout.captions.language.CopyTranscript
import io.github.nexalloy.morphe.youtube.layout.captions.language.PreferredCaptionLanguage
import io.github.nexalloy.morphe.youtube.layout.captions.language.Transcript
import io.github.nexalloy.morphe.youtube.layout.captions.language.TranslatedCaptionPens
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch

/**
 * Caption settings. Used to organize all caption related settings together.
 *
 * The upstream patch shares this group between all caption patches. Ported caption patches
 * add their preferences to this group, and [Captions] moves them into the captions
 * settings screen.
 */
val settingsMenuCaptionGroup = mutableSetOf<BasePreference>()

/**
 * Runtime port of the upstream captionsPatch.
 *
 * Caption settings. Used to organize all caption related settings together.
 *
 * All ported caption patches add their preferences to [settingsMenuCaptionGroup],
 * and this patch moves them into the captions settings screen, so it runs after them.
 */
val Captions = patch(
    name = "Captions",
    description = "Adds options to disable captions from being automatically enabled, " +
            "select a preferred and a secondary caption language, add more auto-translate languages, " +
            "show captions in stable blocks and in picture-in-picture, and copy the transcript. " +
            "Stable blocks and bilingual captions work with YouTube 21.15 and newer, on Android 10 and newer.",
) {
    dependsOn(
        AutoCaptions,
        CaptionTrackCapture,
        CaptionWindowMeasure,
        StableCaptionBlocks,
        CaptionViewport,
        CaptionsAfterSeek,
        PreferredCaptionLanguage,
        AutoTranslateCaptions,
        TranslatedCaptionPens,
        CaptionCookies,
        CaptionsInPip,
        CopyTranscript,
        Transcript,
    )

    val remaining = settingsMenuCaptionGroup.associateByTo(mutableMapOf()) { it.key }
    fun take(vararg keys: String) = keys.mapNotNull { remaining.remove(it) }.toSet()

    val languages = take(
        "morphe_preferred_caption_language",
        "morphe_secondary_caption_language",
        "morphe_auto_translate_additional_languages",
    )
    val presentation = take(
        "morphe_caption_scale",
        "morphe_stable_caption_blocks",
        "morphe_auto_captions_style",
        "morphe_captions_in_pip",
    )
    val copyTranscript = take(
        "morphe_copy_transcript",
        "morphe_copy_transcript_timestamps",
        "morphe_copy_transcript_shorts_flyout",
    )

    PreferenceScreen.PLAYER.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_captions_screen",
            sorting = Sorting.UNSORTED,
            preferences = setOf(
                PreferenceCategory(
                    key = "morphe_captions_languages",
                    sorting = Sorting.UNSORTED,
                    preferences = languages
                ),
                PreferenceCategory(
                    key = "morphe_captions_presentation",
                    sorting = Sorting.UNSORTED,
                    preferences = presentation
                ),
                PreferenceCategory(
                    key = "morphe_copy_transcript_category",
                    sorting = Sorting.UNSORTED,
                    preferences = copyTranscript
                ),
                PreferenceScreenPreference(
                    key = "morphe_captions_advanced",
                    sorting = Sorting.UNSORTED,
                    preferences = remaining.values.toSet()
                ),
            )
        )
    )
}
