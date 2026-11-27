package io.github.nexalloy.morphe.music.misc.quic

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint

/**
 * The Cronet engine builder setter of the QUIC protocol.
 * org.chromium.net.CronetEngine$Builder.enableQuic(Z), present in YT Music 9.46.51.
 */
internal object CronetEngineBuilderFingerprint : Fingerprint(
    definingClass = "/CronetEngine\$Builder;",
    name = "enableQuic",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "L",
    parameters = listOf("Z"),
)

/**
 * The experimental Cronet engine builder setter of the QUIC protocol.
 * org.chromium.net.ExperimentalCronetEngine$Builder.enableQuic(Z), present in YT Music 9.46.51.
 */
internal object ExperimentalCronetEngineBuilderFingerprint : Fingerprint(
    definingClass = "/ExperimentalCronetEngine\$Builder;",
    name = "enableQuic",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "L",
    parameters = listOf("Z"),
)
