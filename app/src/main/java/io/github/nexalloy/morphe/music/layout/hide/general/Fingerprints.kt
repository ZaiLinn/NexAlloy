/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Runtime port of the upstream patcher fingerprints
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/music/layout/hide/general/Fingerprints.kt).
 */

package io.github.nexalloy.morphe.music.layout.hide.general

import io.github.nexalloy.AllowAbstract
import io.github.nexalloy.SkipTest
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.ResourceType
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.checkCast
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.findMethodListDirect
import io.github.nexalloy.morphe.literal
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.resourceLiteral

@SkipTest
internal object AudioVideoSwitchPillContainerFingerprint : Fingerprint(
    filters = listOf(
        resourceLiteral(ResourceType.ID, "audio_video_switch_pill_container"),
        methodCall(name = "findViewById", opcode = Opcode.INVOKE_VIRTUAL),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately())
    )
)

internal object ChipCloudFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        resourceLiteral(ResourceType.LAYOUT, "chip_cloud"),
        opcode(Opcode.CONST_4, location = MatchAfterImmediately()),
        opcode(Opcode.INVOKE_STATIC, location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately())
    )
)

internal object InformationButtonFingerprint : Fingerprint(
    parameters = listOf("Landroid/content/Context;"),
    filters = listOf(
        resourceLiteral(ResourceType.ID, "information_button"),
        opcode(Opcode.CHECK_CAST)
    )
)

@SkipTest
internal object OverlayQueueLoopButtonFingerprint : Fingerprint(
    filters = listOf(
        resourceLiteral(ResourceType.ID, "overlay_queue_loop_button_view"),
        methodCall(name = "findViewById", opcode = Opcode.INVOKE_VIRTUAL),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately())
    )
)

@SkipTest
internal object OverlayQueueShuffleButtonFingerprint : Fingerprint(
    filters = listOf(
        resourceLiteral(ResourceType.ID, "overlay_queue_shuffle_button_view"),
        methodCall(name = "findViewById", opcode = Opcode.INVOKE_VIRTUAL),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately())
    )
)

@SkipTest
internal object PlaybackQueueLoopButtonFingerprint : Fingerprint(
    filters = listOf(
        resourceLiteral(ResourceType.ID, "playback_queue_loop_button_view"),
        methodCall(name = "findViewById", opcode = Opcode.INVOKE_VIRTUAL),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately())
    )
)

@SkipTest
internal object PlaybackQueueShuffleButtonFingerprint : Fingerprint(
    filters = listOf(
        resourceLiteral(ResourceType.ID, "playback_queue_shuffle_button_view"),
        methodCall(name = "findViewById", opcode = Opcode.INVOKE_VIRTUAL),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately())
    )
)

/**
 * Renders the search suggestions response of the search page. Before a query is typed, the
 * response holds the search history shelves, the entry point buttons and the 'You may also like'
 * shelf. Matches the section list being passed to the list controller, right before the loading
 * spinner is updated.
 */
internal object SearchSuggestionsRenderFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L"),
    strings = listOf(
        "renderSuggestions",
        "Search Suggestions Response has Section List but also more than one renderer."
    ),
    filters = listOf(
        opcode(Opcode.NEW_INSTANCE),
        methodCall(
            name = "<init>",
            parameters = listOf("L"),
            opcode = Opcode.INVOKE_DIRECT,
            location = MatchAfterImmediately()
        ),
        methodCall(
            returnType = "V",
            parameters = listOf("L"),
            opcode = Opcode.INVOKE_VIRTUAL,
            location = MatchAfterImmediately()
        ),
        fieldAccess(
            type = "Lcom/google/android/apps/youtube/music/ui/components/loading/LoadingFrameLayout;",
            opcode = Opcode.IGET_OBJECT,
            location = MatchAfterImmediately()
        )
    )
)

/**
 * Converts a section list item to its renderer. It is in the section list wrapper,
 * which [SearchSuggestionsRenderFingerprint] creates. The parameter is the section list item.
 */
internal object SectionListItemRendererFingerprint : Fingerprint(
    definingClass = {
        SearchSuggestionsRenderFingerprint.instructionMatches[1].instruction.methodRef!!
            .declaredClass!!.descriptor
    },
    returnType = { "Lj$/util/Optional;" },
    parameters = { listOf("L") }
)

/**
 * A bit of the music shelf item bit field set when the item is a search history entry.
 */
private const val MUSIC_SHELF_ITEM_SEARCH_HISTORY_BIT = 0x8000000L

/**
 * A bit of the music shelf item bit field set when the item is a suggested search.
 */
private const val MUSIC_SHELF_ITEM_SUGGESTED_SEARCH_BIT = 0x4000000L

/**
 * Returns the renderers of a music shelf. Each shelf item holds one renderer,
 * selected by a bit of the item bit field.
 */
internal object MusicShelfItemsFingerprint : Fingerprint(
    returnType = "Ljava/util/List;",
    parameters = listOf("L"),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT),
        literal(MUSIC_SHELF_ITEM_SEARCH_HISTORY_BIT),
        fieldAccess(opcode = Opcode.IGET_OBJECT, location = MatchAfterWithin(6)),
        literal(MUSIC_SHELF_ITEM_SUGGESTED_SEARCH_BIT),
        fieldAccess(opcode = Opcode.IGET_OBJECT, location = MatchAfterWithin(6))
    )
)

/**
 * Copies a proto list into a mutable list with the given capacity.
 * The list is the type of the shelf items field that [MusicShelfItemsFingerprint] reads first.
 */
@AllowAbstract
internal object ProtoListMutableCopyFingerprint : Fingerprint(
    definingClass = {
        MusicShelfItemsFingerprint.instructionMatches.first().instruction.fieldRef!!
            .type!!.descriptor
    },
    returnType = {
        MusicShelfItemsFingerprint.instructionMatches.first().instruction.fieldRef!!
            .type!!.descriptor
    },
    parameters = { listOf("I") }
)

/**
 * The Library tab binds its floating New button as an extended button. Other fragments bind
 * a view with the same id as a different class, so the cast tells the Library button apart.
 */
@StrictQuery
internal object NewButtonFingerprint : Fingerprint(
    name = "onCreateView",
    filters = listOf(
        resourceLiteral(ResourceType.ID, "floating_action_button"),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterWithin(3)),
        checkCast(
            "Lcom/google/android/material/floatingactionbutton/ExtendedFloatingActionButton;",
            location = MatchAfterImmediately()
        )
    )
)

// region Runtime-resolved members
// The upstream patch injects the hide calls at the matched instructions; the runtime port
// hooks the matched methods and dispatches to the same extension entry points.

/** The methods that look the audio/video switch pill container up, one per view owner. */
internal val audioVideoSwitchPillMethods = findMethodListDirect {
    AudioVideoSwitchPillContainerFingerprint.matchAll().map { it.method }
}

/** The method that binds the comments information button. */
internal val informationButtonMethod = findMethodDirect {
    InformationButtonFingerprint.match().method
}

/** The methods that look the loop button of the overlay and the playback queue up. */
internal val overlayQueueLoopButtonMethods = findMethodListDirect {
    OverlayQueueLoopButtonFingerprint.matchAllOrNull().orEmpty().map { it.method }
}

internal val playbackQueueLoopButtonMethods = findMethodListDirect {
    PlaybackQueueLoopButtonFingerprint.matchAllOrNull().orEmpty().map { it.method }
}

/** The methods that look the shuffle button of the overlay and the playback queue up. */
internal val overlayQueueShuffleButtonMethods = findMethodListDirect {
    OverlayQueueShuffleButtonFingerprint.matchAllOrNull().orEmpty().map { it.method }
}

internal val playbackQueueShuffleButtonMethods = findMethodListDirect {
    PlaybackQueueShuffleButtonFingerprint.matchAllOrNull().orEmpty().map { it.method }
}

/**
 * The constructor of the section list wrapper the search suggestions render method creates.
 * The upstream patch injects the shelf removal right before the wrapper is created; the
 * runtime port hooks this constructor inside a hook of the render method.
 */
internal val searchSuggestionsWrapperConstructor = findMethodDirect {
    SearchSuggestionsRenderFingerprint.instructionMatches[1].instruction.methodRef!!
}

/**
 * The shelf items list field of a music shelf, and the renderer fields each shelf item
 * holds one of, selected by a bit of the item bit field.
 */
internal val musicShelfItemsField = findFieldDirect {
    MusicShelfItemsFingerprint.instructionMatches.first().instruction.fieldRef!!
}

internal val musicShelfSearchHistoryField = findFieldDirect {
    MusicShelfItemsFingerprint.instructionMatches[2].instruction.fieldRef!!
}

internal val musicShelfSuggestedSearchField = findFieldDirect {
    MusicShelfItemsFingerprint.instructionMatches[4].instruction.fieldRef!!
}

/**
 * The static helper that inflates the chip cloud layout of the filter bar. The upstream patch
 * passes the result of this call to the hide call of the filter bar.
 */
internal val chipCloudInflateHelperMethod = findMethodDirect {
    ChipCloudFingerprint.instructionMatches[2].instruction.methodRef!!
}

// endregion
