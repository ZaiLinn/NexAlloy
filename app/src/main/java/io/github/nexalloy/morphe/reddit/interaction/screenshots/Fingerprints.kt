/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.nexalloy.morphe.reddit.interaction.screenshots

import io.github.nexalloy.RequireAppVersion
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.checkCast
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Opcode

private val SHOULD_SHOW_BANNER_FILTERS = listOf(
    fieldAccess(
        opcode = Opcode.IGET_OBJECT,
        definingClass = "this",
        name = $$"$shouldShowBanner$delegate"
    ),
    fieldAccess(
        opcode = Opcode.SGET_OBJECT,
        smali = "Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;",
        location = MatchAfterWithin(3)
    ),
    methodCall(
        opcode = Opcode.INVOKE_INTERFACE,
        name = "setValue",
        location = MatchAfterWithin(3)
    )
)

@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object RedditScreenshotTriggerSharingListenerFingerprint : Fingerprint(
    definingClass = $$"Lcom/reddit/sharing/screenshot/RedditScreenshotTriggerSharingListener$ScreenshotBanner$",
    name = "invokeSuspend",
    returnType = "Ljava/lang/Object;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Ljava/lang/Object;"),
    filters = SHOULD_SHOW_BANNER_FILTERS
)

@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object ScreenshotTakenBannerFingerprint : Fingerprint(
    definingClass = $$"Lcom/reddit/sharing/screenshot/composables/ScreenshotTakenBannerKt$ScreenshotTakenBanner$",
    name = "invokeSuspend",
    returnType = "Ljava/lang/Object;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Ljava/lang/Object;"),
    filters = SHOULD_SHOW_BANNER_FILTERS
)

/**
 * Opens the share sheet after a screenshot, if enabled by a feature flag. Since 2026.33.0.
 */
@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object ScreenshotShareSheetFingerprint : Fingerprint(
    definingClass = $$"Lcom/reddit/sharing/screenshot/RedditScreenshotTriggerSharingListener$openShareSheetOnScreenshot$1;",
    name = "invokeSuspend",
    returnType = "Ljava/lang/Object;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        checkCast("Ljava/lang/Boolean;"),
        methodCall(
            smali = "Ljava/lang/Boolean;->booleanValue()Z",
            location = MatchAfterImmediately()
        )
    )
)
