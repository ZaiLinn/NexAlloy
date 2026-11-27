package io.github.nexalloy.morphe.youtube.layout.hide.ambientmode

import io.github.nexalloy.SkipTest
import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterLast
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.anyInstruction
import io.github.nexalloy.morphe.checkCast
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findClassDirect
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.findMethodListDirect
import io.github.nexalloy.morphe.literal
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.resourceMappings
import io.github.nexalloy.morphe.string
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldData

internal const val POWER_SAVE_MODE_CHANGED = "android.os.action.POWER_SAVE_MODE_CHANGED"
internal const val IS_POWER_SAVE_MODE_CALL = "Landroid/os/PowerManager;->isPowerSaveMode()Z"

//
// CinematicContainerRenderer: the Ambient mode proto of the /next response.
//

/**
 * The Ambient mode renderer proto. The literal is the extension field number of the
 * CinematicContainerRenderer, registered in its static initializer.
 *
 * Anchor (21.45.82): `bebf.<clinit>`.
 */
@StrictQuery
internal object CinematicContainerRendererFingerprint : Fingerprint(
    name = "<clinit>",
    returnType = "V",
    filters = listOf(
        literal(455752576L),
    ),
)

private val rendererDescriptor: DexKitBridge.() -> String = {
    CinematicContainerRendererFingerprint.run().declaredClass!!.descriptor
}

/**
 * Reads the presentation style (field 3) of a CinematicContainerRenderer, if it has one.
 * The matches are: has-bits field, the style bit mask, a zero check and the style field.
 *
 * Anchor (21.45.82): `kkq.g(bebf)beba`.
 */
private fun cinematicContainerPresentationStyleFingerprint(rendererType: String) = Fingerprint(
    isStatic = true,
    returnType = "L",
    parameters = listOf(rendererType),
    filters = listOf(
        // Has-bits field.
        fieldAccess(opcode = Opcode.IGET, definingClass = rendererType, type = "I"),
        opcode(Opcode.AND_INT_LIT8, location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        // Presentation style field.
        fieldAccess(
            opcode = Opcode.IGET,
            definingClass = rendererType,
            type = "I",
            location = MatchAfterImmediately(),
        ),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("I"),
            location = MatchAfterImmediately(),
        ),
    ),
)

/** The has-bits field, style bit mask and presentation style field of the renderer. */
internal class CinematicContainerStyle(
    val hasBitsField: FieldData,
    val styleHasBitMask: Long,
    val styleField: FieldData,
)

private var cinematicContainerStyleResult: CinematicContainerStyle? = null

/**
 * Resolves the presentation style getter and with it the has-bits field, the style bit
 * mask and the style field of the renderer. The getter itself holds no hook.
 */
internal val cinematicPresentationStyleGetter = findMethodDirect {
    val match = cinematicContainerPresentationStyleFingerprint(rendererDescriptor()).match()
    cinematicContainerStyleResult = CinematicContainerStyle(
        match.instructionMatches[0].instruction.fieldRef!!,
        match.instructionMatches[1].instruction.literal!!,
        match.instructionMatches[3].instruction.fieldRef!!,
    )
    match.method
}

/** The has-bits field, style bit mask and presentation style field of the renderer. */
internal val cinematicContainerStyle: CinematicContainerStyle
    get() = cinematicContainerStyleResult
        ?: error("The presentation style getter has not been resolved yet")

/**
 * Reads the config (field 4) of a CinematicContainerRenderer. The last match is the
 * config field of the renderer.
 *
 * Anchor (21.45.82): `jng.a(Object)Object`, which unwraps the Optional of the /next
 * response and reads the config field.
 */
@StrictQuery
private class CinematicContainerConfigFingerprint(rendererType: String) : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        methodCall(name = "get", parameters = listOf(), returnType = "Ljava/lang/Object;"),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        checkCast(rendererType, location = MatchAfterImmediately()),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = rendererType,
            location = MatchAfterImmediately(),
        ),
    ),
)

/** The config field of the renderer. Anchor (21.45.82): `bebf.g` (type beax). */
internal val cinematicContainerConfigField = findFieldDirect {
    CinematicContainerConfigFingerprint(rendererDescriptor())
        .match()
        .instructionMatches
        .last()
        .instruction
        .fieldRef!!
}

/**
 * Reads the 'ambient mode on by default' value (field 11) of the renderer config, used
 * if the flyout menu toggle was never changed. The last match is the boolean field.
 *
 * Anchor (21.45.82): `rer.a(Object, Object)Object`, which prefers the saved flyout menu
 * toggle and otherwise reads the config field.
 */
private fun cinematicContainerEnabledByDefaultFingerprint(configType: String) = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;"),
    filters = listOf(
        // Has-bits field.
        fieldAccess(opcode = Opcode.IGET, definingClass = configType, type = "I"),
        anyInstruction(
            opcode(Opcode.AND_INT_LIT16), // 20.51 - 21.20
            opcode(Opcode.AND_INT_2ADDR), // 20.21, 21.21+
            location = MatchAfterImmediately(),
        ),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        fieldAccess(
            opcode = Opcode.IGET_BOOLEAN,
            definingClass = configType,
            type = "Z",
            location = MatchAfterImmediately(),
        ),
        methodCall(
            smali = "Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;",
            location = MatchAfterImmediately(),
        ),
    ),
)

/** The 'ambient mode on by default' field of the renderer config. Anchor (21.45.82): `beax.m`. */
internal val cinematicContainerEnabledByDefaultField = findFieldDirect {
    cinematicContainerEnabledByDefaultFingerprint(cinematicContainerConfigField().typeSign)
        .match()
        .instructionMatches
        .get(3) // The boolean field precedes the Boolean.valueOf call.
        .instruction
        .fieldRef!!
}

//
// RendererWithState: holds the renderer of the current video and its loading state.
//

/**
 * Holds the CinematicContainerRenderer of the current video and its loading state.
 *
 * Anchor (21.45.82): `rnt.toString()String`.
 */
@StrictQuery
internal object RendererWithStateFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    strings = listOf("RendererWithState{rendererLoadedState="),
)

/** The class that holds the renderer of the current video and its loading state. */
internal val rendererWithStateClass = findClassDirect {
    RendererWithStateFingerprint.run().declaredClass!!
}

//
// The Ambient mode toggle of the player flyout menu.
//

/**
 * Receives the Ambient mode toggle of the player flyout menu when the app resumes and
 * whenever it changes. The third match is the call that saves the toggle as the state
 * of the menu item.
 *
 * Anchor (21.45.82): `rms.a(Object)Object`, case 8, which casts the new value to
 * Boolean and passes it with the name of the menu item.
 */
@StrictQuery
internal object AmbientModeToggleFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        checkCast("Ljava/lang/Boolean;"),
        string("menu_item_cinematic_lighting", location = MatchAfterWithin(8)),
        methodCall(
            parameters = listOf("Ljava/lang/String;", "Ljava/lang/Boolean;"),
            returnType = "V",
            location = MatchAfterImmediately(),
        ),
    ),
)

/**
 * The menu item state setter that receives the Ambient mode toggle of the flyout menu.
 * Anchor (21.45.82): `cjx.an(String, Boolean)V`.
 */
internal val ambientModeToggleStateMethod = findMethodDirect {
    AmbientModeToggleFingerprint.match().instructionMatches[2].instruction.methodRef!!
}

//
// The power save mode wiring that turns Ambient mode off under power restrictions.
//

/**
 * The receivers of the Ambient mode power save mode changed broadcast. Both matches are
 * hooked, and their synthetic power save mode reader is resolved from the receiver.
 *
 * Anchor (21.45.82): `awpq.onReceive(Context, Intent)` and
 * `rlz.onReceive(Context, Intent)`, the only BroadcastReceivers with the broadcast.
 */
@SkipTest // Matches both receivers, and each one's reader is hooked.
internal object PowerSaveModeBroadcastReceiverFingerprint : Fingerprint(
    superclass = "Landroid/content/BroadcastReceiver;",
    name = "onReceive",
    returnType = "V",
    parameters = listOf("Landroid/content/Context;", "Landroid/content/Intent;"),
    filters = listOf(
        string(POWER_SAVE_MODE_CHANGED),
        methodCall(smali = "Landroid/content/Intent;->getAction()Ljava/lang/String;"),
        opcode(Opcode.INVOKE_DIRECT),
    ),
)

/**
 * Registers the Ambient mode power save mode receiver of the standard wiring.
 *
 * Anchor (21.45.82): `fji.B(Context, rlz)V`.
 */
@StrictQuery
internal object PowerSaveModeReceiverRegisterFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "V",
    parameters = listOf("Landroid/content/Context;", "L"),
    filters = listOf(
        string(POWER_SAVE_MODE_CHANGED),
        methodCall(definingClass = "Landroid/content/Context;", name = "registerReceiver"),
    ),
)

/**
 * Reads the first power save mode value of the standard wiring, if the receiver is
 * registered asynchronously. The constructor reference of its synthetic reader gives
 * the reader of both the asynchronous and the synchronous initial value path.
 *
 * Anchor (21.45.82): `fji.A(bwkf, Context)V`.
 */
@StrictQuery
internal object PowerSaveModeInitialValueAsyncFingerprint : Fingerprint(
    classFingerprint = PowerSaveModeReceiverRegisterFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "V",
    parameters = listOf("L", "Landroid/content/Context;"),
    filters = listOf(
        checkCast("Landroid/os/PowerManager;"),
        opcode(Opcode.INVOKE_DIRECT),
    ),
)

/**
 * Reads the first power save mode value of the alternative wiring and registers its
 * receiver. The constructor reference of its synthetic reader gives the reader.
 *
 * Anchor (21.45.82): `bxhl.h()bvdu`, which reads the PowerManager, reads the initial
 * power save mode value through the synthetic and registers the receiver.
 */
@StrictQuery
internal object PowerSaveModeInitialValueFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf(),
    filters = listOf(
        string(POWER_SAVE_MODE_CHANGED),
        checkCast("Landroid/os/PowerManager;"),
        opcode(Opcode.INVOKE_DIRECT),
        methodCall(definingClass = "Landroid/content/Context;", name = "registerReceiver"),
    ),
)

/**
 * The synthetic power save mode readers of the Ambient mode wiring, which read the
 * power save mode and pass it to the Ambient mode state. Each source method constructs
 * its reader with an (Object, int) constructor, and the reader is the only method of
 * its class that calls PowerManager.isPowerSaveMode().
 *
 * Anchor (21.45.82): `awkk.p` (case 4), `rjl.p` (case 7), `rzu.p` (case 13) and
 * `jbb.p` (case 17), the switch cases of the readers of the two receivers and the
 * two initial value readers.
 */
internal val powerSaveModeReaderMethods = findMethodListDirect {
    val sourceMethods = buildList {
        addAll(PowerSaveModeBroadcastReceiverFingerprint.matchAll(1..4).map { it.method })
        add(PowerSaveModeInitialValueAsyncFingerprint.run())
        add(PowerSaveModeInitialValueFingerprint.run())
    }

    val readerClasses = sourceMethods.map { source ->
        source.instructions!!.first { instruction ->
            val reference = instruction.methodRef
            instruction.opcode == Opcode.INVOKE_DIRECT.ordinal &&
                    reference?.name == "<init>" &&
                    reference.declaredClass != source.declaredClass &&
                    reference.paramTypes.size == 2 &&
                    reference.paramTypes[0].descriptor == "Ljava/lang/Object;" &&
                    reference.paramTypes[1].descriptor == "I"
        }.methodRef!!.declaredClass!!
    }.distinct()

    findMethod {
        searchInClass(readerClasses)
        matcher {
            paramCount = 1
            paramTypes("java.lang.Object")
            returnType = "void"
        }
    }.filter { method ->
        method.instructions?.any { it.methodRef?.descriptor == IS_POWER_SAVE_MODE_CALL } == true
    }
}

//
// Enhanced Ambient mode.
//

/**
 * Draws the background of a view, such as a row of the playlist, and the touch feedback
 * over it. The matched call is the background draw that upstream replaces with the
 * extension draw call.
 *
 * Anchor (21.45.82): `TouchFeedbackDrawable.draw(Canvas)V`.
 */
@StrictQuery
internal object TouchFeedbackDrawableDrawFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/libraries/youtube/rendering/ui/spec/motion/TouchFeedbackDrawable;",
    name = "draw",
    returnType = "V",
    parameters = listOf("Landroid/graphics/Canvas;"),
    filters = listOf(
        methodCall(smali = "Landroid/graphics/drawable/Drawable;->draw(Landroid/graphics/Canvas;)V")
    ),
)

/**
 * The method that inflates the controls overlay of the player, whose inflated view is
 * passed to the enhanced Ambient mode controller.
 *
 * Anchor (21.45.82): the method that uses `R.id.inset_overlay_view_layout` (see the
 * SponsorBlock patch for the same anchor).
 */
internal val ambientControlsOverlayFingerprint = findMethodDirect {
    findMethod {
        matcher {
            addUsingNumber(inset_overlay_view_layout)
            paramCount = 0
            returnType = "void"
        }
    }.single()
}

internal val inset_overlay_view_layout get() = resourceMappings["id", "inset_overlay_view_layout"]

//
// Fullscreen Ambient mode background.
//

/**
 * The fullscreen player view, which sets the background color of the video when the
 * player is laid out. The match is the last setBackgroundColor call, which receives
 * the Ambient mode fullscreen color that upstream transforms.
 *
 * Anchor (21.45.82): `YouTubePlayerViewNotForReflection.onLayout(Z, I, I, I, I)V`.
 */
@StrictQuery
internal object SetFullScreenBackgroundColorFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/apps/youtube/app/player/YouTubePlayerViewNotForReflection;",
    name = "onLayout",
    accessFlags = listOf(AccessFlags.PROTECTED, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Z", "I", "I", "I", "I"),
    filters = listOf(
        methodCall(name = "setBackgroundColor", location = MatchAfterLast()),
    ),
)

/**
 * The black background experiment of the new watch page, which replaces Ambient mode
 * with a black background. The literal is the feature flag id, which the patch forces
 * off. The flag is read through the shared feature flag override hook.
 */
internal val AMBIENT_MODE_BLACK_BACKGROUND_FLAG = 45767512L
