/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * Original hard forked code:
 * https://github.com/ReVanced/revanced-patches/commit/724e6d61b2ecd868c1a9a37d465a688e83d74799
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Runtime port of the upstream patcher fingerprints
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/shared/misc/spoof/Fingerprints.kt,
 * morphe-patches/patches/src/main/kotlin/app/morphe/patches/shared/misc/fix/proto/Fingerprints.kt,
 * morphe-patches/patches/src/main/kotlin/app/morphe/patches/shared/misc/request/Fingerprints.kt
 * and morphe-patches/patches/src/main/kotlin/app/morphe/patches/music/misc/extension/hooks/ApplicationInitHook.kt).
 */

package io.github.nexalloy.morphe.music.misc.spoof

import io.github.nexalloy.AllowAbstract
import io.github.nexalloy.SkipTest
import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.findMethodListDirect
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.newInstance
import io.github.nexalloy.morphe.opcodeEnum
import io.github.nexalloy.morphe.literal
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.string
import org.luckypray.dexkit.result.ClassData

private const val MEDIA_INTERFACES = "Lcom/google/android/libraries/youtube/media/interfaces/"

internal const val HTTP_REQUEST_CLASS_TYPE = "${MEDIA_INTERFACES}HttpRequest;"

private const val STREAMING_DATA_OUTER_CLASS =
    "Lcom/google/protos/youtube/api/innertube/StreamingDataOuterClass\$StreamingData;"

/**
 * The onCreate of the YT Music application class, where the spoof client order is set.
 */
internal object YouTubeMusicApplicationInitFingerprint : Fingerprint(
    name = "onCreate",
    returnType = "V",
    parameters = listOf(),
    strings = listOf("activity")
)

/**
 * Called by native code when a media response asks the player to load the player response again.
 * The native CppProxy implementation is excluded at the resolution below.
 */
@SkipTest
internal object ReloadPlayerResponseFingerprint : Fingerprint(
    superclass = "${MEDIA_INTERFACES}PlaybackControllerCallbacks;",
    returnType = "V",
    parameters = listOf(
        "Lcom/google/android/apps/youtube/proto/streaming/ReloadPlayerResponseOuterClass\$ReloadPlayerResponse;"
    )
)

/** The app's own implementation, whose native CppProxy subclass is excluded. */
internal val reloadPlayerResponseMethod = findMethodDirect {
    ReloadPlayerResponseFingerprint.matchAll().single {
        !it.method.declaredClass!!.descriptor.endsWith("CppProxy;")
    }.method
}

/**
 * Native check of whether the Onesie response of a video can be used with the streams of the
 * player response. YouTube Music does not have it, so the patch resolves it with matchAllOrNull.
 */
@SkipTest
internal object OnesieResponseCompatibilityFingerprint : Fingerprint(
    definingClass = "${MEDIA_INTERFACES}OnesieResponseSelector\$CppProxy;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(
        "Ljava/lang/String;",
        "${MEDIA_INTERFACES}PlayerResponseCompatibilityRequirements;"
    )
)

private object CreateStreamingDataParentFingerprint : Fingerprint(
    strings = listOf("Invalid playback type; streaming data is not playable")
)

internal object CreateStreamingDataFingerprint : Fingerprint(
    classFingerprint = CreateStreamingDataParentFingerprint,
    name = "<init>",
    parameters = listOf("L"),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = STREAMING_DATA_OUTER_CLASS
        ),
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = STREAMING_DATA_OUTER_CLASS,
            location = MatchAfterWithin(7)
        ),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            location = MatchAfterImmediately()
        ),
        opcode(
            opcode = Opcode.IF_NEZ,
            location = MatchAfterImmediately()
        ),
        fieldAccess(
            opcode = Opcode.SGET_OBJECT,
            location = MatchAfterImmediately()
        ),
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            location = MatchAfterImmediately()
        ),
        opcode(Opcode.AND_INT_LIT8),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            location = MatchAfterWithin(5)
        )
    )
)

internal object AbrStateDataFingerprint : Fingerprint(
    returnType = "J",
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = {
                CreateStreamingDataFingerprint.instructionMatches.last().instruction.fieldRef!!.type!!.descriptor
            }
        ),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = {
                CreateStreamingDataFingerprint.instructionMatches.last().instruction.fieldRef!!.type!!.descriptor
            },
            location = MatchAfterImmediately()
        ),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            location = MatchAfterWithin(5)
        ),
        string("/videoplayback"),
        string("AbrStateDataSpec: Unexpected http body.")
    )
)

internal object PlayerConfigBuilderFingerprint : Fingerprint(
    returnType = "Lcom/google/protobuf/MessageLite;",
    filters = listOf(
        string("com.google.android.libraries.youtube.innertube.pref.player_config_supplier"),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            name = "decode",
            returnType = "[B"
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            name = "createBuilder",
            parameters = listOf(),
            location = MatchAfterWithin(5)
        ),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            smali = "Lcom/google/protobuf/ExtensionRegistryLite;->getGeneratedRegistry()Lcom/google/protobuf/ExtensionRegistryLite;",
            location = MatchAfterWithin(5)
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            name = "mergeFrom",
            parameters = listOf("[B", "Lcom/google/protobuf/ExtensionRegistryLite;"),
            location = MatchAfterWithin(5)
        ),
        opcode(
            opcode = Opcode.CHECK_CAST,
            location = MatchAfterWithin(3)
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            name = "build",
            parameters = listOf(),
            location = MatchAfterWithin(3)
        )
    )
)

internal object BuildMediaDataSourceFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
    parameters = listOf(
        "Landroid/net/Uri;",
        "J",
        "Ljava/lang/String;",
        "I",
        "[B",
        "Ljava/util/Map;",
        "J",
        "J",
        "Ljava/lang/String;",
        "I",
        "L",
    ),
    filters = listOf(
        fieldAccess(
            definingClass = "this",
            opcode = Opcode.IPUT_OBJECT,
            type = "Landroid/net/Uri;"
        ),
        fieldAccess(
            definingClass = "this",
            opcode = Opcode.IPUT,
            type = "I"
        ),
        fieldAccess(
            definingClass = "this",
            opcode = Opcode.IPUT_OBJECT,
            type = "[B"
        ),
        opcode(Opcode.RETURN_VOID)
    )
)

internal object HlsCurrentTimeFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Z", "L"),
    filters = listOf(
        literal(45355374L) // HLS current time feature flag.
    )
)

internal object MediaFetchEnumConstructorFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf(
        "ENABLED",
        "DISABLED_FOR_PLAYBACK",
        "DISABLED_BY_SABR_STREAMING_URI"
    )
)

/**
 * Builds the video format line of the stats for nerds. The 21.42+ mime type codecs parser with
 * the same string is excluded at the resolution below.
 */
@SkipTest
internal object NerdsStatsVideoFormatBuilderFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Ljava/lang/String;",
    parameters = listOf("L"),
    filters = listOf(
        string("codecs=\"")
    )
)

internal val nerdsStatsVideoFormatBuilderMethod = findMethodDirect {
    NerdsStatsVideoFormatBuilderFingerprint.matchAll().single {
        it.method.paramTypes.first().descriptor != "Ljava/lang/String;"
    }.method
}

internal object MediaSessionFeatureFlagFingerprint : Fingerprint(
    parameters = listOf(),
    returnType = "Z",
    filters = listOf(
        literal(45640404L),
        opcode(
            opcode = Opcode.MOVE_RESULT,
            location = MatchAfterWithin(3)
        ),
        opcode(
            opcode = Opcode.RETURN,
            location = MatchAfterWithin(5)
        )
    )
)

// region media request interfaces

/**
 * The parameters depend on the app version. Newer versions also take a boolean.
 */
internal object HttpRequestConstructorFingerprint : Fingerprint(
    definingClass = HTTP_REQUEST_CLASS_TYPE,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR)
)

/** The constructor writes the header name before its value, as in the upstream fingerprint. */
internal object HttpHeaderConstructorFingerprint : Fingerprint(
    definingClass = "${MEDIA_INTERFACES}HttpHeader;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
    filters = listOf(
        fieldAccess(opcode = Opcode.IPUT_OBJECT, definingClass = "this", type = "Ljava/lang/String;"),
        fieldAccess(opcode = Opcode.IPUT_OBJECT, definingClass = "this", type = "Ljava/lang/String;")
    )
)

internal val mediaHttpHeaderNameField = findFieldDirect {
    HttpHeaderConstructorFingerprint.instructionMatches.first().instruction.fieldRef!!
}

/**
 * The app's own implementation of the media request interface, which native code calls to
 * start a request. The native CppProxy subclass is excluded at the resolution below.
 */
@SkipTest
internal object NetFetchStartFetchTaskFingerprint : Fingerprint(
    superclass = "${MEDIA_INTERFACES}NetFetch;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "${MEDIA_INTERFACES}NetFetchTask;",
    parameters = listOf(HTTP_REQUEST_CLASS_TYPE, "${MEDIA_INTERFACES}NetFetchCallbacks;")
)

internal val netFetchStartFetchTaskMethod = findMethodDirect {
    NetFetchStartFetchTaskFingerprint.matchAll().single {
        !it.method.declaredClass!!.descriptor.endsWith("CppProxy;")
    }.method
}

/**
 * Receives each chunk of a media response before native code reads it.
 */
internal object NetFetchResponseChunkFingerprint : Fingerprint(
    definingClass = "${MEDIA_INTERFACES}NetFetchCallbacks\$CppProxy;",
    returnType = "V",
    parameters = listOf("Ljava/nio/ByteBuffer;")
)

// endregion

// region request building

/**
 * Builds the url request of every network request the app makes.
 * The upstream shared fingerprint, resolved against the YouTube Music host.
 */
@StrictQuery
internal object MusicBuildRequestFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Lorg/chromium/net/UrlRequest\$Builder;",
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            name = "newUrlRequestBuilder",
            parameters = listOf(
                "Ljava/lang/String;",
                "Lorg/chromium/net/UrlRequest\$Callback;",
                "Ljava/util/concurrent/Executor;"
            )
        ),
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            smali = "Ljava/util/Map;->entrySet()Ljava/util/Set;",
            location = MatchAfterWithin(15)
        ),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, name = "ordinal"),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, name = "setPriority")
    )
)

/** Reads the url of the request from the connection the request is built for. */
@get:AllowAbstract
internal val musicRequestUrlMethod = findMethodDirect {
    val method = MusicBuildRequestFingerprint.run()
    val builder = method.instructions.indexOfFirst { it.methodRef?.name == "newUrlRequestBuilder" }
    method.instructions.take(builder).last {
        it.methodRef?.returnType?.descriptor == "Ljava/lang/String;" &&
                it.methodRef?.declaredClass == method.paramTypes.first()
    }.methodRef!!
}

// endregion

// region proto parsing

/**
 * The generated protobuf message parser that turns the bytes of a replacement streaming data
 * into a player response of the host.
 */
internal object ProtobufClassParseByteArrayFingerprint : Fingerprint(
    name = "parseFrom",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "L",
    parameters = listOf("L", "[B"),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.SGET_OBJECT,
            definingClass = "Lcom/google/protobuf/ExtensionRegistryLite;",
            type = "Lcom/google/protobuf/ExtensionRegistryLite;"
        )
    )
)

// endregion

// region video streaming data

/**
 * The toString of the video streaming data of a player response.
 * The upstream shared fingerprint, resolved against the YouTube Music host.
 */
internal object VideoStreamingDataToStringFingerprint : Fingerprint(
    name = "toString",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/String;",
    strings = listOf("VideoStreamingData(itags=")
)

/**
 * The constructor of the video streaming data of a player response.
 * The second instruction match is the video id field of the video the data is of.
 */
internal object VideoStreamingDataConstructorFingerprint : Fingerprint(
    classFingerprint = VideoStreamingDataToStringFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = STREAMING_DATA_OUTER_CLASS
        ),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/String;"
        ),
        newInstance("Ljava/util/ArrayList;"),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = STREAMING_DATA_OUTER_CLASS
        )
    )
)

/** The video id field of the video streaming data of a player response. */
internal val videoStreamingDataVideoIdField = findFieldDirect {
    VideoStreamingDataConstructorFingerprint.instructionMatches[1].instruction.fieldRef!!
}

// endregion

// region media fetch decision

/** The enum field the media fetch is forced to when SABR playback of a video is disabled. */
internal val mediaFetchEnumSabrField = findFieldDirect {
    val match = MediaFetchEnumConstructorFingerprint.match()
    val enumType = match.method.descriptor.substringBefore("->")
    match.method.instructions.drop(match.stringMatches.last().index + 1).first {
        it.opcodeEnum == Opcode.SPUT_OBJECT && it.fieldRef?.type?.descriptor == enumType
    }.fieldRef!!
}

/**
 * Decides the media fetch the streams of a video are loaded with, from the player config and
 * the video streaming data of the video. The synthetic fingerprint of the upstream patch.
 */
internal val mediaFetchSabrDecisionMethod = findMethodDirect {
    val enumType = MediaFetchEnumConstructorFingerprint.match().method.descriptor.substringBefore("->")
    val videoStreamingDataType = VideoStreamingDataToStringFingerprint.match().method.declaredClass!!.descriptor
    Fingerprint(
        returnType = enumType,
        parameters = listOf("L", videoStreamingDataType),
        filters = listOf(opcode(Opcode.SGET_OBJECT), opcode(Opcode.RETURN_OBJECT))
    ).run()
}

// endregion

// region streaming data replacement

/** The streaming data field of the player response proto (9.46.51: `amfw.h`). */
internal val playerProtoStreamingDataField = findFieldDirect {
    CreateStreamingDataFingerprint.instructionMatches.first().instruction.fieldRef!!
}

/** The field of the created streaming data the replacement streaming data is set to (`wkd.a`). */
internal val createStreamingDataSetField = findFieldDirect {
    CreateStreamingDataFingerprint.instructionMatches[1].instruction.fieldRef!!
}

/** The video details field of the player response proto (`amfw.g`). */
internal val playerProtoVideoDetailsField = findFieldDirect {
    CreateStreamingDataFingerprint.instructionMatches[2].instruction.fieldRef!!
}

/** The player config field of the player response proto (`amfw.e`). */
internal val playerProtoPlayerConfigField = findFieldDirect {
    CreateStreamingDataFingerprint.instructionMatches.last().instruction.fieldRef!!
}

/** The media common config field of the player config (`apbf.c`). */
internal val mediaCommonConfigField = findFieldDirect {
    AbrStateDataFingerprint.instructionMatches[1].instruction.fieldRef!!
}

/** The media ustreamer request config field of the media common config. */
internal val mediaUstreamerRequestConfigField = findFieldDirect {
    AbrStateDataFingerprint.instructionMatches[2].instruction.fieldRef!!
}

/** Parses player config bytes into a player config of the host. */
internal val playerConfigCreateBuilderMethod = findMethodDirect {
    PlayerConfigBuilderFingerprint.instructionMatches[2].instruction.methodRef!!
}

internal val playerConfigMergeFromMethod = findMethodDirect {
    PlayerConfigBuilderFingerprint.instructionMatches[4].instruction.methodRef!!
}

internal val playerConfigBuildMethod = findMethodDirect {
    PlayerConfigBuilderFingerprint.instructionMatches[6].instruction.methodRef!!
}

/** Parses replacement streaming data bytes into a player response proto of the host. */
internal val playerResponseParseFromMethod = findMethodDirect {
    ProtobufClassParseByteArrayFingerprint.match().method
}

// endregion

// region media request body

/** The http method field of the media data source the post body of is removed (`bus`). */
internal val mediaDataSourceUrlField = findFieldDirect {
    BuildMediaDataSourceFingerprint.instructionMatches.first().instruction.fieldRef!!
}

internal val mediaDataSourceHttpMethodField = findFieldDirect {
    BuildMediaDataSourceFingerprint.instructionMatches[1].instruction.fieldRef!!
}

internal val mediaDataSourceHttpBodyField = findFieldDirect {
    BuildMediaDataSourceFingerprint.instructionMatches[2].instruction.fieldRef!!
}

// endregion

// region feature flags

/**
 * The boolean experiment flag getter the HLS current time feature flag is read with
 * (9.46.51: `vyx.m(I, J)Z`).
 */
internal val hlsCurrentTimeFlagMethod = findMethodDirect {
    val literalIndex = HlsCurrentTimeFingerprint.match().instructionMatches.first().index
    HlsCurrentTimeFingerprint.match().method.instructions.drop(literalIndex).first {
        it.methodRef?.returnType?.descriptor == "Z" &&
                it.methodRef?.paramTypes?.map(ClassData::descriptor) == listOf("I", "J")
    }.methodRef!!
}

// endregion
