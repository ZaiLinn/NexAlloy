package io.github.nexalloy.morphe.reddit.layout.copytext

import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.literal
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.newInstance
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.string
import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Opcode

/**
 * Compose rich text renderer, used for post and comment bodies.
 */
internal object ComposeRichTextFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    filters = listOf(
        literal(1546229805L)
    )
)

/**
 * Post title composable, used by the post detail screen title section and by link previews.
 * Parameters: title, action handler, modifier, max lines, clickable, composer, changed flags,
 * default flags.
 */
internal object PostTitleFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(
        "Ljava/lang/String;",
        "Lkotlin/jvm/functions/Function1;",
        "L",
        "I",
        "Z",
        "L",
        "I",
        "I"
    ),
    filters = listOf(
        string("post_title")
    )
)

internal object PostTitleSectionToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string("PostUnitTitleSection(data=")
    )
)

/**
 * Post detail screen title section. Invokes the post title composable.
 */
internal object PostTitleSectionFingerprint : Fingerprint(
    classFingerprint = PostTitleSectionToStringFingerprint,
    returnType = "V",
    filters = listOf(
        methodCall(reference = { PostTitleFingerprint() })
    )
)

/**
 * Compose foundation SelectionContainer(modifier, content).
 * Shape: (int changed, int default, Composer, Modifier, ComposableLambdaImpl) -> void.
 */
internal object ComposeSelectionContainerFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("I", "I", "L", "L", "Landroidx/compose/runtime/internal/"),
    filters = listOf(
        literal(1949207773L)
    )
)

/**
 * Compose runtime rememberComposableLambda(key, block, composer).
 */
@StrictQuery
internal object ComposeComposableLambdaFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "Landroidx/compose/runtime/internal/",
    parameters = listOf("I", "L", "L"),
    filters = listOf(
        newInstance("Landroidx/compose/runtime/internal/")
    )
)

/**
 * Comment click event handler. Clicking a comment collapses or expands it.
 */
internal object CommentClickEventHandlerFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = listOf("L", "Lkotlin/coroutines/jvm/internal/ContinuationImpl;"),
    filters = listOf(
        newInstance($$"Lcom/reddit/comments/events/handler/OnClickCommentEventHandler$handle$1;"),
        // The handler looks the clicked comment up in the comment tree. Unlike the patcher,
        // a runtime hook cannot inject at the collapsed branch, so it reuses this call to read
        // the collapsed state before the handler runs.
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            parameters = listOf("Ljava/lang/String;"),
            returnType = "Lcom/reddit/domain/model/IComment;"
        ),
        methodCall(
            definingClass = "Lcom/reddit/domain/model/Comment;",
            name = "getCollapsed"
        ),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
        // Branch to collapse the comment if it is expanded.
        opcode(Opcode.IF_EQZ, MatchAfterWithin(1)),
        fieldAccess(
            opcode = Opcode.SGET_OBJECT,
            definingClass = "Lkotlin/Unit;"
        )
    )
)

/**
 * Selection container tap handler. A touch that does not move more than the touch slop
 * is a tap, which clears the selection.
 */
internal object SelectionTapDetectionFingerprint : Fingerprint(
    definingClass = $$"Landroidx/compose/foundation/text/selection/SelectionManager$onClearSelectionRequested$1$1;",
    name = "invokeSuspend",
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            returnType = "Ljava/lang/Object;",
            parameters = listOf(
                "L",
                "L",
                "Landroidx/compose/ui/input/pointer/PointerEventPass;",
                "Lkotlin/coroutines/jvm/internal/BaseContinuationImpl;"
            )
        )
    )
)

/**
 * awaitAllPointersUpWithSlopDetection(scope, down, pass). Reads the touch slop
 * through a static helper that returns a float.
 */
@StrictQuery
internal object PointersUpSlopDetectionFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/Object;",
    parameters = listOf(
        "L",
        "L",
        "Landroidx/compose/ui/input/pointer/PointerEventPass;",
        "Lkotlin/coroutines/jvm/internal/BaseContinuationImpl;"
    ),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            returnType = "F",
            parameters = listOf("L", "I")
        ),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately())
    )
)

/**
 * SelectionManager.onRelease(): clears the selection and hides the toolbar.
 */
@StrictQuery
internal object SelectionReleaseFingerprint : Fingerprint(
    definingClass = "Landroidx/compose/foundation/text/selection/",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        methodCall(name = "setValue"),
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            definingClass = "Lkotlin/jvm/functions/Function1;",
            name = "invoke"
        )
    )
)

/**
 * SelectionManager.setSelection(selection).
 */
@StrictQuery
internal object SelectionChangedFingerprint : Fingerprint(
    definingClass = "Landroidx/compose/foundation/text/selection/",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        methodCall(name = "setValue"),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, parameters = listOf(), returnType = "V")
    )
)

/**
 * FocusOwner.clearFocus(force). Reddit's post detail screen clears focus on every touch,
 * which also clears the text selection.
 */
@StrictQuery
internal object FocusClearFingerprint : Fingerprint(
    definingClass = "Landroidx/compose/ui/focus/",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Z"),
    filters = listOf(
        literal(8L),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, parameters = listOf("I", "Z", "Z"), returnType = "Z")
    )
)

/**
 * AndroidComposeView.dispatchTouchEvent(MotionEvent): every touch that enters Compose.
 */
internal object ComposeDispatchTouchEventFingerprint : Fingerprint(
    definingClass = "Landroidx/compose/ui/platform/",
    name = "dispatchTouchEvent",
    returnType = "Z",
    parameters = listOf("Landroid/view/MotionEvent;")
)


internal val readSelectionTouchSlop = findMethodDirect {
    PointersUpSlopDetectionFingerprint.instructionMatches.first().instruction.methodRef!!
}

/**
 * Comment tree lookup called by the comment click handler:
 * the model id with kind of the click event is resolved to the comment.
 */
internal val findCommentTreeLookup = findMethodDirect {
    CommentClickEventHandlerFingerprint.instructionMatches[1].instruction.methodRef!!
}
