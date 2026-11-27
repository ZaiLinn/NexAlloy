/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.nexalloy.morphe.reddit.misc.flag

import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.string
import io.github.nexalloy.morphe.AccessFlags

internal object RedditFeatureFlagReader : Fingerprint(
    classFingerprint = Fingerprint(
        returnType = "V",
        accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
        parameters = listOf("Ljava/lang/String;"),
        filters = listOf(
            string("experiment_name"),
            string("max_length")
        )
    ),
    returnType = "Z",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Ljava/lang/String;", "Z")
)
