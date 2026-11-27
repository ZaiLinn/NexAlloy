/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3870
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.nexalloy.morphe.reddit.misc.downloadlocation

import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.string
import io.github.nexalloy.morphe.Opcode

private const val CONTENT_RESOLVER_CLASS = "Landroid/content/ContentResolver;"

/**
 * Download model of a post, which is obfuscated in some app versions.
 */
@StrictQuery
internal object LinkDownloadModelToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string(", author="),
        string("LinkDownloadModel(subreddit="),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/String;",
            location = MatchAfterImmediately()
        ),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/String;",
            location = MatchAfterImmediately()
        )
    )
)

private object ImageDownloadParamsToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    strings = listOf(", isGif=", ", linkModel=")
)

/**
 * Sets the image url, which is the only string field, and the download model of the post.
 */
@StrictQuery
internal object ImageDownloadParamsConstructorFingerprint : Fingerprint(
    classFingerprint = ImageDownloadParamsToStringFingerprint,
    name = "<init>",
    returnType = "V",
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/String;"
        ),
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = { LinkDownloadModelToStringFingerprint().declaredClass!!.descriptor }
        )
    )
)

@StrictQuery
internal object DownloadVideoFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_DIRECT,
            definingClass = $$"Landroid/app/DownloadManager$Request;",
            name = "<init>"
        ),
        methodCall(
            definingClass = $$"Landroid/app/DownloadManager$Request;",
            name = "setDestinationInExternalPublicDir"
        ),
        string("DownloadManager_not_available"),
        methodCall(
            definingClass = "Landroid/app/DownloadManager;",
            name = "enqueue"
        )
    )
)

/**
 * Downloads and saves an image. The first read of the parameters is at the start of the download.
 */
internal object DownloadImageFingerprint : Fingerprint(
    definingClass = $$"Lcom/reddit/domain/media/usecase/RedditDownloadMediaUseCase$downloadMediaFile$2;",
    name = "invokeSuspend",
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            name = $$"$params"
        )
    )
)

@StrictQuery
internal object SaveImageFingerprint : Fingerprint(
    returnType = "Landroid/net/Uri;",
    parameters = listOf("Landroid/net/Uri;", "Ljava/io/File;", "Ljava/lang/String;"),
    filters = listOf(
        string("relative_path"),
        string("datetaken"),
        methodCall(
            definingClass = CONTENT_RESOLVER_CLASS,
            name = "insert"
        )
    )
)

@StrictQuery
internal object SaveToGalleryFingerprint : Fingerprint(
    name = "invokeSuspend",
    filters = listOf(
        string("reddit_image_"),
        string("datetaken"),
        methodCall(
            definingClass = CONTENT_RESOLVER_CLASS,
            name = "insert"
        )
    )
)


internal object DownloadLinkConstructorFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/domain/model/Link;",
    name = "<init>",
    returnType = "V",
    filters = listOf(fieldAccess(opcode = Opcode.IPUT_OBJECT, name = "url", type = "Ljava/lang/String;")),
)

internal val imageDownloadParamsField = findFieldDirect {
    checkNotNull(DownloadImageFingerprint.instructionMatches.first().instruction.fieldRef)
}

internal val imageDownloadUrlField = findFieldDirect {
    checkNotNull(ImageDownloadParamsConstructorFingerprint.instructionMatches[0].instruction.fieldRef)
}

internal val imageDownloadModelField = findFieldDirect {
    checkNotNull(ImageDownloadParamsConstructorFingerprint.instructionMatches[1].instruction.fieldRef)
}

internal val downloadModelSubreddit = findFieldDirect {
    checkNotNull(LinkDownloadModelToStringFingerprint.instructionMatches[2].instruction.fieldRef)
}

internal val downloadModelAuthor = findFieldDirect {
    checkNotNull(LinkDownloadModelToStringFingerprint.instructionMatches[3].instruction.fieldRef)
}
