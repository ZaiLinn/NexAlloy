package io.github.nexalloy.morphe.youtube.misc.fix.pipchatbar

import android.app.Activity
import app.morphe.extension.youtube.patches.FixPipChatBarPatch
import io.github.nexalloy.patch

/**
 * Hides the bar that appears over the PiP video after using live chat text entry.
 *
 * Upstream inserts the extension call at the start of the picture-in-picture mode change method
 * of the main activity. The same is done here with a hook that runs before the method.
 */
internal val fixPipChatBarPatch = patch(
    description = "Hides the bar that appears over the PiP video after using live chat text entry."
) {
    PipModeChangedFingerprint.hookMethod {
        before {
            FixPipChatBarPatch.onPipModeChanged(
                it.thisObject as Activity,
                it.args[0] as Boolean
            )
        }
    }
}
