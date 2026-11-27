package io.github.nexalloy.morphe.reddit.misc.debugging

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.methodCall

internal object ExperimentVariantFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Ljava/lang/String;", "Z"),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/reddit/experiments/",
            returnType = "V",
            parameters = listOf("Ljava/lang/String;"),
        ),
        methodCall(smali = "Lcom/reddit/common/experiments/ExperimentVariant;->getName()Ljava/lang/String;"),
    ),
)
