/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3714
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.nexalloy.morphe.reddit.interaction.redgifs

import io.github.nexalloy.RequireAppVersion
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.string
import io.github.nexalloy.morphe.Opcode

internal const val DOMAIN_VIDEO_URLS_CLASS = "Lcom/reddit/domain/model/VideoUrls;"

/**
 * Feed post header.
 */
@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object MetadataCellToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        // Media path, which is nullable.
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/String;"
        ),
        opcode(Opcode.IF_NEZ, location = MatchAfterImmediately()),
        string("MetadataCellFragment(id="),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/String;",
            location = MatchAfterWithin(2)
        ),
        string(", mediaPath=")
    )
)

@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object MetadataCellConstructorFingerprint : Fingerprint(
    classFingerprint = MetadataCellToStringFingerprint,
    name = "<init>",
    returnType = "V"
)

@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object VideoElementToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("VideoElement(linkId="),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/String;",
            location = MatchAfterWithin(3)
        ),
        string(", defaultUrl="),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/String;",
            location = MatchAfterWithin(8)
        )
    )
)

/**
 * Feed video.
 */
@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object VideoElementConstructorFingerprint : Fingerprint(
    classFingerprint = VideoElementToStringFingerprint,
    name = "<init>",
    returnType = "V",
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            reference = { VideoElementToStringFingerprint.instructionMatches[3].instruction.fieldRef!! }
        )
    )
)

@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object VideoUrlsToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("VideoUrls(defaultUrl=")
    )
)

/**
 * Video player urls. The first parameter is the default url.
 */
@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object VideoUrlsConstructorFingerprint : Fingerprint(
    classFingerprint = VideoUrlsToStringFingerprint,
    name = "<init>",
    returnType = "V"
)

/**
 * Video urls of feed videos.
 */
@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object DomainVideoUrlsConstructorFingerprint : Fingerprint(
    definingClass = DOMAIN_VIDEO_URLS_CLASS,
    name = "<init>",
    returnType = "V",
    parameters = listOf(
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Lcom/reddit/domain/model/PlaybackMp4s;",
        "Ljava/lang/String;"
    )
)

@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object DataSpecToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string("DataSpec[")
    )
)

/**
 * Media3 DataSpec, created for every url the video player loads.
 */
@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object DataSpecConstructorFingerprint : Fingerprint(
    classFingerprint = DataSpecToStringFingerprint,
    name = "<init>",
    returnType = "V",
    parameters = listOf(
        "Landroid/net/Uri;",
        "J",
        "I",
        "[B",
        "Ljava/util/Map;",
        "J",
        "J",
        "Ljava/lang/String;",
        "I"
    )
)

/**
 * Video download, which is enqueued with the download manager.
 */
@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object DownloadVideoFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_DIRECT,
            definingClass = $$"Landroid/app/DownloadManager$Request;",
            name = "<init>"
        ),
        string("DownloadManager_not_available")
    )
)

@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object RedgifsLinkConstructorFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/domain/model/Link;",
    name = "<init>",
    returnType = "V",
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            name = "url",
            type = "Ljava/lang/String;",
        ),
    ),
)

internal val redgifsMetadataIdField = findFieldDirect {
    MetadataCellToStringFingerprint.instructionMatches[3].instruction.fieldRef!!
}

internal val redgifsMetadataPathField = findFieldDirect {
    MetadataCellToStringFingerprint.instructionMatches.first().instruction.fieldRef!!
}

internal val redgifsVideoPostIdField = findFieldDirect {
    VideoElementToStringFingerprint.instructionMatches[1].instruction.fieldRef!!
}

internal val redgifsVideoUrlField = findFieldDirect {
    VideoElementToStringFingerprint.instructionMatches[3].instruction.fieldRef!!
}

internal val redgifsDownloadRequestConstructor = findMethodDirect {
    DownloadVideoFingerprint.instructionMatches.first().instruction.methodRef!!
}
