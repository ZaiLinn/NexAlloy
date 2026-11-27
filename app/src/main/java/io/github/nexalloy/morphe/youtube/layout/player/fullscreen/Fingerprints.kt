/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/2616
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Runtime port of the Morphe patcher fingerprints of the same names
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/youtube/layout/player/fullscreen/Fingerprints.kt).
 * The PlayerViewConstructorFingerprint is not ported: the patcher patch adds an interface to
 * the host PlayerView class so the extension can read its video view field, which a runtime
 * hook cannot do. The extension falls back to the video aspect ratio recorded from the
 * adaptive formats instead.
 */

package io.github.nexalloy.morphe.youtube.layout.player.fullscreen

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.findMethodListDirect
import io.github.nexalloy.morphe.opcode

@StrictQuery
internal object YouTubePlayerOverlaysLayoutConstructorFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/apps/youtube/app/common/player/overlay/YouTubePlayerOverlaysLayout;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
    parameters = listOf("Landroid/content/Context;", "Landroid/util/AttributeSet;"),
    filters = listOf(
        opcode(Opcode.RETURN_VOID)
    )
)

@StrictQuery
internal object YouTubePlayerViewOnLayoutFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/apps/youtube/app/player/YouTubePlayerViewNotForReflection;",
    name = "onLayout",
    accessFlags = listOf(AccessFlags.PROTECTED, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Z", "I", "I", "I", "I"),
    filters = listOf(
        opcode(Opcode.RETURN_VOID)
    )
)

/**
 * The constructors of the YouTubePlayerOverlaysLayout, which hold the player overlays.
 */
internal val youTubePlayerOverlaysLayoutConstructors = findMethodListDirect {
    YouTubePlayerOverlaysLayoutConstructorFingerprint.matchAll().map { it.method }
}
