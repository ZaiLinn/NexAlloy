/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/2582
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.nexalloy.morphe.youtube.layout.scrolling

import io.github.nexalloy.RequireAppVersion
import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.checkCast
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findFieldDirect

private const val SNAPPY_RECYCLER_VIEW =
    "Lcom/google/android/apps/youtube/app/common/rendering/SnappyRecyclerView;"

@RequireAppVersion(minVersion = "20.35.000")
@StrictQuery
internal object SnappyRecyclerViewSetFlingLimitFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    filters = listOf(
        checkCast(SNAPPY_RECYCLER_VIEW),
        fieldAccess(opcode = Opcode.IPUT_BOOLEAN, definingClass = SNAPPY_RECYCLER_VIEW, type = "Z"),
    ),
)

@get:RequireAppVersion(minVersion = "20.35.000")
internal val snappyRecyclerViewFlingLimitField = findFieldDirect {
    SnappyRecyclerViewSetFlingLimitFingerprint.instructionMatches.last().instruction.fieldRef!!
}
