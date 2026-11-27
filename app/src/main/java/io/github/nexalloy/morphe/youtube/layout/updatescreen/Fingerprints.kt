package io.github.nexalloy.morphe.youtube.layout.updatescreen

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint

internal object AppBlockingCheckResultToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    strings = listOf("AppBlockingCheckResult{intent=")
)

internal object AppBlockingCheckResultConstructorFingerprint : Fingerprint(
    classFingerprint = AppBlockingCheckResultToStringFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    parameters = listOf("Landroid/content/Intent;", "Z")
)
