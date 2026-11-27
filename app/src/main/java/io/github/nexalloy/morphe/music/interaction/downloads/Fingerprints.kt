package io.github.nexalloy.morphe.music.interaction.downloads

import io.github.nexalloy.SkipTest
import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.ResourceType
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.anyInstruction
import io.github.nexalloy.morphe.checkCast
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findFieldListDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.findMethodListDirect
import io.github.nexalloy.morphe.literal
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.resourceLiteral
import io.github.nexalloy.morphe.string
import java.lang.reflect.Modifier
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.ClassData

/**
 * @return The default instance field of a generated protobuf message.
 */
context(bridge: DexKitBridge)
private fun defaultInstance(type: ClassData) = type.fields.single {
    Modifier.isStatic(it.modifiers) && Modifier.isFinal(it.modifiers) && it.type.descriptor == type.descriptor
}

// region Command resolution.

/**
 * Resolves a command with the command mappings that support it. Anchor (9.46.51): `vxv.c`
 * logs "Unknown command not resolved %s"; the `vxw` router logs a colon variant that
 * does not match, and the MusicCommandRouter logs the exception instead.
 */
internal object MusicResolveCommandFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "Ljava/util/Map;"),
    filters = listOf(
        anyInstruction(
            string("Unknown command not resolved"),
            string("Unknown command not resolved %s")
        )
    ),
    custom = {
        addAnyOf { addEqString("Unknown command not resolved") }
        addAnyOf { addEqString("Unknown command not resolved %s") }
    }
)

internal val musicOfflineCommandDefaultInstance = findFieldDirect {
    defaultInstance(MusicResolveCommandFingerprint().paramTypes.first())
}

// endregion

// region Offline video and playlist endpoints.

/**
 * Resolves the offline video endpoint. Anchor (9.46.51): `gxw.c` logs
 * "Object is not an offlineable video: %s" and saves with the matched call.
 */
internal object MusicOfflineVideoEndpointFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "Ljava/util/Map;"),
    strings = listOf("Object is not an offlineable video: %s"),
    filters = listOf(
        methodCall(
            opcodes = listOf(Opcode.INVOKE_INTERFACE, Opcode.INVOKE_VIRTUAL),
            parameters = listOf("Ljava/lang/String;", "L", "L", "L"),
            returnType = "V"
        )
    )
)

/**
 * Resolves the offline playlist endpoint. Anchor (9.46.51): `gxv.c` logs
 * "Object is not an offlineable playlist: %s".
 */
internal object MusicOfflinePlaylistEndpointFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "Ljava/util/Map;"),
    strings = listOf("Object is not an offlineable playlist: %s")
)

/**
 * Executes the offline playlist endpoint (9.46.51: `gxv.d`), which saves the playlist
 * with the offlineability renderer of the second parameter of the matched call.
 */
internal object MusicSaveOfflinePlaylistCallFingerprint : Fingerprint(
    classFingerprint = MusicOfflinePlaylistEndpointFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "Ljava/util/Map;", "L"),
    filters = listOf(
        methodCall(
            opcodes = listOf(Opcode.INVOKE_INTERFACE_RANGE, Opcode.INVOKE_VIRTUAL_RANGE),
            parameters = listOf("Ljava/lang/String;", "L", "L", "L", "L"),
            returnType = "V"
        )
    )
)

internal val musicSaveOfflineVideoMethod = findMethodDirect {
    MusicOfflineVideoEndpointFingerprint.instructionMatches.first().instruction.methodRef!!
}

internal val musicSaveOfflinePlaylistMethod = findMethodDirect {
    MusicSaveOfflinePlaylistCallFingerprint.instructionMatches.first().instruction.methodRef!!
}

internal val musicOfflineabilityDefaultInstance = findFieldDirect {
    defaultInstance(musicSaveOfflineVideoMethod().paramTypes[1])
}

// endregion

// region Offline upsell endpoint.

/**
 * Response callback of the 'ypc/get_offline_upsell' endpoint. The dex class is the anonymous
 * callback the endpoint creates (9.46.51: jadx shows it inline in `hav` as "from class: hau"):
 * the response is checked-cast, its case is the 131899791 upsell renderer, the captured
 * command field of the callback is read, and the interaction logger string is used to resolve it.
 */
internal object MusicOfflineUpsellResponseFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        checkCast("L"), // Response.
        literal(131899791), // Upsell renderer of the response.
        fieldAccess(opcode = Opcode.IGET_OBJECT, definingClass = "this"), // Endpoint command.
        string("com.google.android.libraries.youtube.logging.interaction_logger")
    )
)

internal val musicOfflineUpsellCommandField = findFieldDirect {
    MusicOfflineUpsellResponseFingerprint.instructionMatches[2].instruction.fieldRef!!
}

internal val musicOfflineUpsellDefaultInstance = findFieldDirect {
    defaultInstance(MusicOfflineUpsellResponseFingerprint.instructionMatches.first().instruction.classRef!!)
}

// endregion

// region Offline player response.

/**
 * Reads the player response a song is saved with from the cache, or fetches it and caches it.
 * Anchor (9.46.51): `abwd.e` logs "Some error occurred when reading from the cache player response".
 */
internal object MusicOfflinePlayerResponseFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf("L"),
    filters = listOf(
        string("Some error occurred when reading from the cache player response"),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            parameters = listOf("L"),
            returnType = "L"
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately())
    )
)

/**
 * Creates a player response model from the bytes of a player response.
 * Anchor (9.46.51): `wkn.aj` parses the bytes with the default instance of the player response
 * and returns a model of the type the response cache reads return. The other model factory
 * of `wkn` parses the same way, but its transport validation casts the player response only
 * after it built its result, so the cast right after the parse tells them apart.
 */
@StrictQuery
internal object MusicPlayerResponseModelFromBytesFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = { MusicOfflinePlayerResponseFingerprint().returnType!!.descriptor },
    parameters = listOf("[B", "J"),
    filters = listOf(
        methodCall(
            parameters = listOf("[B", "Lcom/google/protobuf/MessageLite;"),
            returnType = "Lcom/google/protobuf/MessageLite;"
        ),
        // Player response, cast right after the parse.
        opcode(Opcode.CHECK_CAST, location = MatchAfterWithin(3))
    )
)

/**
 * Getter of the player response of a player response model (9.46.51: `wkn.u`, declared
 * by the model interface the response cache returns). Must be used with the class of
 * [MusicPlayerResponseModelFromBytesFingerprint].
 */
internal object MusicPlayerResponseGetterFingerprint : Fingerprint(
    definingClass = { MusicPlayerResponseModelFromBytesFingerprint().declaredClass!!.descriptor },
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = {
        MusicPlayerResponseModelFromBytesFingerprint.instructionMatches.last().instruction.classRef!!.descriptor
    },
    parameters = { emptyList<CharSequence>() }
)

// endregion

// region Offline policy refreshes.

/**
 * Updates the offline state of a saved song with the state of the 'player/refresh' response.
 * Anchor (9.46.51): `aaoh.d` and `aats.g` log "[Offline] UpdateVideoPolicy failed for video %s".
 * Matches two methods, one for each of the two ways the app refreshes saved songs.
 */
@SkipTest
internal object MusicUpdateOfflineVideoPolicyFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        "L",
        "J", // Time of the refresh.
        "L", // Offline state.
        "L" // Video policy.
    ),
    filters = listOf(
        anyInstruction(
            string("[Offline] UpdateVideoPolicy failed for video "),
            string("[Offline] UpdateVideoPolicy failed for video %s")
        )
    ),
    custom = {
        addAnyOf { addEqString("[Offline] UpdateVideoPolicy failed for video ") }
        addAnyOf { addEqString("[Offline] UpdateVideoPolicy failed for video %s") }
    }
)

internal val musicUpdateOfflineVideoPolicyMethods = findMethodListDirect {
    MusicUpdateOfflineVideoPolicyFingerprint.matchAll(2..2).map { it.method }
}

internal val musicOfflineStateDefaultInstance = findFieldDirect {
    defaultInstance(musicUpdateOfflineVideoPolicyMethods().first().paramTypes[2])
}

// endregion

// region Download quality setting.

/**
 * Builds the entries of the media quality setting from the qualities the app offers.
 * Anchor (9.46.51): `OfflineSettingsFragmentCompat.onCreatePreferences` reads the two
 * static lists of format types of the setting and takes the size of the chosen one.
 * The list with 720p is declared as java.util.List, the other as its ImmutableList type.
 */
@StrictQuery
internal object MusicDownloadQualitySettingFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/apps/youtube/music/settings/fragment/OfflineSettingsFragmentCompat;",
    name = "onCreatePreferences",
    filters = listOf(
        // Qualities with 720p.
        fieldAccess(opcode = Opcode.SGET_OBJECT, type = "Ljava/util/List;"),
        // Qualities without 720p.
        fieldAccess(opcode = Opcode.SGET_OBJECT, location = MatchAfterWithin(5)),
        methodCall(smali = "Ljava/util/List;->size()I", location = MatchAfterImmediately())
    )
)

/**
 * Maps the format types to their heights and to the labels of the quality setting.
 * Anchor (9.46.51): `aayz.<clinit>` reads "offline_video_quality_1080p".
 */
internal object MusicFormatTypeMapsFingerprint : Fingerprint(
    name = "<clinit>",
    filters = listOf(
        resourceLiteral(ResourceType.STRING, "offline_video_quality_1080p")
    )
)

/**
 * Label of a format type in the quality setting, or -1 if it has none (9.46.51: `aayz.b`).
 */
internal object MusicDownloadQualityLabelFingerprint : Fingerprint(
    classFingerprint = MusicFormatTypeMapsFingerprint,
    returnType = "I",
    parameters = listOf("L")
)

/**
 * Height of the quality of a format type (9.46.51: `aayz.a`).
 */
internal object MusicOfflineQualityValueFingerprint : Fingerprint(
    classFingerprint = MusicFormatTypeMapsFingerprint,
    returnType = "I",
    parameters = listOf("L", "I")
)

/**
 * The offline settings keep only the download qualities up to the height of the screen.
 * Anchor (9.46.51): `aauj.<init>`, the shared DefaultOfflineSettings, copies the format
 * types of the heights map into an immutable list.
 */
internal object MusicOfflineSettingsConstructorFingerprint : Fingerprint(
    name = "<init>",
    parameters = listOf(
        "Landroid/content/SharedPreferences;",
        "L",
        "I", // Height of the highest download quality.
        "L",
        "L"
    ),
    filters = listOf(
        // Format types of the heights map.
        methodCall(smali = "Ljava/util/Map;->keySet()Ljava/util/Set;"),
        // Copies the format types into an immutable list.
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("Ljava/util/Collection;")
        )
    )
)

// endregion

// region Download stream picking.

/**
 * Picks the video and audio streams when a download starts, and pairs them.
 * Anchor (9.46.51): `abdx.i` reads the 45738203 feature flag, picks the streams with the
 * matched interface call and pairs them with the matched constructor call.
 */
internal object MusicDownloadStreamPairFingerprint : Fingerprint(
    returnType = "L",
    parameters = listOf(
        "L", // Streaming data.
        "L", // Player config.
        "I", // Height of the quality.
        "I", // Max height.
        "L", // Audio quality.
        "Z", // Audio only.
        "Ljava/lang/String;" // Audio track.
    ),
    filters = listOf(
        literal(45738203L),
        // Picks the streams.
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            parameters = listOf("L", "L", "L")
        ),
        // Pairs the video stream with the audio stream.
        methodCall(
            opcode = Opcode.INVOKE_DIRECT,
            name = "<init>",
            parameters = listOf("L", "L"),
            location = MatchAfterWithin(30)
        )
    )
)

internal val musicDownloadStreamPairConstructor = findMethodDirect {
    MusicDownloadStreamPairFingerprint.instructionMatches.last().instruction.methodRef!!
}

/**
 * Video field and audio field of the stream pair, in the order the constructor writes them.
 */
internal val musicDownloadStreamPairFields = findFieldListDirect {
    musicDownloadStreamPairConstructor().instructions.filter {
        it.opcode == Opcode.IPUT_OBJECT.opCode
    }.mapNotNull { it.fieldRef }.also { check(it.size == 2) }
}

/**
 * The streaming data of a video. Anchor (9.46.51): `wkf` prints "VideoStreamingData(itags=".
 */
internal object MusicStreamingDataToStringFingerprint : Fingerprint(
    name = "toString",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string("VideoStreamingData(itags=")
    )
)

/**
 * Constructor of the streaming data, which first sets the list of every stream of the video.
 */
internal object MusicStreamingDataConstructorFingerprint : Fingerprint(
    classFingerprint = MusicStreamingDataToStringFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = "Ljava/util/List;"
        )
    )
)

internal val musicStreamingDataFormatStreamsField = findFieldDirect {
    MusicStreamingDataConstructorFingerprint.instructionMatches.first().instruction.fieldRef!!
}

// endregion

// region Menus of saved songs and videos.

/**
 * Layout of the menu bottom sheet of songs and videos.
 * Anchor (9.46.51): `jcr.k` returns the "music_menu_bottom_sheet_dialog_fragment" layout.
 */
internal object MusicMenuBottomSheetLayoutFingerprint : Fingerprint(
    returnType = "I",
    parameters = listOf(),
    filters = listOf(
        resourceLiteral(ResourceType.LAYOUT, "music_menu_bottom_sheet_dialog_fragment")
    )
)

/**
 * Creates the items of the menu bottom sheet of songs and videos, such as the menus of the
 * downloads list, from the items of the menu renderer. Anchor (9.46.51): `jcr.l` reads the
 * menu renderer field, iterates its items, casts them to the menu item renderer and adds
 * them to the bottom sheet.
 */
internal object MusicMenuBottomSheetItemsFingerprint : Fingerprint(
    classFingerprint = MusicMenuBottomSheetLayoutFingerprint,
    accessFlags = listOf(AccessFlags.PROTECTED, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf(),
    filters = listOf(
        // Menu renderer of the bottom sheet.
        fieldAccess(opcode = Opcode.IGET_OBJECT, type = "Ljava/lang/Object;"),
        methodCall(smali = "Ljava/util/List;->iterator()Ljava/util/Iterator;"),
        methodCall(smali = "Ljava/util/Iterator;->next()Ljava/lang/Object;"),
        // Menu item renderer.
        opcode(Opcode.CHECK_CAST, location = MatchAfterWithin(3)),
        // Adds it to the items of the bottom sheet.
        methodCall(
            name = "add",
            parameters = listOf("Ljava/lang/Object;"),
            returnType = "Z",
            location = MatchAfterImmediately()
        )
    )
)

internal val musicMenuBottomSheetMenuField = findFieldDirect {
    MusicMenuBottomSheetItemsFingerprint.instructionMatches.first().instruction.fieldRef!!
}

internal val musicMenuItemDefaultInstance = findFieldDirect {
    defaultInstance(MusicMenuBottomSheetItemsFingerprint.instructionMatches[3].instruction.classRef!!)
}

/**
 * Menu item view holder. Anchor (9.46.51): `jfp.e` reads "toggleMenuItemMutations"
 * and adds the item view to the menu item container.
 */
internal object MusicMenuItemFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        string("toggleMenuItemMutations"),
        // Menu item container the item view is added to.
        fieldAccess(opcode = Opcode.IGET_OBJECT, type = "Landroid/widget/FrameLayout;")
    )
)

/**
 * Click of an item of a native menu (9.46.51: `jfp.onClick`).
 */
internal object MusicMenuItemClickFingerprint : Fingerprint(
    classFingerprint = MusicMenuItemFingerprint,
    name = "onClick",
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
    filters = listOf(
        // Menu item renderer of the item.
        fieldAccess(definingClass = "this", opcode = Opcode.IGET_OBJECT)
    )
)

internal val musicMenuItemField = findFieldDirect {
    MusicMenuItemClickFingerprint.instructionMatches.first().instruction.fieldRef!!
}

// endregion

// region Protobuf parsing.

/**
 * The module and the host use separate protobuf implementations, so the host parses
 * the messages the extension builds. Can match more than one method, so its only use
 * is to find the class of [MusicProtobufParseFromWithRegistryFingerprint].
 */
@SkipTest
internal object MusicProtobufParseByteArrayFingerprint : Fingerprint(
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

internal object MusicProtobufParseFromWithRegistryFingerprint : Fingerprint(
    classFingerprint = MusicProtobufParseByteArrayFingerprint,
    name = "parseFrom",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "L",
    parameters = listOf("L", "[B", "Lcom/google/protobuf/ExtensionRegistryLite;")
)

internal object MusicGeneratedRegistryFingerprint : Fingerprint(
    definingClass = "Lcom/google/protobuf/ExtensionRegistryLite;",
    name = "getGeneratedRegistry",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Lcom/google/protobuf/ExtensionRegistryLite;",
    parameters = emptyList()
)

// endregion
