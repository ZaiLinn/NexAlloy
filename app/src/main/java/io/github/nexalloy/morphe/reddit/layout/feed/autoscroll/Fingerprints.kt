/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4958
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.nexalloy.morphe.reddit.layout.feed.autoscroll

import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.ResourceType
import io.github.nexalloy.morphe.resourceLiteral
import io.github.nexalloy.morphe.string
import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Opcode

internal object FeedContextToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("FeedContext(onEvent="),
        string(", feedVisibilityFlow="),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            location = MatchAfterWithin(3),
        ),
    )
)

/**
 * Composable that shows a feed as a staggered grid, with the feed context as the second parameter.
 */
@StrictQuery
internal object GridFeedFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/feeds/ui/composables/feed/",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    filters = listOf(
        // rememberLazyStaggeredGridState()
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("I", "I", "L"),
            returnType = "Landroidx/compose/foundation/lazy/staggeredgrid/"
        ),
        opcode(
            opcode = Opcode.MOVE_RESULT_OBJECT,
            location = MatchAfterImmediately()
        )
    )
)

internal object FeedVisibilityEventFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/feeds/impl/ui/events/FeedVisibilityEvent;",
    name = "<init>",
    parameters = listOf("Lcom/reddit/feeds/ui/FeedVisibility;")
)

internal object MainActivityOnResumeFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/launch/main/MainActivity;",
    name = "onResume",
    returnType = "V",
    parameters = listOf()
)

internal object MainActivityOnPauseFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/launch/main/MainActivity;",
    name = "onPause",
    returnType = "V",
    parameters = listOf()
)

/**
 * Inflates the view of the post detail pager.
 */
@StrictQuery
internal object PostDetailPagerInflateFingerprint : Fingerprint(
    filters = listOf(
        resourceLiteral(ResourceType.LAYOUT, "fragment_pager"),
        methodCall(
            definingClass = "Landroid/view/LayoutInflater;",
            name = "inflate"
        ),
        opcode(
            opcode = Opcode.MOVE_RESULT_OBJECT,
            location = MatchAfterImmediately()
        )
    )
)

/**
 * Attached callback of a screen in the navigation stack.
 */
@StrictQuery
internal object ScreenAttachFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/navstack/",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
    filters = listOf(
        fieldAccess(definingClass = "Landroidx/lifecycle/Lifecycle\$State;", name = "DESTROYED"),
        fieldAccess(definingClass = "Landroidx/lifecycle/Lifecycle\$State;", name = "RESUMED")
    )
)

/**
 * Detached callback of a screen in the navigation stack.
 */
@StrictQuery
internal object ScreenDetachFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/navstack/",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
    filters = listOf(
        fieldAccess(definingClass = "Landroidx/lifecycle/Lifecycle\$State;", name = "RESUMED"),
        methodCall(definingClass = "Landroidx/lifecycle/Lifecycle\$State;", name = "isAtLeast"),
        fieldAccess(definingClass = "Landroidx/lifecycle/Lifecycle\$State;", name = "STARTED")
    )
)

@StrictQuery
internal object LatestFeedScreenFingerprint : Fingerprint(
    filters = listOf(
        string("latest_screen_surface"),
        methodCall(
            opcode = Opcode.INVOKE_STATIC_RANGE,
            definingClass = "Lcom/reddit/feeds/ui/composables/feed/",
            returnType = "V"
        )
    )
)

internal val feedVisibilityFlowField = findFieldDirect {
    FeedContextToStringFingerprint.instructionMatches.last().instruction.fieldRef!!
}

internal val listFeedComposable = findMethodDirect {
    LatestFeedScreenFingerprint.instructionMatches.last().instruction.methodRef!!
}

internal val gridStateCreator = findMethodDirect {
    GridFeedFingerprint.instructionMatches.first().instruction.methodRef!!
}

internal val feedPostDetailInflater = findMethodDirect {
    PostDetailPagerInflateFingerprint.instructionMatches[1].instruction.methodRef!!
}
