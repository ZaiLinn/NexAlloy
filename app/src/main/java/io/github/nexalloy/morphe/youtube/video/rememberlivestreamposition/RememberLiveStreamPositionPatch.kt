package io.github.nexalloy.morphe.youtube.video.rememberlivestreamposition

import app.morphe.extension.youtube.patches.playback.livestream.RememberLiveStreamPositionPatch
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.youtube.video.information.VideoInformationPatch
import io.github.nexalloy.morphe.youtube.video.information.onCreateHook
import io.github.nexalloy.morphe.youtube.video.information.videoTimeHook
import io.github.nexalloy.patch

val RememberLiveStreamPosition = patch(
    name = "Remember live stream playback position",
    description = "Adds an option to remember the playback position of an ongoing live stream " +
        "and resume from there when reopening that live stream.",
) {
    dependsOn(VideoInformationPatch)

    PreferenceScreen.VIDEO.addPreferences(
        // Keep the preferences organized together.
        noTitleUnsortedPreferenceCategory(
            SwitchPreference("morphe_remember_live_stream_position", summary = true)
        )
    )

    // Hook called when a new video starts playing (player controller created).
    onCreateHook { controller ->
        RememberLiveStreamPositionPatch.newVideoStarted(controller)
    }

    // Hook called approximately once per second with the current playback time.
    videoTimeHook { videoTime ->
        RememberLiveStreamPositionPatch.videoTimeChanged(videoTime)
    }
}
