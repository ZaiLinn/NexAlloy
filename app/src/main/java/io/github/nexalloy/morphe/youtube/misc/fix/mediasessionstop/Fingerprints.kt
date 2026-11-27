package io.github.nexalloy.morphe.youtube.misc.fix.mediasessionstop

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.StringComparisonType
import io.github.nexalloy.morphe.findFieldListDirect
import io.github.nexalloy.morphe.findMethodListDirect
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.string
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData

internal const val MEDIA_SESSION_OWNER_FEATURE_FLAG = 45857115L

/**
 * Starts the media session shared by all players.
 */
internal object MediaSessionStartFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(),
    strings = listOf("MediaSession setActive(true)")
)

/**
 * Stops the media session shared by all players (the parameter is true if the playback is destroyed).
 */
internal object MediaSessionStopFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Z"),
    strings = listOf("MediaSession setActive(false)")
)

/**
 * Stops the background playback: removes the media notification and stops the background player service.
 */
internal object BackgroundPlaybackStopFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(),
    strings = listOf(
        // 21.04+ starts with "%s: ".
        string(
            "About to stop background service while in a pending state.",
            StringComparisonType.ENDS_WITH
        )
    )
)

/**
 * Every method that calls [reference]. Use with [Fingerprint.matchAllOrNull].
 */
internal fun methodCallFingerprint(reference: DexKitBridge.() -> MethodData) = Fingerprint(
    filters = listOf(methodCall(reference = reference))
)

internal val sessionStartCallers = findMethodListDirect {
    methodCallFingerprint { MediaSessionStartFingerprint() }
        .matchAllOrNull()?.map { it.method } ?: emptyList()
}

internal val sessionStopCallers = findMethodListDirect {
    methodCallFingerprint { MediaSessionStopFingerprint() }
        .matchAllOrNull()?.map { it.method } ?: emptyList()
}

internal val backgroundPlaybackStopCallers = findMethodListDirect {
    methodCallFingerprint { BackgroundPlaybackStopFingerprint() }
        .matchAllOrNull()?.map { it.method } ?: emptyList()
}

/**
 * The fields of the player classes that hold the shared media session. Upstream reads the
 * player from these fields right before the session or background playback methods are called.
 * The fields are declared by the classes of the call sites, and a candidate object is the
 * player if it holds the called session or background playback object in one of these fields.
 */
internal val mediaSessionPlayerFields = findFieldListDirect {
    val sessionClass = MediaSessionStartFingerprint().declaredClass!!

    (sessionStartCallers() + sessionStopCallers() + backgroundPlaybackStopCallers())
        .mapNotNull { it.declaredClass }
        .distinct()
        .flatMap { it.fields }
        .filter { it.type.descriptor == sessionClass.descriptor }
}
