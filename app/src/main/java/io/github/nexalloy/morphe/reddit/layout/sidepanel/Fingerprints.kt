/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4978
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.nexalloy.morphe.reddit.layout.sidepanel

import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.newInstance
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.Opcode

/**
 * Creates the view of a screen. Modal screens are wrapped in a ModalBackdropView.
 */
@StrictQuery
internal object ScreenCreateViewFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/screen/BaseScreen;",
    returnType = "Landroid/view/View;",
    parameters = listOf("Landroid/view/LayoutInflater;", "Landroid/view/ViewGroup;"),
    filters = listOf(
        // Presentation of the screen (full screen, dialog or bottom sheet).
        methodCall(
            definingClass = "Lcom/reddit/screen/BaseScreen;",
            parameters = listOf()
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        opcode(Opcode.INSTANCE_OF, location = MatchAfterImmediately()),
        methodCall(
            definingClass = "Lcom/reddit/screen/dialog/ModalBackdropView;",
            name = "setOnClickedOutside"
        ),
        methodCall(
            definingClass = "Lcom/reddit/screen/BaseScreen;",
            returnType = "Landroidx/appcompat/widget/Toolbar;",
            parameters = listOf()
        ),
        opcode(Opcode.RETURN_OBJECT, location = MatchAfterWithin(10))
    )
)

/**
 * Presentation of the post detail screen.
 */
internal object PostDetailPresentationFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/frontpage/presentation/listing/linkpager/refactor/PostDetailPagerScreen;",
    returnType = { ScreenCreateViewFingerprint.instructionMatches.first().instruction.methodRef!!.returnType!!.descriptor },
    parameters = listOf()
)

/**
 * Creates the dialog presentation, with the default values for all parameters except the first.
 */
@StrictQuery
internal object DialogPresentationFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/emailcollection/screens/EmailCollectionPopupScreen;",
    name = "<init>",
    filters = listOf(
        methodCall(
            name = "<init>",
            parameters = listOf("Z", "L", "L", "I")
        )
    )
)

/**
 * Opens a post from a feed card with a transition that expands the card.
 */
@StrictQuery
internal object OpenPostWithTransitionFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        methodCall(
            definingClass = "Lcom/reddit/listing/common/ListingViewMode;",
            name = "isClassic"
        ),
        methodCall(
            returnType = "Lcom/bluelinelabs/conductor/ScreenController;",
            parameters = listOf("L")
        )
    )
)

/**
 * Opens a post. Without a feed card it pushes the post screen without a transition.
 */
@StrictQuery
internal object OpenPostFingerprint : Fingerprint(
    classFingerprint = OpenPostWithTransitionFingerprint,
    returnType = "V",
    filters = listOf(
        newInstance("Lcom/reddit/frontpage/presentation/listing/linkpager/refactor/PostDetailPagerScreen;"),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            returnType = "V",
            parameters = listOf("Landroid/content/Context;", "Lcom/reddit/screen/BaseScreen;", "L")
        )
    )
)


internal val panelPresentationConstructor = findMethodDirect {
    checkNotNull(DialogPresentationFingerprint.instructionMatches.first().instruction.methodRef)
}

internal val openPostWithoutTransition = findMethodDirect {
    checkNotNull(OpenPostFingerprint.instructionMatches.last().instruction.methodRef)
}
