package io.github.nexalloy.morphe.music.video.audio

import io.github.nexalloy.morphe.music.misc.playservice.is_9_26_or_greater
import io.github.nexalloy.morphe.music.misc.playservice.versionCheckPatch
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.music.shared.MusicActivityOnCreateFingerprint
import io.github.nexalloy.morphe.shared.misc.audio.tracks.forceOriginalAudioPatch
import io.github.nexalloy.patch

val ForceOriginalAudio = forceOriginalAudioPatch(
    block = {
        dependsOn(versionCheckPatch)
    },
    // The localized audio track flag was removed in 9.26+ (upstream music).
    fixUseLocalizedAudioTrackFlag = { !is_9_26_or_greater },
    forcedServerAdaptiveStreaming = { is_9_26_or_greater },
    mainActivityOnCreateFingerprint = MusicActivityOnCreateFingerprint,
    subclassExtensionClassDescriptor = "Lapp/morphe/extension/music/patches/ForceOriginalAudioPatch;",
    // Upstream puts the preference on the VIDEO screen, which the music settings of this
    // module do not have; the player screen is the closest match.
    preferenceScreen = PreferenceScreen.PLAYER,
)
