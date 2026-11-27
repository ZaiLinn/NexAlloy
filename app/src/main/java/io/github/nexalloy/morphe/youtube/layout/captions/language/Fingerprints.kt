package io.github.nexalloy.morphe.youtube.layout.captions.language

import io.github.nexalloy.SkipTest
import io.github.nexalloy.AllowAbstract
import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.InstructionLocation.MatchFirst
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.ResourceType
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.findMethodListDirect
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.newInstance
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.resourceLiteral
import io.github.nexalloy.morphe.string

/**
 * Ported from app.morphe.patches.youtube.layout.captions.Fingerprints.kt.
 *
 * The fingerprint names differ from upstream, because the persistent dexkit cache
 * is keyed by the simple name of the fingerprint class.
 * [io.github.nexalloy.morphe.youtube.layout.captions.Fingerprints] already uses the upstream names.
 *
 * Anchors are verified against YouTube 21.45.82. The obfuscated classes and methods
 * each fingerprint resolves to are documented on the fingerprints and resolved members:
 * - asyt: subtitle manager. atam: caption track (21.02+ concrete class).
 * - atal: caption track builder. atao: caption tracks manager.
 * - armu: caption selection reason enum. armo: caption track event.
 * - asdf: caption overlay presenter. aszb: caption track request.
 * - arcl: cached caption track file parser. aqma: response parse runnable. ageq: timed text parser.
 * - aszh: timed text <s> element handler. atab: timed text pen table.
 * - afic: Cronet http requester. juq: player overlay state. jur: overlay visibility interface.
 * - ouq: captions overlay. aksd: engagement panel header setup.
 */

/** Resolves the subtitle manager class at its static captions-enabled check. */
internal object CaptionSubtitleManagerClassFingerprint : Fingerprint(
    isStatic = true,
    returnType = "Z",
    parameters = listOf("L", "Landroid/view/accessibility/CaptioningManager;"),
    filters = listOf(
        fieldAccess(smali = "Ljava/util/concurrent/TimeUnit;->SECONDS:Ljava/util/concurrent/TimeUnit;"),
        methodCall("Landroid/view/accessibility/CaptioningManager;->isEnabled()Z"),
    )
)

/**
 * Resolves the caption track class at its isDisableOption check. 21.02+.
 * Match 1 is the language code field access of the track (atam.a).
 */
internal object LanguageCaptionTrackIsDisableOptionFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(
        string("DISABLE_CAPTIONS_OPTION"),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/String;",
            location = MatchAfterImmediately()
        ),
        methodCall(
            smali = "Ljava/lang/String;->equals(Ljava/lang/Object;)Z",
            location = MatchAfterImmediately()
        ),
    )
)

/**
 * Resolves the caption track class at its isAutoTranslated check. 21.02+.
 * Match 0 is the vss id field access of the track (atam.k).
 */
internal object LanguageCaptionTrackIsAutoTranslatedFingerprint : Fingerprint(
    classFingerprint = LanguageCaptionTrackIsDisableOptionFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/String;"
        ),
        string("t", location = MatchAfterImmediately()),
        methodCall(
            smali = "Ljava/lang/String;->startsWith(Ljava/lang/String;)Z",
            location = MatchAfterImmediately()
        ),
    )
)

/** Resolves the caption track builder class at its build method. Match 0 is the label field. */
internal object LanguageCaptionTrackBuilderBuildFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf(),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/CharSequence;"
        ),
        string(" languageCode"),
        string(" vssId"),
        string(" url"),
    )
)

/** Copy constructor of the caption track builder, which takes the track to copy. */
internal object LanguageCaptionTrackBuilderCopyConstructorFingerprint : Fingerprint(
    classFingerprint = LanguageCaptionTrackBuilderBuildFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    parameters = listOf("L"),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/CharSequence;"
        )
    )
)

/** Builder setter of a caption track property, anchored at its null check message. */
internal open class CaptionTrackBuilderSetterFingerprint(nullValueMessage: String) : Fingerprint(
    classFingerprint = LanguageCaptionTrackBuilderBuildFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Ljava/lang/String;"),
    filters = listOf(string(nullValueMessage))
)

internal object SetTrackLanguageCodeFingerprint : CaptionTrackBuilderSetterFingerprint("Null languageCode")

internal object SetTrackVideoIdFingerprint : CaptionTrackBuilderSetterFingerprint("Null videoId")

internal object SetTrackUrlFingerprint : CaptionTrackBuilderSetterFingerprint("Null url")

internal object SetTrackVssIdFingerprint : CaptionTrackBuilderSetterFingerprint("Null vssId")

/** Creates a caption track builder with the default values of every caption track. */
internal object CaptionTrackBuilderDefaultsFingerprint : Fingerprint(
    definingClass = { LanguageCaptionTrackIsDisableOptionFingerprint().declaredClass!!.descriptor },
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = { LanguageCaptionTrackBuilderBuildFingerprint().declaredClass!!.descriptor },
    parameters = { emptyList<CharSequence>() },
)

/** Resolves the caption tracks manager class at its direct tracks getter. */
internal object LanguageCaptionTracksManagerDirectTracksFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/util/List;",
    parameters = listOf(),
    filters = listOf(
        string("AUTO_TRANSLATE_CAPTIONS_OPTION")
    )
)

/**
 * The auto-translated tracks getter of the caption tracks manager.
 * Match 0 is the url field access of the source caption track (atam.m).
 */
internal object LanguageCaptionTracksManagerAutoTranslateTracksFingerprint : Fingerprint(
    classFingerprint = LanguageCaptionTracksManagerDirectTracksFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/util/List;",
    parameters = listOf(),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT, type = "Ljava/lang/String;"),
        methodCall(
            smali = "Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;",
            location = MatchAfterImmediately()
        ),
        string("&tlang=", location = MatchAfterImmediately()),
    )
)

/** The track manager's default source, without subtitle preference or CC state overrides. */
internal object CaptionTracksManagerDefaultSourceFingerprint : Fingerprint(
    classFingerprint = LanguageCaptionTracksManagerDirectTracksFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = { LanguageCaptionTrackBuilderBuildFingerprint().returnType!!.descriptor },
    parameters = listOf(),
)

/**
 * The general translation source fallback that is used if a translation language
 * has no source of its own. Same method as the auto-translate tracks getter, matched
 * at the source lookup.
 *
 * Matches: 0 metadata, 1 audio track, 2 null check, 3 track convert call,
 * 4 move result, 5 general source indices, 6 iterator, 7 index unboxing,
 * 8 audio caption indices, 9 contains, 10 caption tracks, 11 list get,
 * 12 metadata to track convert call.
 */
internal object CaptionTracksManagerTranslationSourceFingerprint : Fingerprint(
    classFingerprint = LanguageCaptionTracksManagerDirectTracksFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/util/List;",
    parameters = listOf(),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT, definingClass = "this"),
        fieldAccess(opcode = Opcode.IGET_OBJECT, definingClass = "this"),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        methodCall(opcode = Opcode.INVOKE_DIRECT, definingClass = "this", parameters = listOf("L")),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        fieldAccess(opcode = Opcode.IGET_OBJECT, location = MatchAfterWithin(4)),
        methodCall(
            smali = "Ljava/util/List;->iterator()Ljava/util/Iterator;",
            location = MatchAfterImmediately()
        ),
        methodCall(
            smali = "Ljava/lang/Integer;->intValue()I",
            location = MatchAfterWithin(7)
        ),
        fieldAccess(opcode = Opcode.IGET_OBJECT, location = MatchAfterWithin(1)),
        methodCall(
            smali = "Ljava/util/List;->contains(Ljava/lang/Object;)Z",
            location = MatchAfterImmediately()
        ),
        fieldAccess(opcode = Opcode.IGET_OBJECT, location = MatchAfterWithin(2)),
        methodCall(
            name = "get",
            parameters = listOf("I"),
            returnType = "Ljava/lang/Object;",
            location = MatchAfterImmediately()
        ),
        methodCall(
            opcode = Opcode.INVOKE_DIRECT,
            definingClass = "this",
            parameters = listOf("L"),
            location = MatchAfterWithin(2)
        ),
    )
)

/** Calls of the auto-translate tracks getter inside the subtitle manager. Matches more than one method. */
@SkipTest
internal object AutoTranslateTracksCallFingerprint : Fingerprint(
    classFingerprint = CaptionSubtitleManagerClassFingerprint,
    filters = listOf(
        methodCall(reference = { LanguageCaptionTracksManagerAutoTranslateTracksFingerprint() })
    )
)

/** The methods of the subtitle manager that ask for the auto-translated tracks. */
@get:SkipTest
internal val autoTranslateTracksCallers = findMethodListDirect {
    AutoTranslateTracksCallFingerprint.matchAll().map { it.method }
}

/**
 * The final translation-count check before the manager builds its own Auto-translate
 * option row in the direct tracks getter.
 *
 * Matches: 0 translation size, 1 move result, 2 empty check, 3 CC enabled check,
 * 4 skip, 5 option video id field, 6 option label field, 7 option language code,
 * 8 returned list.
 */
internal object AutoTranslateRowCountFingerprint : Fingerprint(
    classFingerprint = LanguageCaptionTracksManagerDirectTracksFingerprint,
    parameters = listOf(),
    returnType = "Ljava/util/List;",
    filters = listOf(
        methodCall(name = "size", parameters = listOf(), returnType = "I"),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        opcode(Opcode.IF_LEZ, location = MatchAfterImmediately()),
        fieldAccess(
            opcode = Opcode.IGET_BOOLEAN,
            definingClass = "this",
            type = "Z",
            location = MatchAfterImmediately()
        ),
        opcode(Opcode.IF_NEZ, location = MatchAfterImmediately()),
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
        ),
        string("AUTO_TRANSLATE_CAPTIONS_OPTION", location = MatchAfterWithin(3)),
        opcode(Opcode.RETURN_OBJECT, location = io.github.nexalloy.morphe.InstructionLocation.MatchAfterAnywhere()),
    )
)

/** Returns the direct caption tracks of the subtitle manager. Match 0 is the tracks manager field. */
internal object SubtitleManagerDirectTracksFingerprint : Fingerprint(
    classFingerprint = CaptionSubtitleManagerClassFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/util/List;",
    parameters = listOf(),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = { LanguageCaptionTracksManagerDirectTracksFingerprint().declaredClass!!.descriptor }
        ),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        methodCall(reference = { LanguageCaptionTracksManagerDirectTracksFingerprint() }, location = MatchAfterImmediately()),
    )
)

/** The default caption track of the subtitle manager. */
internal object DefaultCaptionTrackFingerprint : Fingerprint(
    classFingerprint = CaptionSubtitleManagerClassFingerprint,
    returnType = "L",
    parameters = listOf(),
    filters = listOf(
        methodCall("Landroid/view/accessibility/CaptioningManager;->isEnabled()Z"),
    )
)

/** Calls the default caption track when the caption tracks of a video are loaded. */
internal object PreferredCaptionsTrackFingerprint : Fingerprint(
    classFingerprint = DefaultCaptionTrackFingerprint,
    parameters = listOf("L", "L"),
    filters = listOf(
        methodCall(reference = { DefaultCaptionTrackFingerprint() }),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
    )
)

/** Sets the caption track for a selection reason, matched at its debug throwable. */
@StrictQuery
internal object SetSubtitleTrackFingerprint : Fingerprint(
    classFingerprint = CaptionSubtitleManagerClassFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    filters = listOf(
        newInstance("Ljava/lang/Throwable;")
    )
)

/** Native selection of a track, which calls the setSubtitleTrack method. */
internal object CaptionSelectionFingerprint : Fingerprint(
    classFingerprint = CaptionSubtitleManagerClassFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = { "V" },
    parameters = {
        listOf(
            SetSubtitleTrackFingerprint().paramTypes[0].descriptor,
            SetSubtitleTrackFingerprint().paramTypes[1].descriptor,
        )
    },
    filters = listOf(
        methodCall(reference = { SetSubtitleTrackFingerprint() })
    )
)

/** Enum of the caption selection reasons, matched at the field of the default reason. */
internal object CaptionDefaultSelectionFingerprint : Fingerprint(
    definingClass = { CaptionSelectionFingerprint().paramTypes[1].descriptor },
    name = "<clinit>",
    returnType = { "V" },
    filters = listOf(
        string("DEFAULT"),
        methodCall(name = "<init>", location = MatchAfterWithin(5)),
        fieldAccess(
            opcode = Opcode.SPUT_OBJECT,
            definingClass = "this",
            type = { CaptionSelectionFingerprint().paramTypes[1].descriptor },
            location = MatchAfterImmediately()
        ),
    )
)

/** Parses a cached caption track file. Match 1 is the caption track field of the request. */
@StrictQuery
internal object LanguageCaptionFileParserFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/Object;", "L"),
    filters = listOf(
        // Caption request.
        opcode(Opcode.CHECK_CAST, location = MatchFirst()),
        // Caption track of the request.
        fieldAccess(opcode = Opcode.IGET_OBJECT, location = MatchAfterWithin(2)),
        methodCall(smali = "Ljava/io/File;->exists()Z"),
        methodCall(smali = "Ljava/io/FileInputStream;-><init>(Ljava/io/File;)V"),
        // Parses the cached file.
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            parameters = listOf("Ljava/io/InputStream;", "L"),
            returnType = "Ljava/lang/Object;"
        ),
    )
)

/**
 * Parses the response of a request. Used for caption tracks and other requests.
 * Match 2 is the request field of the runnable.
 */
@StrictQuery
internal object RequestResponseParserFingerprint : Fingerprint(
    name = "run",
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        // Response parser.
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            parameters = listOf("Ljava/lang/Object;"),
            returnType = "Ljava/lang/Object;"
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        // Request.
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/Object;",
            location = MatchAfterWithin(1)
        ),
        // Response callback.
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;"),
            returnType = "V",
            location = MatchAfterImmediately()
        ),
        // Parse failure handler.
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;", "L", "Ljava/lang/Exception;"),
            returnType = "V"
        ),
    )
)

/**
 * Timed text (srv3) `<s>` segment element handler. Resolves the pen lookup method.
 * Match 2 is the pen lookup by id.
 */
internal object TimedTextSegmentElementFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/util/Deque;", "Lorg/xml/sax/Attributes;", "Ljava/lang/String;"),
    filters = listOf(
        string("p"),
        methodCall(
            smali = "Lorg/xml/sax/Attributes;->getValue(Ljava/lang/String;)Ljava/lang/String;",
            location = MatchAfterImmediately()
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            parameters = listOf("I"),
            returnType = "L",
            location = MatchAfterWithin(5)
        ),
        string("t"),
        string("ac"),
        string("<br/>"),
    )
)

/** Builds a Timed Text API request with the Cronet url request builder. */
@StrictQuery
internal object TimedTextUrlFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf("L"),
    filters = listOf(
        methodCall(
            smali = "Lorg/chromium/net/CronetEngine;->newUrlRequestBuilder(Ljava/lang/String;Lorg/chromium/net/UrlRequest\$Callback;Ljava/util/concurrent/Executor;)Lorg/chromium/net/UrlRequest\$Builder;"
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        methodCall(smali = "Lorg/chromium/net/UploadDataProviders;->create(Ljava/nio/ByteBuffer;)Lorg/chromium/net/UploadDataProvider;"),
    )
)

/**
 * Player state given to each player overlay to decide if it is shown.
 * Matches: 0 watch state, 1 vr active, 2 inline active, 3 pip active.
 */
internal object PlayerOverlayStateToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT, definingClass = "this", location = MatchFirst()),
        fieldAccess(
            opcode = Opcode.IGET_BOOLEAN,
            definingClass = "this",
            type = "Z",
            location = MatchAfterWithin(1)
        ),
        fieldAccess(
            opcode = Opcode.IGET_BOOLEAN,
            definingClass = "this",
            type = "Z",
            location = MatchAfterWithin(7)
        ),
        fieldAccess(
            opcode = Opcode.IGET_BOOLEAN,
            definingClass = "this",
            type = "Z",
            location = MatchAfterWithin(5)
        ),
        string("isPipActive", io.github.nexalloy.morphe.StringComparisonType.CONTAINS),
    )
)

/** Creates the player state and asks an overlay if it is shown. Matches more than one method. */
@SkipTest
internal object PlayerOverlayVisibilityFingerprint : Fingerprint(
    filters = listOf(
        methodCall(
            definingClass = { PlayerOverlayStateToStringFingerprint().declaredClass!!.descriptor },
            name = "<init>"
        ),
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            parameters = { listOf(PlayerOverlayStateToStringFingerprint().declaredClass!!.descriptor) },
            returnType = "Z",
            location = MatchAfterImmediately()
        ),
    )
)

/** Name of the captions overlay. */
internal object CaptionsOverlayNameFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("player_overlay_caption")
    )
)

/**
 * Sets the caption track of a caption track event. Match 0 is the track field of the
 * event, match 1 the track setter of the presenter.
 */
@StrictQuery
internal object CaptionTrackEventFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = { LanguageCaptionTrackIsDisableOptionFingerprint().declaredClass!!.descriptor }
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            definingClass = "this",
            parameters = { listOf(LanguageCaptionTrackIsDisableOptionFingerprint().declaredClass!!.descriptor) },
            returnType = "V",
            location = MatchAfterImmediately()
        ),
    )
)

/** Sets up the header of an engagement panel, such as the transcript panel. */
internal object EngagementPanelHeaderFingerprint : Fingerprint(
    isStatic = true,
    returnType = "V",
    parameters = listOf("Landroid/view/ViewGroup;", "L"),
    filters = listOf(
        resourceLiteral(ResourceType.ID, "panel_header"),
        resourceLiteral(ResourceType.ID, "close_button"),
        resourceLiteral(ResourceType.ID, "panel_subheader"),
    )
)

// Resolved members. Each is resolved lazily by the patch that uses it,
// because the instructions of a fingerprint are only available while its query runs.

/** Language code field of a caption track (atam.a). */
internal val languageCaptionBridgeCaptionTrackLanguageCodeField = findFieldDirect {
    LanguageCaptionTrackIsDisableOptionFingerprint.instructionMatches[1].instruction.fieldRef!!
}

/** Vss id field of a caption track (atam.k). */
internal val languageCaptionBridgeCaptionTrackVssIdField = findFieldDirect {
    LanguageCaptionTrackIsAutoTranslatedFingerprint.instructionMatches[0].instruction.fieldRef!!
}

/** Url field of a caption track (atam.m). */
internal val languageCaptionBridgeCaptionTrackUrlField = findFieldDirect {
    LanguageCaptionTracksManagerAutoTranslateTracksFingerprint.instructionMatches[0].instruction.fieldRef!!
}

/** Label field of a caption track builder (atal.c). */
internal val languageCaptionBridgeCaptionTrackBuilderLabelField = findFieldDirect {
    LanguageCaptionTrackBuilderBuildFingerprint.instructionMatches[0].instruction.fieldRef!!
}

/** Copy constructor of the caption track builder, which takes the track to copy (atal.<init>(atam)). */
internal val languageCaptionBridgeCaptionTrackBuilderCopyConstructor = findMethodDirect {
    LanguageCaptionTrackBuilderCopyConstructorFingerprint()
}

/** Build method of the caption track builder (atal.a). */
internal val languageCaptionBridgeCaptionTrackBuilderBuildMethod = findMethodDirect {
    LanguageCaptionTrackBuilderBuildFingerprint()
}

/** Creates a caption track builder with the default values of every caption track (atam.a). */
internal val captionTrackBuilderDefaultsMethod = findMethodDirect {
    CaptionTrackBuilderDefaultsFingerprint()
}

internal val setTrackLanguageCodeMethod = findMethodDirect { SetTrackLanguageCodeFingerprint() }

internal val setTrackVideoIdMethod = findMethodDirect { SetTrackVideoIdFingerprint() }

internal val setTrackUrlMethod = findMethodDirect { SetTrackUrlFingerprint() }

internal val setTrackVssIdMethod = findMethodDirect { SetTrackVssIdFingerprint() }

/** Tracks manager field of the subtitle manager (asyt.p). */
internal val subtitleManagerTracksField = findFieldDirect {
    SubtitleManagerDirectTracksFingerprint.instructionMatches[0].instruction.fieldRef!!
}

/** Default caption track of the subtitle manager (asyt.s). */
internal val subtitleManagerDefaultTrackMethod = findMethodDirect {
    DefaultCaptionTrackFingerprint()
}

/** Native track selection of the subtitle manager (asyt.l). */
internal val subtitleManagerSelectTrackMethod = findMethodDirect {
    CaptionSelectionFingerprint()
}

/** Default caption selection reason of the selection reason enum (armu.b). */
internal val captionDefaultSelectionReasonField = findFieldDirect {
    CaptionDefaultSelectionFingerprint.instructionMatches.last().instruction.fieldRef!!
}

/** Direct caption tracks of the caption tracks manager (atao.h). */
internal val tracksManagerDirectTracksMethod = findMethodDirect {
    LanguageCaptionTracksManagerDirectTracksFingerprint()
}

/** Auto-translated caption tracks of the caption tracks manager (atao.g). */
internal val tracksManagerAutoTranslateTracksMethod = findMethodDirect {
    LanguageCaptionTracksManagerAutoTranslateTracksFingerprint()
}

/** Default caption source of the current audio track (atao.b). */
internal val tracksManagerDefaultSourceMethod = findMethodDirect {
    CaptionTracksManagerDefaultSourceFingerprint()
}

/** Caption track metadata field of the caption tracks manager (atao.a). */
internal val tracksManagerMetadataField = findFieldDirect {
    CaptionTracksManagerTranslationSourceFingerprint.instructionMatches[0].instruction.fieldRef!!
}

/** Current audio track field of the caption tracks manager (atao.b field). */
internal val tracksManagerAudioTrackField = findFieldDirect {
    CaptionTracksManagerTranslationSourceFingerprint.instructionMatches[1].instruction.fieldRef!!
}

/** General translation source indices field of the caption metadata (bkzy.f). */
internal val tracksManagerSourceIndicesField = findFieldDirect {
    CaptionTracksManagerTranslationSourceFingerprint.instructionMatches[5].instruction.fieldRef!!
}

/** Caption track indices field of the current audio track (bkzw.d). */
internal val tracksManagerAudioCaptionIndicesField = findFieldDirect {
    CaptionTracksManagerTranslationSourceFingerprint.instructionMatches[8].instruction.fieldRef!!
}

/** Caption track metadata list field of the caption metadata (bkzy.b). */
internal val tracksManagerCaptionTracksField = findFieldDirect {
    CaptionTracksManagerTranslationSourceFingerprint.instructionMatches[10].instruction.fieldRef!!
}

/** Converts caption track metadata to a caption track (atao.i). */
internal val tracksManagerConvertTrackMethod = findMethodDirect {
    CaptionTracksManagerTranslationSourceFingerprint.instructionMatches[12].instruction.methodRef!!
}

/** Video id field of the caption tracks manager, used by the auto-translate option row (atao.d). */
internal val tracksManagerVideoIdField = findFieldDirect {
    AutoTranslateRowCountFingerprint.instructionMatches[5].instruction.fieldRef!!
}

/** Auto-translate option label field of the caption tracks manager (atao.g). */
internal val tracksManagerOptionLabelField = findFieldDirect {
    AutoTranslateRowCountFingerprint.instructionMatches[6].instruction.fieldRef!!
}

/** Caption track field of a caption track request (aszb.a). */
internal val captionRequestTrackField = findFieldDirect {
    LanguageCaptionFileParserFingerprint.instructionMatches[1].instruction.fieldRef!!
}

/** Timed text decode entry, used for cached and downloaded caption tracks alike (ageq.a). */
internal val timedTextParseMethod = findMethodDirect {
    LanguageCaptionFileParserFingerprint.instructionMatches.last().instruction.methodRef!!
}

/** Request field of the response parse runnable (aqma.b). */
internal val requestResponseRequestField = findFieldDirect {
    RequestResponseParserFingerprint.instructionMatches[2].instruction.fieldRef!!
}

/** Pen lookup by id of the timed text parser (atab.c). */
internal val captionPenLookupMethod = findMethodDirect {
    TimedTextSegmentElementFingerprint.instructionMatches[2].instruction.methodRef!!
}

/** Cronet url request builder call of the Timed Text request (CronetEngine.newUrlRequestBuilder). */
@get:AllowAbstract // The call site references the CronetEngine interface method.
internal val cronetUrlRequestBuilderMethod = findMethodDirect {
    TimedTextUrlFingerprint.instructionMatches[0].instruction.methodRef!!
}

/** isPipActive field of the player overlay state (juq.d). */
internal val playerOverlayPipField = findFieldDirect {
    PlayerOverlayStateToStringFingerprint.instructionMatches[3].instruction.fieldRef!!
}

/** Name method of the captions overlay (ouq.mz). */
internal val captionsOverlayNameMethod = findMethodDirect {
    CaptionsOverlayNameFingerprint()
}

/** Overlay visibility method of the player overlays (jur.lk), asked with the player state. */
@get:SkipTest
internal val overlayVisibilityMethod = findMethodDirect {
    PlayerOverlayVisibilityFingerprint.matchAll().first().instructionMatches[1].instruction.methodRef!!
}

/** Concrete implementations of the overlay visibility ABI, including overlay wrappers. */
internal val overlayVisibilityImplementationMethods = findMethodListDirect {
    val abi = overlayVisibilityMethod()
    findMethod {
        matcher {
            name = abi.name
            paramCount = 1
            paramTypes(abi.paramTypes[0].name)
            returnType = "boolean"
        }
    }.filter { !java.lang.reflect.Modifier.isAbstract(it.modifiers) }
}

/** The methods that create the player state and ask an overlay if it is shown. */
@get:SkipTest
internal val overlayVisibilityCallers = findMethodListDirect {
    PlayerOverlayVisibilityFingerprint.matchAll().map { it.method }
}

/** Current caption track field of the caption overlay presenter (asdf.h). */
internal val captionPresenterTrackField = findFieldDirect {
    CaptionTrackEventFingerprint().declaredClass!!.fields.single {
        it.type.descriptor == LanguageCaptionTrackIsDisableOptionFingerprint().declaredClass!!.descriptor
    }
}

/** Caption track setter of the caption overlay presenter (asdf.p). */
internal val captionEventSetTrackMethod = findMethodDirect {
    CaptionTrackEventFingerprint.instructionMatches[1].instruction.methodRef!!
}

/** Header setup of an engagement panel (aksd.a). */
internal val engagementPanelHeaderMethod = findMethodDirect {
    EngagementPanelHeaderFingerprint()
}
