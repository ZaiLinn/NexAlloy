package io.github.nexalloy.morphe.youtube.layout.captions.language

import app.morphe.extension.youtube.patches.PreferredCaptionLanguagePatch
import app.morphe.extension.youtube.settings.preference.CaptionLanguagePreference
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.morphe.youtube.shared.hookVideoStarted
import io.github.nexalloy.morphe.youtube.shared.startVideoInformerPatch
import io.github.nexalloy.morphe.youtube.video.information.VideoInformationPatch
import io.github.nexalloy.morphe.youtube.video.information.onCreateHook
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook
import io.github.nexalloy.morphe.shared.misc.settings.preference.ListPreference
import io.github.nexalloy.morphe.youtube.layout.captions.core.settingsMenuCaptionGroup

val PreferredCaptionLanguage = patch(
    name = "Preferred caption language",
    description = "Adds an option to automatically select captions in your preferred language " +
            "(provider subtitles first, then auto-translated).",
) {
    dependsOn(
        VersionCheck,
        startVideoInformerPatch,
        VideoInformationPatch,
    )

    settingsMenuCaptionGroup.addAll(listOf(
        ListPreference(
            key = "morphe_preferred_caption_language",
            tag = CaptionLanguagePreference::class.java,
            entriesKey = null,
            entryValuesKey = null,
        ))
    )

    // Resolve the bridge members shared by the caption language patches.
    CaptionBridge.apply {
        languageCodeField = ::languageCaptionBridgeCaptionTrackLanguageCodeField.field.accessible()
        vssIdField = ::languageCaptionBridgeCaptionTrackVssIdField.field.accessible()
        urlField = ::languageCaptionBridgeCaptionTrackUrlField.field.accessible()
        builderCopyConstructor = ::languageCaptionBridgeCaptionTrackBuilderCopyConstructor.constructor.accessible()
        builderBuildMethod = ::languageCaptionBridgeCaptionTrackBuilderBuildMethod.method.accessible()
        builderDefaultsMethod = ::captionTrackBuilderDefaultsMethod.method.accessible()
        setLanguageCodeMethod = ::setTrackLanguageCodeMethod.method.accessible()
        setVideoIdMethod = ::setTrackVideoIdMethod.method.accessible()
        setUrlMethod = ::setTrackUrlMethod.method.accessible()
        setVssIdMethod = ::setTrackVssIdMethod.method.accessible()
        builderLabelField = ::languageCaptionBridgeCaptionTrackBuilderLabelField.field.accessible()

        selectTrackMethod = ::subtitleManagerSelectTrackMethod.method.accessible()
        defaultSelectionReason = ::captionDefaultSelectionReasonField.field.accessible().get(null)
            ?: error("Missing default caption selection reason")

        directTracksMethod = ::tracksManagerDirectTracksMethod.method.accessible()
        autoTranslateTracksMethod = ::tracksManagerAutoTranslateTracksMethod.method.accessible()
        defaultSourceMethod = ::tracksManagerDefaultSourceMethod.method.accessible()
        metadataField = ::tracksManagerMetadataField.field.accessible()
        audioTrackField = ::tracksManagerAudioTrackField.field.accessible()
        sourceIndicesField = ::tracksManagerSourceIndicesField.field.accessible()
        audioCaptionIndicesField = ::tracksManagerAudioCaptionIndicesField.field.accessible()
        captionTracksField = ::tracksManagerCaptionTracksField.field.accessible()
        convertTrackMethod = ::tracksManagerConvertTrackMethod.method.accessible()
        optionVideoIdField = ::tracksManagerVideoIdField.field.accessible()
        optionLabelField = ::tracksManagerOptionLabelField.field.accessible()
    }

    // Resolved outside the bridge scope, because the bridge members shadow
    // the equally named resolved members.
    CaptionBridge.subtitleManagerTracksField = ::subtitleManagerTracksField.field.accessible()
    CaptionBridge.requestClass = ::captionRequestTrackField.declaredClass
    CaptionBridge.requestTrackField = ::captionRequestTrackField.field.accessible()

    // The default caption track is chosen when the caption tracks of a video are loaded.
    // Hook the call site and not the default track method itself,
    // so the subtitle manager instance is available.
    PreferredCaptionsTrackFingerprint.hookMethod(scopedHook(::subtitleManagerDefaultTrackMethod.method) {
        after {
            if (innerDepth != 0) return@after

            val preferred = PreferredCaptionLanguagePatch.getPreferredCaptionTrack(
                wrapSubtitleManager(it.thisObject),
                wrapTrack(it.result),
            )
            it.result = unwrapTrack(preferred)
        }
    })

    // Native track selection, used to select the preferred track and to keep
    // the selection of the bilingual captions toggle.
    SetSubtitleTrackFingerprint.hookMethod {
        before {
            val selected = PreferredCaptionLanguagePatch.onSetSubtitleTrack(
                wrapSubtitleManager(it.thisObject),
                wrapTrack(it.args[0]),
                it.args[1] as? Enum<*>,
            )
            it.args[0] = unwrapTrack(selected)
        }
    }

    onCreateHook(PreferredCaptionLanguagePatch::newVideoStarted)

    hookVideoStarted(PreferredCaptionLanguagePatch::videoInformationLoaded)
}
