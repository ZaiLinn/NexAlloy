package io.github.nexalloy.morphe.youtube.misc.fix.delayedping

import io.github.nexalloy.RequireAppVersion
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.checkCast
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.string

/**
 * Listener of a response, which a delayed ping gets with a null result.
 * 21.28 - 21.31 check that the result is not null.
 */
@RequireAppVersion(minVersion = "21.28.000", maxVersion = "21.31.000")
internal object DelayedPingResponseListenerFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        checkCast("Ljava/lang/Void;"),
        methodCall(
            definingClass = "Ljava/lang/Object;",
            name = "getClass",
            location = MatchAfterImmediately()
        ),
        string("crash_report_id")
    )
)
