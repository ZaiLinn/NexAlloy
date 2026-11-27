package io.github.nexalloy.morphe.youtube.layout.captions.core

import io.github.nexalloy.RequireAppVersion
import io.github.nexalloy.AllowAbstract
import io.github.nexalloy.SkipTest
import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterAnywhere
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.InstructionLocation.MatchFirst
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.ResourceType
import io.github.nexalloy.morphe.StringComparisonType
import io.github.nexalloy.morphe.anyInstruction
import io.github.nexalloy.morphe.checkCast
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findClassDirect
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.findMethodListDirect
import io.github.nexalloy.morphe.literal
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.newInstance
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.resourceLiteral
import io.github.nexalloy.morphe.string

/**
 * Runtime port of the captions core fingerprints
 * (upstream: app.morphe.patches.youtube.layout.captions.Fingerprints).
 *
 * Only the fingerprints required by the ported core patches are declared here.
 */

internal const val SUBTITLE_WINDOW_VIEW_CLASS =
    "Lcom/google/android/libraries/youtube/player/subtitles/ui/SubtitleWindowView;"

// =============================================================================================
// Captions after seek
// =============================================================================================

/**
 * Cue range manager seek. Its class also adds and removes cue ranges.
 */
internal object CueRangeManagerSeekFingerprint : Fingerprint(
    returnType = "J",
    parameters = listOf("J", "J", "Z"),
    filters = listOf(
        string("CueRangeManager", StringComparisonType.CONTAINS)
    )
)

/**
 * Server flag to start cue ranges that are added while the video time is inside them.
 */
internal object EnterCueRangeOnAddFeatureFlagFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(
        literal(45415907L)
    )
)

/**
 * Adds a list of cue ranges. Matches at the cast of each cue range and the flag check.
 */
internal object AddCueRangesFingerprint : Fingerprint(
    classFingerprint = CueRangeManagerSeekFingerprint,
    returnType = "V",
    parameters = listOf("Ljava/util/List;"),
    filters = listOf(
        methodCall(smali = "Ljava/util/Iterator;->next()Ljava/lang/Object;"),
        opcode(Opcode.CHECK_CAST, MatchAfterWithin(2)),
        methodCall(reference = { EnterCueRangeOnAddFeatureFlagFingerprint.run() }),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately())
    )
)

/**
 * Captions of a fully loaded caption track.
 * Matches at the field of the sorted times the captions change at.
 */
internal object CaptionWindowsConstructorFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    parameters = listOf("Ljava/util/List;"),
    filters = listOf(
        newInstance("Ljava/util/TreeSet;"),
        methodCall(smali = "Ljava/util/TreeSet;->addAll(Ljava/util/Collection;)Z"),
        newInstance("Ljava/util/ArrayList;"),
        methodCall(smali = "Lj\$/util/DesugarCollections;->unmodifiableList(Ljava/util/List;)Ljava/util/List;"),
        opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
        fieldAccess(
            definingClass = "this",
            type = "Ljava/util/List;",
            opcode = Opcode.IPUT_OBJECT,
            location = MatchAfterImmediately()
        )
    )
)

/**
 * Caption chunk loader. Matches at the field of the captions it loads from.
 */
internal fun getCaptionChunkLoaderConstructorFingerprint(captionWindowsType: String) = Fingerprint(
    name = "<init>",
    returnType = "V",
    filters = listOf(
        newInstance("Ljava/util/TreeMap;"),
        fieldAccess(
            definingClass = "this",
            type = captionWindowsType,
            opcode = Opcode.IPUT_OBJECT
        )
    )
)

/**
 * Loads the caption cues that start in the chunk of a video time.
 */
internal fun getCaptionChunkLoaderFingerprint(loaderType: String) = Fingerprint(
    definingClass = loaderType,
    returnType = "V",
    parameters = listOf("J"),
    filters = listOf(
        methodCall(smali = "Ljava/util/concurrent/Executor;->execute(Ljava/lang/Runnable;)V")
    )
)

/**
 * Caption cue, a cue range with the caption windows it shows when it starts.
 */
internal fun getCaptionCueConstructorFingerprint(cueRangeType: String) = Fingerprint(
    superclass = cueRangeType,
    name = "<init>",
    returnType = "V",
    parameters = listOf("J", "J", "Ljava/util/List;", "L"),
    filters = listOf(
        methodCall(
            definingClass = cueRangeType,
            name = "<init>"
        )
    )
)

/** The sorted times the captions change at, of the loaded caption track. */
internal val captionChunkTimesField = findFieldDirect {
    CaptionWindowsConstructorFingerprint.instructionMatches.last().instruction.fieldRef!!
}

/** The caption chunk loader method that loads the cues starting in a chunk of a video time. */
internal val captionChunkLoaderMethod = findMethodDirect {
    val timelineType = CaptionWindowsConstructorFingerprint.run().declaredClass!!.descriptor
    val loaderType = getCaptionChunkLoaderConstructorFingerprint(timelineType)
        .match().method.declaredClass!!.descriptor
    getCaptionChunkLoaderFingerprint(loaderType).run()
}

/** The loaded caption track of the chunk loader. */
internal val captionChunkWindowsField = findFieldDirect {
    val timelineType = CaptionWindowsConstructorFingerprint.run().declaredClass!!.descriptor
    getCaptionChunkLoaderConstructorFingerprint(timelineType)
        .match().instructionMatches.last().instruction.fieldRef!!
}

internal val addCueRangesMethod = findMethodDirect {
    AddCueRangesFingerprint.run()
}

internal val enterCueRangeOnAddFlagMethod = findMethodDirect {
    EnterCueRangeOnAddFeatureFlagFingerprint.run()
}

/** The caption cue range class (a cue range with caption windows). */
internal val captionCueRangeClass = findClassDirect {
    val cueRangeType = AddCueRangesFingerprint.match().instructionMatches[1].instruction.classRef!!.descriptor
    getCaptionCueConstructorFingerprint(cueRangeType).run().declaredClass!!
}

// =============================================================================================
// Caption window measure, caption font size
// =============================================================================================

/**
 * The height measure spec branch incorrectly checks the width measure mode.
 */
internal object CaptionWindowMeasureFingerprint : Fingerprint(
    definingClass = SUBTITLE_WINDOW_VIEW_CLASS,
    name = "onMeasure",
    returnType = "V",
    parameters = listOf("I", "I"),
    filters = listOf(
        methodCall(smali = "Landroid/view/View\$MeasureSpec;->getMode(I)I"),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
        literal(0x40000000, location = MatchAfterImmediately()),
        methodCall(smali = "Landroid/view/View\$MeasureSpec;->getMode(I)I"),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
        // Height mode is AT_MOST.
        opcode(Opcode.IF_NE, MatchAfterImmediately()),
        // Width mode is EXACTLY.
        anyInstruction(
            opcode(Opcode.IF_EQ),
            opcode(Opcode.IF_NE),
            location = MatchAfterWithin(4)
        )
    )
)

internal val captionWindowMeasureMethod = findMethodDirect {
    CaptionWindowMeasureFingerprint.run()
}

/** Final font size setter shared by the native caption renderers. */
internal object CaptionWindowFontSizeFingerprint : Fingerprint(
    classFingerprint = CaptionWindowMeasureFingerprint,
    returnType = "V",
    parameters = listOf("F"),
    filters = listOf(
        methodCall(smali = "Ljava/lang/Math;->abs(F)F"),
        literal(0.01f),
        fieldAccess(
            definingClass = "this",
            type = "F",
            opcode = Opcode.IPUT
        )
    )
)

/**
 * Caption font size, calculated from the size of the subtitle overlay.
 */
@RequireAppVersion(minVersion = "21.10.000")
internal object CaptionFontSizeFingerprint : Fingerprint(
    returnType = "F",
    parameters = listOf("F", "I", "I"),
    filters = listOf(
        methodCall(smali = "Ljava/lang/Math;->min(II)I"),
        fieldAccess(smali = "Landroid/util/DisplayMetrics;->scaledDensity:F"),
        literal(0.0625f),
        literal(13.0f)
    )
)

// =============================================================================================
// Caption viewport
// =============================================================================================

internal val captionFontSizeMethod = findMethodDirect {
    CaptionFontSizeFingerprint.run()
}

internal val captionWindowFontSizeMethod = findMethodDirect {
    CaptionWindowFontSizeFingerprint.run()
}

// =============================================================================================
// Caption track capture
// =============================================================================================

/**
 * Native caption track handler, matched at the failure callback of its requests.
 */
@RequireAppVersion(minVersion = "21.15.000")
internal object CaptionTrackHandlerErrorFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Exception;"),
    filters = listOf(
        string("error retrieving subtitle")
    )
)

/**
 * Parses a cached caption track file.
 */
@RequireAppVersion(minVersion = "21.15.000")
internal object CaptionFileParserFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/Object;", "L"),
    filters = listOf(
        // Caption request.
        opcode(Opcode.CHECK_CAST, MatchFirst()),
        // Caption track of the request.
        fieldAccess(opcode = Opcode.IGET_OBJECT, location = MatchAfterWithin(2)),
        methodCall(smali = "Ljava/io/File;->exists()Z"),
        methodCall(smali = "Ljava/io/FileInputStream;-><init>(Ljava/io/File;)V"),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            parameters = listOf("Ljava/io/InputStream;", "L"),
            returnType = "Ljava/lang/Object;"
        )
    )
)

/**
 * Track selection of the native caption track handler, which creates the track request.
 *
 * @param requestClass Class of the track request, holding the caption track.
 * @param trackType Class of the caption track.
 */
@RequireAppVersion(minVersion = "21.15.000")
internal fun getCaptionTrackHandlerSelectFingerprint(requestClass: String, trackType: String) = Fingerprint(
    classFingerprint = CaptionTrackHandlerErrorFingerprint,
    returnType = "V",
    parameters = listOf(trackType),
    filters = listOf(
        // Loader of the track requests.
        fieldAccess(definingClass = "this", opcode = Opcode.IGET_OBJECT),
        methodCall(
            definingClass = requestClass,
            name = "<init>",
            parameters = listOf(trackType),
            location = MatchAfterWithin(4)
        ),
        // Request of the loader, with the request and its callback.
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            parameters = listOf(requestClass, "L"),
            returnType = "V",
            location = MatchAfterWithin(4)
        ),
        // Subtitle manager. The type is not restricted, it is taken from the matched field.
        fieldAccess(definingClass = "this", opcode = Opcode.IGET_OBJECT)
    )
)

/**
 * Result callback of the requests of the native caption track handler.
 */
@RequireAppVersion(minVersion = "21.15.000")
internal fun getCaptionTrackHandlerResultFingerprint(requestClass: String) = Fingerprint(
    classFingerprint = CaptionTrackHandlerErrorFingerprint,
    returnType = "V",
    parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;"),
    filters = listOf(
        checkCast(requestClass)
    )
)

/**
 * Updates the installed caption renderer with the playback time and speed.
 */
@RequireAppVersion(minVersion = "21.15.000")
internal object CaptionTrackHandlerTimeFingerprint : Fingerprint(
    classFingerprint = CaptionTrackHandlerErrorFingerprint,
    returnType = "V",
    parameters = listOf("J", "F"),
    filters = listOf(
        methodCall(parameters = listOf("J", "F"), returnType = "V")
    )
)

/**
 * Selected track event of the subtitle manager: stores the selected track after
 * the handler is notified. Anchors the selected track field.
 *
 * The upstream fingerprint (SubtitleManagerSelectedTrackFingerprint) anchors the field
 * in the selection reason method. At runtime the anchor resolves through the handler select
 * call that the upstream smali patch used for the reload helper.
 *
 * @param subtitleManagerType Class of the subtitle manager.
 * @param trackType Class of the caption track.
 */
@RequireAppVersion(minVersion = "21.15.000")
internal fun getSubtitleManagerSelectedTrackFingerprint(subtitleManagerType: String, trackType: String) = Fingerprint(
    definingClass = subtitleManagerType,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(trackType),
    filters = listOf(
        // The track selection handler is notified,
        methodCall(parameters = listOf(trackType), returnType = "V"),
        // and the selected track is stored.
        fieldAccess(
            definingClass = "this",
            type = trackType,
            opcode = Opcode.IPUT_OBJECT,
            location = MatchAfterImmediately()
        )
    )
)

/**
 * Resolves to the caption track class, matched at the disable option check.
 */
internal object CaptionTrackIsDisableOptionFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(
        string("DISABLE_CAPTIONS_OPTION"),
        // Language code.
        fieldAccess(
            definingClass = "this",
            type = "Ljava/lang/String;",
            opcode = Opcode.IGET_OBJECT,
            location = MatchAfterImmediately()
        ),
        methodCall(
            smali = "Ljava/lang/String;->equals(Ljava/lang/Object;)Z",
            location = MatchAfterImmediately()
        )
    )
)

/**
 * The vss id check of a caption track.
 */
internal object CaptionTrackIsAutoTranslatedFingerprint : Fingerprint(
    classFingerprint = CaptionTrackIsDisableOptionFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(
        // Vss id.
        fieldAccess(
            definingClass = "this",
            type = "Ljava/lang/String;",
            opcode = Opcode.IGET_OBJECT,
            location = MatchAfterAnywhere()
        ),
        string("t", location = MatchAfterImmediately()),
        methodCall(
            smali = "Ljava/lang/String;->startsWith(Ljava/lang/String;)Z",
            location = MatchAfterImmediately()
        )
    )
)

/**
 * Resolves to the caption tracks manager class.
 */
internal object CaptionTracksManagerDirectTracksFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/util/List;",
    parameters = listOf(),
    filters = listOf(
        string("AUTO_TRANSLATE_CAPTIONS_OPTION")
    )
)

internal object CaptionTracksManagerAutoTranslateTracksFingerprint : Fingerprint(
    classFingerprint = CaptionTracksManagerDirectTracksFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/util/List;",
    parameters = listOf(),
    filters = listOf(
        // Url of the source caption track.
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = "Ljava/lang/String;"
        ),
        methodCall(
            smali = "Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;",
            location = MatchAfterImmediately()
        ),
        string("&tlang=", location = MatchAfterImmediately())
    )
)

/**
 * Resolves to the caption track builder class.
 */
internal object CaptionTrackBuilderBuildFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf(),
    filters = listOf(
        // Display name.
        fieldAccess(
            definingClass = "this",
            opcode = Opcode.IGET_OBJECT,
            type = "Ljava/lang/CharSequence;"
        ),
        string(" languageCode"),
        string(" vssId"),
        string(" url")
    )
)

internal object CaptionTrackBuilderCopyConstructorFingerprint : Fingerprint(
    classFingerprint = CaptionTrackBuilderBuildFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    parameters = listOf("L"),
    filters = listOf(
        fieldAccess(
            definingClass = "this",
            opcode = Opcode.IPUT_OBJECT,
            type = "Ljava/lang/CharSequence;"
        )
    )
)

/**
 * @param nullValueMessage Exception message of the setter, such as "Null url".
 */
internal fun getCaptionTrackBuilderSetterFingerprint(nullValueMessage: String) = Fingerprint(
    classFingerprint = CaptionTrackBuilderBuildFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Ljava/lang/String;"),
    filters = listOf(
        string(nullValueMessage)
    )
)

/** The caption track request class, holding the requested caption track. */
internal val captionTrackRequestClass = findClassDirect {
    CaptionFileParserFingerprint.match().instructionMatches[1].instruction.fieldRef!!.declaredClass!!
}

/** The caption track field of the caption track request. */
internal val captionTrackRequestTrackField = findFieldDirect {
    CaptionFileParserFingerprint.match().instructionMatches[1].instruction.fieldRef!!
}

/** The caption track class. */
internal val captionTrackClass = findClassDirect {
    CaptionFileParserFingerprint.match().instructionMatches[1].instruction.fieldRef!!.type!!
}

/** Track selection method of the native caption track handler. */
internal val captionTrackHandlerSelectMethod = findMethodDirect {
    val requestTrack = CaptionFileParserFingerprint.match().instructionMatches[1].instruction.fieldRef!!
    getCaptionTrackHandlerSelectFingerprint(
        requestTrack.declaredClass!!.descriptor,
        requestTrack.type!!.descriptor
    ).run()
}

/** The loader field of the handler, and its request method. */
internal val captionTrackLoaderField = findFieldDirect {
    val requestTrack = CaptionFileParserFingerprint.match().instructionMatches[1].instruction.fieldRef!!
    getCaptionTrackHandlerSelectFingerprint(
        requestTrack.declaredClass!!.descriptor,
        requestTrack.type!!.descriptor
    ).match().instructionMatches[0].instruction.fieldRef!!
}

@get:AllowAbstract // Invoked on the loader instance; this member is its interface ABI.
internal val captionTrackLoaderRequestMethod = findMethodDirect {
    val requestTrack = CaptionFileParserFingerprint.match().instructionMatches[1].instruction.fieldRef!!
    getCaptionTrackHandlerSelectFingerprint(
        requestTrack.declaredClass!!.descriptor,
        requestTrack.type!!.descriptor
    ).match().instructionMatches[2].instruction.methodRef!!
}

/** The subtitle manager field of the handler. */
internal val captionTrackHandlerSubtitleManagerField = findFieldDirect {
    val requestTrack = CaptionFileParserFingerprint.match().instructionMatches[1].instruction.fieldRef!!
    getCaptionTrackHandlerSelectFingerprint(
        requestTrack.declaredClass!!.descriptor,
        requestTrack.type!!.descriptor
    ).match().instructionMatches[3].instruction.fieldRef!!
}

/** Result callback method of the requests of the native caption track handler. */
internal val captionTrackHandlerResultMethod = findMethodDirect {
    val requestClass = CaptionFileParserFingerprint.match().instructionMatches[1]
        .instruction.fieldRef!!.declaredClass!!.descriptor
    getCaptionTrackHandlerResultFingerprint(requestClass).run()
}

internal val captionTrackHandlerTimeMethod = findMethodDirect {
    CaptionTrackHandlerTimeFingerprint.run()
}

/** The track the subtitle manager has selected, of the subtitle manager. */
internal val subtitleManagerSelectedTrackField = findFieldDirect {
    val requestTrack = CaptionFileParserFingerprint.match().instructionMatches[1].instruction.fieldRef!!
    val subtitleManagerType = getCaptionTrackHandlerSelectFingerprint(
        requestTrack.declaredClass!!.descriptor,
        requestTrack.type!!.descriptor
    ).match().instructionMatches[3].instruction.fieldRef!!.type!!.descriptor
    getSubtitleManagerSelectedTrackFingerprint(subtitleManagerType, requestTrack.type!!.descriptor)
        .match().instructionMatches.last().instruction.fieldRef!!
}

// Caption track accessors.

internal val captionTrackLanguageCodeField = findFieldDirect {
    CaptionTrackIsDisableOptionFingerprint.match().instructionMatches[1].instruction.fieldRef!!
}

internal val captionTrackVssIdField = findFieldDirect {
    CaptionTrackIsAutoTranslatedFingerprint.match().instructionMatches[0].instruction.fieldRef!!
}

internal val captionTrackUrlField = findFieldDirect {
    CaptionTracksManagerAutoTranslateTracksFingerprint.match().instructionMatches[0].instruction.fieldRef!!
}

internal val captionTrackBuilderCopyConstructor = findMethodDirect {
    CaptionTrackBuilderCopyConstructorFingerprint.run()
}

internal val captionTrackBuilderBuildMethod = findMethodDirect {
    CaptionTrackBuilderBuildFingerprint.run()
}

internal val captionTrackBuilderLabelField = findFieldDirect {
    CaptionTrackBuilderBuildFingerprint.match().instructionMatches[0].instruction.fieldRef!!
}

internal val captionTrackBuilderSetLanguageCodeMethod = findMethodDirect {
    getCaptionTrackBuilderSetterFingerprint("Null languageCode").run()
}

internal val captionTrackBuilderSetUrlMethod = findMethodDirect {
    getCaptionTrackBuilderSetterFingerprint("Null url").run()
}

internal val captionTrackBuilderSetVssIdMethod = findMethodDirect {
    getCaptionTrackBuilderSetterFingerprint("Null vssId").run()
}

// =============================================================================================
// Stable caption blocks
// =============================================================================================

/**
 * Old captions bottom sheet, matched at its list view.
 * Matched at both bottom sheet fragments, so this is a designed multi-match.
 */
@SkipTest
internal object CaptionsBottomSheetListFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Landroid/view/View;",
    parameters = listOf(
        "Landroid/view/LayoutInflater;",
        "Landroid/view/ViewGroup;",
        "Landroid/os/Bundle;"
    ),
    filters = listOf(
        checkCast("Landroid/widget/ListView;"),
        resourceLiteral(ResourceType.STRING, "subtitle_menu_settings_footer_info")
    )
)

/**
 * The views that inflate the captions bottom sheets.
 */
internal val captionsBottomSheetListMethods = findMethodListDirect {
    CaptionsBottomSheetListFingerprint.matchAll(1..2).map { it.method }
}

/**
 * Returns the caption cues shown at a video time.
 */
internal fun getCaptionSamplerFingerprint(cueType: String) = Fingerprint(
    classFingerprint = CaptionWindowsConstructorFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/util/List;",
    parameters = listOf("J"),
    filters = listOf(
        fieldAccess(
            definingClass = "this",
            type = "Ljava/util/List;",
            opcode = Opcode.IGET_OBJECT
        ),
        newInstance(cueType),
        methodCall(definingClass = cueType, name = "<init>")
    )
)

/** The caption windows of the loaded caption track. */
internal val captionTimelineWindowsField = findFieldDirect {
    getCaptionSamplerFingerprint(CaptionCueToStringFingerprint.run().declaredClass!!.descriptor)
        .match().instructionMatches[0].instruction.fieldRef!!
}

/** The loaded caption track constructor. */
internal val captionTimelineConstructor = findMethodDirect {
    CaptionWindowsConstructorFingerprint.run()
}

/** The captions shown at a video time, of the loaded caption track. */
internal val captionTimelineSamplerMethod = findMethodDirect {
    getCaptionSamplerFingerprint(CaptionCueToStringFingerprint.run().declaredClass!!.descriptor).run()
}

/**
 * Caption cue of a subtitle window. Matches the id, time, text, full text and settings fields.
 */
internal object CaptionCueToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        fieldAccess(definingClass = "this", type = "I", opcode = Opcode.IGET),
        fieldAccess(definingClass = "this", type = "J", opcode = Opcode.IGET_WIDE),
        fieldAccess(
            definingClass = "this",
            type = "Ljava/lang/CharSequence;",
            opcode = Opcode.IGET_OBJECT
        ),
        fieldAccess(
            definingClass = "this",
            type = "Ljava/lang/CharSequence;",
            opcode = Opcode.IGET_OBJECT,
            location = MatchAfterImmediately()
        ),
        fieldAccess(
            definingClass = "this",
            opcode = Opcode.IGET_OBJECT,
            location = MatchAfterImmediately()
        ),
        string("id: %d time: %d text: %s fullText: %s settings: %s")
    )
)
