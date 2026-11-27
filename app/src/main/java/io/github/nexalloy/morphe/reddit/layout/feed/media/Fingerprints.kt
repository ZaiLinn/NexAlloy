/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.nexalloy.morphe.reddit.layout.feed.media

import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findFieldListDirect
import io.github.nexalloy.morphe.findClassDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.findMethodListDirect
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.InstructionLocation.MatchFirst
import io.github.nexalloy.morphe.StringComparisonType
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.literal
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.string
import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Opcode

/**
 * toString of a feed post style, which logs whether media is inset.
 */
internal object FeedPostStyleToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string(", mediaInsetEnabled="),
        fieldAccess(
            opcode = Opcode.IGET_BOOLEAN,
            definingClass = "this",
            type = "Z",
            location = MatchAfterWithin(3)
        )
    )
)

/**
 * Getter of the feed post style 'mediaInsetEnabled' field.
 */
internal object MediaInsetGetterFingerprint : Fingerprint(
    classFingerprint = FeedPostStyleToStringFingerprint,
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(
        fieldAccess(
            reference = { FeedPostStyleToStringFingerprint.instructionMatches.last().instruction.fieldRef!! },
            opcode = Opcode.IGET_BOOLEAN,
            location = MatchFirst()
        )
    )
)

/**
 * The 'mediaInsetEnabled' getter of every feed post style (regular, crosspost, ...),
 * which all extend the same base class.
 */
internal fun feedPostStyleMediaInsetFingerprint(baseClassType: String, getterName: String) = Fingerprint(
    superclass = baseClassType,
    name = getterName,
    returnType = "Z",
    parameters = listOf()
)

/**
 * Converts the 16dp side padding of inset media (32dp in total) to pixels.
 */
private fun mediaInsetFilters() = listOf(
    literal(32.0f),
    methodCall(
        parameters = listOf("F"),
        returnType = "I",
        location = MatchAfterWithin(3)
    )
)

/**
 * Caps the height of a feed image to 4:3 of the inset media width.
 */
internal object FeedImageMaxHeightFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "I",
    parameters = listOf("I", "L", "L"),
    filters = mediaInsetFilters() + methodCall(smali = "Ljava/lang/Integer;->min(II)I")
)

/**
 * Height of a feed video, from its aspect ratio and the inset media width.
 */
internal object FeedVideoHeightFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "F",
    parameters = listOf("L", "L", "L"),
    filters = mediaInsetFilters() + methodCall(smali = "Ljava/lang/Integer;->min(II)I")
)

/**
 * Height of a feed gallery, from the size of its images and the inset media width.
 */
internal object FeedGalleryHeightFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "F",
    parameters = listOf("Z", "I", "L", "L", "L"),
    filters = mediaInsetFilters()
)

/**
 * Size of a feed image, from its size, the screen size and whether the media is inset.
 * Without a maximum aspect ratio, the height is capped to 4:3 of the inset media width.
 */
internal object FeedImageSizeFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf("I", "I", "I", "I", "Z", "Ljava/lang/Float;", "Z", "L"),
    filters = mediaInsetFilters() + literal(4f / 3f)
)

/**
 * toString of the image component props, used in the post details,
 * which logs whether the image is inset.
 */
internal object PostImagePropsToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string("PostImageComponentProps(", StringComparisonType.STARTS_WITH),
        string(", applyInset="),
        fieldAccess(
            opcode = Opcode.IGET_BOOLEAN,
            definingClass = "this",
            type = "Z",
            location = MatchAfterWithin(3)
        )
    )
)

/**
 * Constructor of the image component props, which sets whether the image is inset.
 */
@StrictQuery
internal object PostImagePropsConstructorFingerprint : Fingerprint(
    classFingerprint = PostImagePropsToStringFingerprint,
    name = "<init>",
    filters = listOf(
        fieldAccess(
            reference = { PostImagePropsToStringFingerprint.instructionMatches.last().instruction.fieldRef!! },
            opcode = Opcode.IPUT_BOOLEAN
        )
    )
)

/**
 * toString of the post details content props, which logs the post content first.
 */
internal object PostContentPropsToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string("PostUnitContentProps(data="),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            location = MatchAfterWithin(3)
        )
    )
)

/**
 * toString of the GIF or video post content.
 */
internal object GifAndVideoContentToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string("GifAndVideo(", StringComparisonType.STARTS_WITH)
    )
)

/**
 * Lambda showing the content of a post in the post details, before 2026.46.0.
 */
@StrictQuery
internal object PostContentLambdaConstructorLegacyFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    parameters = {
        listOf(
            PostContentPropsToStringFingerprint().declaredClass!!.descriptor,
            "L",
            "L",
            "L",
            "Z",
            "L",
            "L",
            "L",
            "L",
            "Z",
            "Ljava/lang/Float;",
            "Z",
            "Z"
        )
    }
)

/**
 * GIF or video in the post details: (videoInfo, autoplay, width, height, ...).
 * The width and height are the display size, for the screen width minus the side padding.
 * Its 15th parameter sizes the video to that width and height.
 */
@StrictQuery
internal object PostDetailVideoFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    filters = listOf(
        string("media_content"),
        fieldAccess(
            definingClass = "Landroidx/compose/foundation/layout/IntrinsicSize;",
            name = "Min"
        )
    )
)

/**
 * Leading parameters of the gallery in the post details.
 * Its 14th parameter insets the gallery with side padding and rounded corners.
 */
private val POST_DETAIL_GALLERY_PARAMETERS = listOf(
    "L",
    "Z",
    "Lkotlin/jvm/functions/Function1;",
    "Ljava/lang/String;",
    "L",
    "I",
    "Z",
    "Z",
    "Z",
    "Z",
    "Z",
    "Z",
    "Lkotlin/jvm/functions/Function0;",
    "Z",
    "Ljava/lang/Float;"
)

/**
 * Gallery in the post details, 2026.36.0 to 2026.42.0.
 */
internal object PostDetailGalleryLegacyFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = POST_DETAIL_GALLERY_PARAMETERS + listOf("L", "I", "I"),
    filters = listOf(
        string("post_media_gallery_content")
    )
)

/**
 * toString of the YouTube video section of a feed post, which logs whether the video is inset.
 */
internal object YouTubeVideoSectionToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string("YoutubeVideoSection(width="),
        string(", applyInset="),
        fieldAccess(
            opcode = Opcode.IGET_BOOLEAN,
            definingClass = "this",
            type = "Z",
            location = MatchAfterWithin(3)
        )
    )
)

/**
 * Constructor of the YouTube video section, which sets whether the video is inset.
 */
internal object YouTubeVideoSectionConstructorFingerprint : Fingerprint(
    classFingerprint = YouTubeVideoSectionToStringFingerprint,
    name = "<init>",
    filters = listOf(
        fieldAccess(
            reference = { YouTubeVideoSectionToStringFingerprint.instructionMatches.last().instruction.fieldRef!! },
            opcode = Opcode.IPUT_BOOLEAN
        )
    )
)

/**
 * YouTube video in the feed. It is always padded by 16dp at the sides and clipped
 * with rounded corners and an outline. Its height is computed from the screen width
 * minus that padding (32dp in total).
 */
internal object YouTubeVideoSectionContentFingerprint : Fingerprint(
    classFingerprint = YouTubeVideoSectionToStringFingerprint,
    returnType = "V",
    filters = listOf(
        literal(16.0f),
        // Horizontal padding.
        methodCall(
            parameters = listOf("L", "F", "F", "I"),
            returnType = "L",
            location = MatchAfterWithin(3)
        ),
        // Rounded corners and outline.
        methodCall(
            parameters = listOf("L"),
            returnType = "L",
            location = MatchAfterWithin(2)
        )
    ) + mediaInsetFilters()
)

/**
 * Horizontal padding of the post details content, in dp.
 */
internal fun postDetailHorizontalPaddingFilter() = methodCall(
    // '-D9Ej5fM' suffix is not obfuscation.
    // Kotlin adds it to functions returning Dp (an inline value class).
    smali = "Lcom/reddit/postdetail/PostDetailStyle\$HorizontalPadding;->getSize-D9Ej5fM()F"
)

/**
 * Embedded video (YouTube, ...) in the post details: (hub, videoMedia, previewImage, modifier, ...).
 * Its size is computed for the screen width minus the horizontal padding, it is padded by it,
 * and it is clipped with 8dp rounded corners and an outline.
 */
internal object PostDetailEmbedVideoFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(
        "L",
        "Lcom/reddit/domain/model/VideoMedia;",
        "Lcom/reddit/domain/model/Image;",
        "L",
        "L",
        "I"
    ),
    filters = listOf(
        postDetailHorizontalPaddingFilter(),
        literal(8.0f),
        // Rounded corners and outline.
        methodCall(
            parameters = listOf("L", "Lkotlin/jvm/functions/Function1;", "L"),
            returnType = "L"
        )
    )
)

/**
 * Height of a gallery, from the size of its images and the media width.
 * Capped to 4:3 of the media width.
 */
internal object GalleryHeightFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "I",
    parameters = listOf("I", "Ljava/util/List;"),
    filters = listOf(
        literal(4f / 3f)
    )
)

/**
 * Height of a feed gallery for a given media width.
 * Without image sizes, capped to 4:3 of the media width.
 */
internal object FeedGalleryHeightForWidthFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "F",
    parameters = listOf("Z", "I", "L", "I", "L"),
    filters = listOf(
        methodCall(smali = "Ljava/lang/Math;->min(II)I")
    )
)

/**
 * Feed video composable. With a maximum aspect ratio set, caps the height to 4:3 of the media width.
 */
internal object FeedVideoFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        string("_media_video"),
        literal(4f / 3f)
    )
)

internal val mediaInsetGetterMethods = findMethodListDirect {
    val style = FeedPostStyleToStringFingerprint().declaredClass!!
    feedPostStyleMediaInsetFingerprint(style.superClass!!.descriptor, MediaInsetGetterFingerprint().name)
        .matchAll().map { it.method }
}

internal val mediaDensityRound = findMethodDirect {
    FeedImageMaxHeightFingerprint.instructionMatches[1].instruction.methodRef!!
}

internal val postDetailHorizontalPaddingMethod = findMethodDirect {
    PostDetailEmbedVideoFingerprint.instructionMatches.first().instruction.methodRef!!
}

internal val postDetailEmbeddedShapeMethod = findMethodDirect {
    PostDetailEmbedVideoFingerprint.instructionMatches.last().instruction.methodRef!!
}

internal val postContentField = findFieldDirect {
    PostContentPropsToStringFingerprint.instructionMatches.last().instruction.fieldRef!!
}

internal val gifAndVideoContent = findClassDirect {
    GifAndVideoContentToStringFingerprint().declaredClass!!
}

internal val feedYouTubePaddingMethod = findMethodDirect {
    YouTubeVideoSectionContentFingerprint.instructionMatches[1].instruction.methodRef!!
}

internal val feedYouTubeShapeMethod = findMethodDirect {
    YouTubeVideoSectionContentFingerprint.instructionMatches[2].instruction.methodRef!!
}

internal val insetMediaViewportWidth = findFieldDirect {
    val match = FeedImageMaxHeightFingerprint.match()
    match.method.instructions.toList()[match.instructionMatches.first().index - 1].fieldRef!!
}

internal val imageHeightCoercionMethod = findMethodDirect {
    FeedImageSizeFingerprint().instructions.single {
        it.methodRef?.paramTypeNames == listOf("double", "double", "double")
    }.methodRef!!
}

internal val galleryImageSizeFields = findFieldListDirect {
    GalleryHeightFingerprint().instructions.mapNotNull { it.fieldRef }
        .filter { it.typeSign == "I" }.distinct()
}

internal val feedVideoAspectMethods = findMethodListDirect {
    val method = FeedVideoHeightFingerprint()
    val calls = method.instructions.mapNotNull { it.methodRef }
    val videoProps = calls.first { it.declaredClass!!.descriptor == method.paramTypes.first().descriptor }
    val dimensions = calls.filter { it.returnTypeName == "int" && it.paramTypeNames.isEmpty() }
    val toDp = calls.single {
        it.paramTypeNames == listOf("int") && it.returnTypeName == "float"
    }
    listOf(videoProps, dimensions[0], dimensions[1], toDp)
}

internal val feedVideoViewportMethods = findMethodListDirect {
    val instructions = FeedVideoFingerprint().instructions.toList()
    val cap = instructions.indexOfFirst { it.literal == (4f / 3f).toRawBits().toLong() }
    val factory = instructions.take(cap).last {
        it.methodRef?.returnTypeName == FeedVideoHeightFingerprint().paramTypeNames.first()
    }.methodRef!!
    val viewport = instructions.drop(cap).first {
        it.methodRef?.paramTypeNames?.take(3) == listOf("float", "float", "boolean")
    }.methodRef!!
    listOf(factory, viewport)
}
