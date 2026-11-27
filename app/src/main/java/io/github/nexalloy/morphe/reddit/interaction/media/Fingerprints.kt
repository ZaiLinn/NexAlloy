/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.nexalloy.morphe.reddit.interaction.media

import io.github.nexalloy.RequireAppVersion
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.findMethodListDirect
import io.github.nexalloy.morphe.literal
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.newInstance
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.string
import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Opcode

/**
 * Composable of the media viewer (full bleed player) caption, user info and action bar.
 */
@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object MediaViewerChromeFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    filters = listOf(
        string("userInfoAndActionBarAlpha")
    )
)

/**
 * toString of the media viewer chrome state, which logs whether the overlay is visible.
 */
@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object FullBleedChromeStateToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string("FullBleedChromeState(userViewState="),
        string(", isVisible="),
        fieldAccess(
            opcode = Opcode.IGET_BOOLEAN,
            definingClass = "this",
            type = "Z",
            location = MatchAfterWithin(3)
        )
    )
)

/**
 * Creates the media viewer chrome state of a post, when it is shown in the media viewer.
 */
@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object CreateFullBleedChromeStateFingerprint : Fingerprint(
    returnType = { FullBleedChromeStateToStringFingerprint().declaredClass!!.descriptor },
    parameters = listOf("Lcom/reddit/domain/model/Link;", "L", "Ljava/lang/String;", "Ljava/lang/String;"),
    filters = listOf(
        newInstance(type = { FullBleedChromeStateToStringFingerprint().declaredClass!!.descriptor })
    )
)

/**
 * Page of the media viewer pager. Pages are padded for the navigation bar at the bottom,
 * and for the status bar at the top, except images and videos.
 */
@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object MediaViewerPageFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    filters = listOf(
        methodCall(
            definingClass = "Lcom/reddit/fullbleedplayer/FbpVideoControls;",
            name = "isSeekbarDocked"
        ),
        fieldAccess(
            definingClass = "Landroid/content/res/Configuration;",
            name = "orientation",
            location = MatchAfterWithin(6)
        ),
        // Navigation bar padding.
        methodCall(
            parameters = listOf("L"),
            returnType = "L",
            opcodes = listOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE)
        ),
        // Images and videos skip the status bar padding.
        opcode(Opcode.IF_NEZ, location = MatchAfterWithin(3)),
        // Status bar padding.
        methodCall(
            parameters = listOf("L"),
            returnType = "L",
            opcodes = listOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE),
            location = MatchAfterImmediately()
        )
    )
)

/**
 * Test tags of the media viewer screen and pagers, each drawn with the theme background color.
 * Video pages don't draw their own background, so the pager color shows around them.
 */
internal val MEDIA_VIEWER_BACKGROUND_TAGS = listOf(
    "fbp_screen",
    "fbp_screen_horizontal_pager",
    "fbp_horizontal_pager"
)

// Modifier.background(color, shape).
internal val mediaViewerBackgroundCallFilter = methodCall(
    parameters = listOf("L", "J", "L"),
    opcodes = listOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE)
)

/**
 * Bottom sheet menu host around the media viewer pager, with the theme background color.
 * It covers the page area between the system bars.
 */
@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object MediaViewerBottomSheetMenuFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/fullbleedplayer/ui/composables/a;",
    returnType = "V",
    filters = listOf(
        newInstance($$"Lcom/reddit/fullbleedplayer/ui/composables/BottomSheetMenuKt$BottomSheetMenu$2$1;"),
        mediaViewerBackgroundCallFilter
    )
)

/**
 * Top fade of a media viewer page, a gradient from the theme background color. Since 2026.26.0.
 */
@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object MediaViewerTopGradientFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/fullbleedplayer/ui/composables/a;",
    returnType = "V",
    filters = listOf(
        literal(80f),
        methodCall(
            definingClass = "Ljava/util/Arrays;",
            name = "asList"
        )
    )
)

/**
 * Video player shared by the feed and the media viewer.
 * Its background is black, or the theme background color with some experiments since 2026.20.0.
 */
@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object VideoPlayerBackgroundFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        fieldAccess(
            definingClass = "Lcom/reddit/features/VideoThumbnailFadeInVariant;",
            name = "CONTROL"
        ),
        methodCall(
            returnType = "Ljava/lang/Object;",
            parameters = listOf("L"),
            opcode = Opcode.INVOKE_VIRTUAL
        ),
        mediaViewerBackgroundCallFilter
    )
)

/**
 * Static initializer of the Android composition locals, which creates LocalContext.
 */
@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object LocalContextFingerprint : Fingerprint(
    definingClass = "Landroidx/compose/ui/platform/AndroidCompositionLocals_androidKt;",
    name = "<clinit>",
    filters = listOf(
        fieldAccess(
            opcode = Opcode.SGET_OBJECT,
            definingClass = $$"Landroidx/compose/ui/platform/AndroidCompositionLocals_androidKt$LocalContext$1;",
            name = "INSTANCE"
        ),
        // LocalContext field.
        fieldAccess(
            opcode = Opcode.SPUT_OBJECT,
            definingClass = "this",
            location = MatchAfterWithin(2)
        )
    )
)

// Color.copy(alpha) calls before 2026.45.0, with the alpha as the last parameter.
internal val mediaViewerFadeAlphaLegacyFilter = methodCall(
    parameters = listOf("J", "F"),
    returnType = "J",
    opcodes = listOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE)
)

/**
 * Lazy value of whether the media viewer chains posts vertically (swipe up for the next post),
 * instead of horizontally.
 */
@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object VerticalChainingFingerprint : Fingerprint(
    name = "invoke",
    returnType = "Ljava/lang/Object;",
    parameters = listOf(),
    filters = listOf(
        fieldAccess(
            definingClass = "Lcom/reddit/fullbleedplayer/FbpRedesignM3Variant;",
            name = "VERTICAL_CHAINING"
        ),
        methodCall(
            smali = "Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;",
            location = MatchAfterWithin(5)
        )
    )
)

private const val MEDIA_CONTEXT_CLASS = "Lcom/reddit/domain/model/media/MediaContext;"

private fun recommendedPostsRequestFilters(endpointClass: String) = listOf(
    methodCall(definingClass = MEDIA_CONTEXT_CLASS, name = "getSeedSubredditIds"),
    methodCall(definingClass = MEDIA_CONTEXT_CLASS, name = "getListingType"),
    fieldAccess(definingClass = endpointClass, name = "COMMUNITY"),
    methodCall(definingClass = "Lcom/reddit/domain/model/listing/Listing;", name = "<init>")
)

/**
 * RemoteGqlLinkDataSource.getRecommendedMedia, which loads the posts of the media viewer.
 */
@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object RecommendedMediaRequestFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    filters = recommendedPostsRequestFilters("Lcom/reddit/type/MediaFeedEndpoint;")
)

/**
 * RemoteGqlLinkDataSource.getRecommendedVideos, which loads the posts of the video viewer.
 */
@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object RecommendedVideosRequestFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    filters = recommendedPostsRequestFilters("Lcom/reddit/type/VideoFeedEndpoint;")
)

internal val mediaChromeVisibleField = findFieldDirect {
    FullBleedChromeStateToStringFingerprint.instructionMatches.last().instruction.fieldRef!!
}

internal val mediaViewerColorCopy = findMethodDirect {
    val method = MediaViewerChromeFingerprint()
    method.instructions.filter { mediaViewerFadeAlphaLegacyFilter.matches(method, it) }
        .mapNotNull { it.methodRef }
        .distinctBy { it.descriptor }
        .single()
}

context(_: org.luckypray.dexkit.DexKitBridge)
private fun mediaViewerBackgroundMatches() =
    MEDIA_VIEWER_BACKGROUND_TAGS.flatMap { tag ->
        Fingerprint(
            filters = listOf(mediaViewerBackgroundCallFilter, string(tag, location = MatchAfterWithin(30))),
        ).matchAllOrNull().orEmpty()
    }.also { check(it.isNotEmpty()) }

internal val mediaViewerBackgroundMethods = findMethodListDirect {
    mediaViewerBackgroundMatches().map { it.method }.distinctBy { it.descriptor }
}

internal val inlineMediaViewerBackgroundDiscriminator = findFieldDirect {
    val method = mediaViewerBackgroundMatches().map { it.method }.distinctBy { it.descriptor }
        .single { it.declaredClass!!.descriptor == "Lirh;" }
    val discriminator = method.instructions.first { it.opcode == Opcode.IGET.opCode }
    discriminator.fieldRef!!
}

internal val mediaViewerBackgroundCalls = findMethodListDirect {
    (mediaViewerBackgroundMatches().map { it.instructionMatches.first().instruction.methodRef!! } +
        MediaViewerBottomSheetMenuFingerprint.instructionMatches.last().instruction.methodRef!!)
        .distinctBy { it.descriptor }
}

internal val videoPlayerReadCompositionLocal = findMethodDirect {
    VideoPlayerBackgroundFingerprint.instructionMatches[1].instruction.methodRef!!
}

internal val androidLocalContext = findFieldDirect {
    LocalContextFingerprint.instructionMatches.last().instruction.fieldRef!!
}

internal val mediaViewerTopGradientColor = findMethodDirect {
    val method = MediaViewerTopGradientFingerprint()
    val index = method.instructions.indexOfFirst { it.opcode == Opcode.MOVE_RESULT_WIDE.opCode }
    check(index > 0)
    method.instructions[index - 1].methodRef!!
}

internal val mediaRequestListingConstructor = findMethodDirect {
    val media = RecommendedMediaRequestFingerprint.instructionMatches.last().instruction.methodRef!!
    val videos = RecommendedVideosRequestFingerprint.instructionMatches.last().instruction.methodRef!!
    check(media.descriptor == videos.descriptor)
    media
}

internal val videoPlayerBackgroundCall = findMethodDirect {
    VideoPlayerBackgroundFingerprint.instructionMatches.last().instruction.methodRef!!
}

@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object JoinConversationControlsFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/fullbleedplayer/ui/composables/a;",
    returnType = "V",
    strings = listOf("joinConversationScrubAlpha"),
)

@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object JoinConversationLayoutFingerprint : Fingerprint(
    name = "invoke",
    returnType = "Ljava/lang/Object;",
    filters = listOf(
        methodCall(
            smali = "Lt2e;->X(Lz3x;FFFFI)Lz3x;",
        ),
        methodCall(
            reference = { JoinConversationControlsFingerprint() },
        ),
    ),
)

internal val joinConversationBottomPadding = findMethodDirect {
    JoinConversationLayoutFingerprint.instructionMatches.first().instruction.methodRef!!
}

internal val mediaViewerStatusBarPadding = findMethodDirect {
    MediaViewerPageFingerprint.instructionMatches.last().instruction.methodRef!!
}

internal val verticalChainingDiscriminator = findFieldDirect {
    val method = VerticalChainingFingerprint()
    val first = method.instructions.first()
    check(first.opcode == Opcode.IGET.opCode)
    first.fieldRef!!
}
