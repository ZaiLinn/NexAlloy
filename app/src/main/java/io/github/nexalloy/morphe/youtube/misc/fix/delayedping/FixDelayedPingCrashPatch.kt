package io.github.nexalloy.morphe.youtube.misc.fix.delayedping

import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.morphe.youtube.misc.playservice.is_21_28_or_greater
import io.github.nexalloy.morphe.youtube.misc.playservice.is_21_32_or_greater
import io.github.nexalloy.patch

internal val fixDelayedPingCrashPatch = patch(
    description = "Fixes a crash when a delayed ping is sent, such as when opening a Short."
) {
    dependsOn(VersionCheck)

    if (!is_21_28_or_greater || is_21_32_or_greater) return@patch

    // A delayed ping always gives its listener a null result, but 21.28 - 21.31 check that
    // the result is not null, so the app crashes when the ping is sent (#5032).
    // Upstream removes the check, as 21.32 and newer do, and the listener then passes the
    // result on as 21.32 does. Removing a single instruction is not possible at runtime,
    // so the listener is skipped entirely for a null result instead, which prevents the
    // same crash.
    DelayedPingResponseListenerFingerprint.hookMethod {
        before { if (it.args[0] == null) it.result = null }
    }
}
