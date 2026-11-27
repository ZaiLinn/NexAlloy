package io.github.nexalloy.morphe.youtube.layout.captions.language

import app.morphe.extension.youtube.patches.AutoTranslateCaptionsPatch
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook
import io.github.nexalloy.morphe.youtube.layout.captions.core.settingsMenuCaptionGroup

val AutoTranslateCaptions = patch(
    name = "Additional auto-translate languages",
    description = "Adds an option to add more auto-translate languages and sort the languages by name.",
) {
    dependsOn(PreferredCaptionLanguage)

    settingsMenuCaptionGroup.addAll(listOf(
        SwitchPreference("morphe_auto_translate_additional_languages", summary = true),
    ))

    // Add the missing translation languages to the auto-translated tracks.
    LanguageCaptionTracksManagerAutoTranslateTracksFingerprint.hookMethod {
        after {
            val tracks = wrapAutoTranslateTracks(it.result) ?: return@after

            it.result = AutoTranslateCaptionsPatch.addTranslationLanguages(tracks)
                .mapNotNull(::unwrapTrack)
        }
    }

    // The subtitle manager builds the auto-translate menu rows with the getter results.
    // Keep the original getter intact, the adapter calls it once and cannot call itself.
    val translationAdapterHook = scopedHook(::tracksManagerAutoTranslateTracksMethod.method) {
        after {
            if (innerDepth != 0) return@after
            val tracksManager = it.thisObject ?: return@after
            val tracks = wrapAutoTranslateTracks(it.result) ?: return@after

            val restored = AutoTranslateCaptionsPatch.restoreTranslationTracks(
                tracks,
                wrapTrackList(CaptionBridge.directTracks(tracksManager)),
                wrapTrack(CaptionBridge.translationSource(tracksManager)),
            )
            it.result = restored.mapNotNull(::unwrapTrack)
        }
    }
    ::autoTranslateTracksCallers.dexMethodList.forEach { caller ->
        caller.hookMethod(translationAdapterHook)
    }

    // Reuse YouTube's localized option row and preserve its remaining native checks,
    // even when the manager offers no auto-translations of its own.
    AutoTranslateRowCountFingerprint.hookMethod {
        after {
            val tracks = it.result as? List<*> ?: return@after
            val tracksManager = it.thisObject ?: return@after

            // Native check passed, the option row already exists.
            if (tracks.any { track ->
                    track != null && runCatching {
                        CaptionBridge.languageCodeField.get(track) == AUTO_TRANSLATE_CAPTIONS_OPTION
                    }.getOrDefault(false)
                }
            ) return@after

            val nativeCount = 0
            if (AutoTranslateCaptionsPatch.getTranslationCount(
                    nativeCount,
                    wrapTrackList(tracks),
                    wrapTrack(CaptionBridge.translationSource(tracksManager)),
                ) != 1
            ) return@after

            val optionRow = CaptionBridge.buildAutoTranslateOptionRow(tracksManager) ?: return@after
            it.result = ArrayList(tracks).apply { add(optionRow) }
        }
    }
}
