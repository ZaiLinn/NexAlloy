package io.github.nexalloy.morphe.music.layout.lyrics

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findClassDirect
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.literal
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.string

internal const val TABBED_VIEW_CLASS = "Lcom/google/android/apps/youtube/music/ui/tabs/TabbedView;"

/**
 * Field number of the tab renderer in a tab of the watch next response.
 */
internal const val TAB_RENDERER_FIELD = 58174010L

/**
 * Matched only to reach the engagement panel controller class, which owns the field
 * holding the panel currently in the container.
 */
internal object EngagementPanelControllerFingerprint : Fingerprint(
    strings = listOf(
        "EngagementPanelController: cannot show EngagementPanel before EngagementPanelController.init() has been called.",
        "[EngagementPanel] Cannot show EngagementPanel before EngagementPanelController.init() has been called."
    )
)

/**
 * Compares the identifier of the panel being shown with the one of the comments panel. The
 * static call right before the string is the helper that reads the identifier out of the
 * panel renderer, which is what tells the lyrics panel from the other panels.
 *
 * Strict: another method builds the comments panel from its identifier, without calling
 * the helper.
 */
@StrictQuery
internal object PanelIdentifierFingerprint : Fingerprint(
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("L"),
            returnType = "Ljava/lang/String;"
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        string("music-comment-panel", location = MatchAfterWithin(2))
    )
)

/**
 * Tells the listeners of a tabbed view which tab was selected. The other single int parameter
 * methods of the class do not call the listeners.
 */
internal object TabbedViewTabSelectedFingerprint : Fingerprint(
    definingClass = TABBED_VIEW_CLASS,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("I"),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            parameters = listOf("I", "Z"),
            returnType = "V"
        )
    )
)

/**
 * Builds the watch next response of device files, which has only a Songs tab. The built tab
 * renderer is cast to its type and put in the tab, and then the tab is marked as holding a
 * tab renderer.
 */
internal object SideloadedWatchNextResponseFingerprint : Fingerprint(
    returnType = "Lcom/google/common/util/concurrent/ListenableFuture;",
    strings = listOf("Playlist panel has no current sideloaded track."),
    filters = listOf(
        opcode(Opcode.MOVE_RESULT_OBJECT),
        opcode(Opcode.CHECK_CAST, location = MatchAfterImmediately()),
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            type = "Ljava/lang/Object;",
            location = MatchAfterWithin(3)
        ),
        literal(TAB_RENDERER_FIELD, location = MatchAfterImmediately()),
        fieldAccess(opcode = Opcode.IPUT, type = "I", location = MatchAfterImmediately())
    )
)

/**
 * Adds a tab and its page to a tabbed view. The page is the second view.
 */
internal object TabControllerAddTabFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L", "Landroid/view/View;", "Landroid/view/View;", "L", "I"),
    filters = listOf(
        methodCall(definingClass = "Ljava/util/Map;", name = "put"),
        fieldAccess(opcode = Opcode.IGET_OBJECT, type = TABBED_VIEW_CLASS),
        methodCall(
            definingClass = TABBED_VIEW_CLASS,
            parameters = listOf("Ljava/lang/Runnable;"),
            returnType = "V"
        )
    )
)

/**
 * Removes a tab from a tabbed view.
 */
internal object TabControllerRemoveTabFingerprint : Fingerprint(
    classFingerprint = TabControllerAddTabFingerprint,
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        methodCall(
            definingClass = TABBED_VIEW_CLASS,
            parameters = listOf("Ljava/lang/Runnable;"),
            returnType = "V"
        ),
        methodCall(definingClass = "Ljava/util/Map;", name = "remove")
    )
)

/**
 * Constructor of the tab model that the tab controller adds, which keeps the tab renderer.
 * The tab model is the first parameter of [TabControllerAddTabFingerprint], and the tab renderer is
 * the type [SideloadedWatchNextResponseFingerprint] casts to.
 */
internal object TabModelConstructorFingerprint : Fingerprint(
    definingClass = {
        TabControllerAddTabFingerprint.match().method.paramTypes.first().descriptor
    },
    name = "<init>",
    parameters = {
        listOf(SideloadedWatchNextResponseFingerprint.instructionMatches[1].instruction.classRef!!.descriptor)
    },
    filters = listOf(
        fieldAccess(opcode = Opcode.IPUT_OBJECT, definingClass = "this")
    )
)

/**
 * The tab renderer type the watch next response of a device file casts to.
 */
internal val lyricsTabRendererClass = findClassDirect {
    SideloadedWatchNextResponseFingerprint.instructionMatches[1].instruction.classRef!!
}

/**
 * The tab class of the watch next response, the class of the object that holds
 * the tab renderer and the renderer bit.
 */
internal val lyricsTabClass = findClassDirect {
    SideloadedWatchNextResponseFingerprint.instructionMatches.last().instruction.fieldRef!!.declaredClass!!
}

/**
 * The method of the tabs builder that adds the built tab. Both paths that build the
 * sideloaded watch next response call it.
 */
internal val lyricsTabsBuilderAddTabMethod = findMethodDirect {
    val match = SideloadedWatchNextResponseFingerprint.match()
    val tabType = match.instructionMatches.last().instruction.fieldRef!!.declaredClass!!.descriptor
    match.method.instructions!!
        .mapNotNull { it.methodRef }
        .filter {
            !it.isConstructor &&
                it.paramTypes.map { parameter -> parameter.descriptor } == listOf(tabType) &&
                it.returnType?.descriptor == "V"
        }
        .distinctBy { it.descriptor }
        .single()
}

/**
 * The field of the tab controller that holds the tabbed view.
 */
internal val lyricsTabbedViewField = findFieldDirect {
    TabControllerAddTabFingerprint.instructionMatches[1].instruction.fieldRef!!
}

/**
 * The field of the tab model that holds the tab renderer.
 */
internal val lyricsTabModelRendererField = findFieldDirect {
    TabModelConstructorFingerprint.instructionMatches[0].instruction.fieldRef!!
}

/**
 * The app's static helper that reads the identifier of a panel out of its renderer.
 */
internal val lyricsPanelIdentifierHelperMethod = findMethodDirect {
    PanelIdentifierFingerprint.instructionMatches[0].instruction.methodRef!!
}
