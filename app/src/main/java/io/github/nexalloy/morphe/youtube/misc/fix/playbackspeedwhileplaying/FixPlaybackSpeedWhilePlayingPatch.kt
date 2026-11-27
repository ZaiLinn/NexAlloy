package io.github.nexalloy.morphe.youtube.misc.fix.playbackspeedwhileplaying

import app.morphe.extension.youtube.patches.FixPlaybackSpeedWhilePlayingPatch
import io.github.nexalloy.morphe.youtube.misc.playertype.PlayerTypeHook
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.patch

/**
 * Fixes a bug in YouTube 19.34+ where the playback speed
 * can incorrectly reset to 1.0x under certain conditions.
 *
 * Upstream inserts the extension call right after the method reads the playback speed field,
 * and jumps to the return when the extension blocks the change. The same is done here before
 * the method runs: the field is read, and the method is skipped when the change is blocked.
 */
internal val fixPlaybackSpeedWhilePlayingPatch = patch(
    description = "Fixes a bug where the playback speed can incorrectly reset to 1.0x under certain conditions."
) {
    dependsOn(VersionCheck, PlayerTypeHook)

    val speedField = ::playbackSpeedField.field

    PlaybackSpeedInFeedsFingerprint.hookMethod {
        before { param ->
            // The receiver of the field read is not known at runtime; try the method
            // receiver and its arguments. Fail closed and do nothing if it cannot be read.
            val playbackSpeed = (listOf(param.thisObject) + param.args).asSequence()
                .filterNotNull()
                .mapNotNull { runCatching { speedField.get(it) as? Float }.getOrNull() }
                .firstOrNull() ?: return@before

            if (FixPlaybackSpeedWhilePlayingPatch.playbackSpeedChanged(playbackSpeed)) {
                param.result = null
            }
        }
    }
}
