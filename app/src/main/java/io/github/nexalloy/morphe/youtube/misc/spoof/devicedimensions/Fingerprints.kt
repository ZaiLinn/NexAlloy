package io.github.nexalloy.morphe.youtube.misc.spoof.devicedimensions

import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.string

/**
 * The sizes the hardware video decoder supports. The stream selection drops the streams outside of them.
 */
internal object DeviceDimensionsModelToStringFingerprint : Fingerprint(
    returnType = "L",
    filters = listOf(
        string("minh."),
        string(";maxh."),
    ),
)

internal object DeviceDimensionsModelConstructorFingerprint : Fingerprint(
    classFingerprint = DeviceDimensionsModelToStringFingerprint,
    name = "<init>",
    returnType = "V",
    parameters = listOf(
        "I", // Min height.
        "I", // Max height.
        "I", // Min width.
        "I", // Max width.
    ),
)
