/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/5082
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Runtime port of the upstream patcher fingerprints
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/music/layout/applemusicartwork/Fingerprints.kt).
 */

package io.github.nexalloy.morphe.music.layout.applemusicartwork

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.ResourceType
import io.github.nexalloy.morphe.checkCast
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.resourceLiteral

private const val AUTO_CROP_IMAGE_VIEW_CLASS_TYPE =
    "Lcom/google/android/apps/youtube/music/ui/image/AutoCropImageView;"

/**
 * Constructor of the player controls overlay, which stores the player artwork view.
 */
internal object PlayerThumbnailFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    filters = listOf(
        resourceLiteral(ResourceType.ID, "player_thumbnail"),
        checkCast(AUTO_CROP_IMAGE_VIEW_CLASS_TYPE),
        fieldAccess(opcode = Opcode.IPUT_OBJECT, type = AUTO_CROP_IMAGE_VIEW_CLASS_TYPE)
    )
)

/**
 * The field the constructor stores the player artwork view in. The upstream patch passes the
 * value of the store instruction to the extension; the runtime port reads the same field.
 */
internal val playerThumbnailField = findFieldDirect {
    PlayerThumbnailFingerprint.instructionMatches.last().instruction.fieldRef!!
}
