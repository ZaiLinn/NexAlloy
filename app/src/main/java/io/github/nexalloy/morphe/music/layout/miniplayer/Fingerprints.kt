/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Runtime port of the upstream patcher fingerprints
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/music/layout/miniplayer/Fingerprints.kt).
 * Every anchor below was re-verified against the YT Music 9.46.51 jadx ground truth
 * (/tmp/ytm-src/sources); the obfuscated names in the comments are that version's names.
 */

package io.github.nexalloy.morphe.music.layout.miniplayer

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.ResourceType
import io.github.nexalloy.RequireAppVersion
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.literal
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.resourceLiteral
import io.github.nexalloy.morphe.string

internal const val WATCH_WHILE_LAYOUT_CLASS =
    "Lcom/google/android/apps/youtube/music/watchpage/ui/WatchWhileLayout;"

internal const val PLAYER_PAGE_BEHAVIOR_CLASS =
    "Lcom/google/android/apps/youtube/music/watchpage/ui/PlayerPageBehavior;"

/**
 * Matches the miniplayer constructor.
 * Identified by the play/pause button resource literal and a unique string in the method body.
 * Anchor (9.46.51): `Ldefpackage/jsi;-><init>(...)V`, the class of the miniplayer controller.
 */
internal object MiniPlayerConstructorFingerprint : Fingerprint(
    name = "<init>",
    filters = listOf(
        resourceLiteral(ResourceType.ID, "mini_player_play_pause_replay_button"),
        methodCall(
            name = "findViewById",
            opcode = Opcode.INVOKE_VIRTUAL,
            location = MatchAfterWithin(5)
        ),
        string("sharedToggleMenuItemMutations")
    )
)

/**
 * Matches the TabLayout method that assigns the navigation bar background color.
 * Anchor (9.46.51): `Ldefpackage/hnc;->q()V`, the visible-tab-bar branch sets the
 * `ytm_color_grey_12` background; the last instruction match is that setBackgroundColor call.
 */
internal object NavigationBarTabLayoutFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        string("FEmusic_radio_builder"),
        resourceLiteral(ResourceType.COLOR, "ytm_color_grey_12"),
        methodCall(name = "setBackgroundColor")
    )
)

/**
 * The public switch-toggle color setter of the miniplayer, which applies the album color.
 * Anchor (9.46.51): `Ldefpackage/jsi;->x(Lizp;J)V`.
 */
internal object SwitchToggleColorParentFingerprint : Fingerprint(
    classFingerprint = MiniPlayerConstructorFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "J")
)

/**
 * The private color math helper of the miniplayer (9.32+). The last instruction match reads
 * the color math object of the toggle; instruction match 4 reads the color int of it.
 * Anchor (9.46.51): `Ldefpackage/jsi;->G(Lizp;J)V`, `Lizp;->f:Lizq;` and `Lizq;->d:I`.
 */
internal object SwitchToggleColorFingerprint : Fingerprint(
    classFingerprint = MiniPlayerConstructorFingerprint,
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "J"),
    filters = listOf(
        methodCall(opcode = Opcode.INVOKE_STATIC, smali = "Lj$/time/Duration;->ofMillis(J)Lj$/time/Duration;"),
        opcode(Opcode.IF_NE),
        opcode(Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
        opcode(Opcode.GOTO, location = MatchAfterWithin(5)),
        fieldAccess(opcode = Opcode.IGET, type = "I", location = MatchAfterWithin(5)),
        fieldAccess(opcode = Opcode.IGET_OBJECT, type = "L", location = MatchAfterWithin(5)),
    )
)

/**
 * The color math helper of miniplayer builds older than 9.32, where the color math object is
 * reached through a getter instead of a field. Not reachable on the tested 9.46.51 host.
 */
@RequireAppVersion(maxVersion = "9.31.99")
internal object SwitchToggleColorLegacyFingerprint : Fingerprint(
    classFingerprint = MiniPlayerConstructorFingerprint,
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "J"),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            parameters = listOf(),
            returnType = "L"
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        opcode(Opcode.CHECK_CAST, location = MatchAfterImmediately()),
        opcode(Opcode.GOTO, location = MatchAfterWithin(5)),
        fieldAccess(opcode = Opcode.IGET, type = "I"),
        opcode(Opcode.INVOKE_VIRTUAL, location = MatchAfterImmediately()),
    )
)

/**
 * Matches the miniplayer method that animates its colors back to the color in its color field.
 * It compares the color last applied with the color field, and if they differ,
 * saves the color field as the color applied and animates to it for 200 Ms.
 * The second instruction match reads the color field.
 * Anchor (9.46.51): `Ldefpackage/jsi;->E()V`, the color field is `jsi.aZ` and the placeholder
 * color math object read is `Lizp;->a:Lizp;`.
 */
internal object MiniplayerResetColorsFingerprint : Fingerprint(
    classFingerprint = MiniPlayerConstructorFingerprint,
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET,
            definingClass = "this",
            type = "I"
        ),
        fieldAccess(
            opcode = Opcode.IGET,
            definingClass = "this",
            type = "I",
            location = MatchAfterImmediately()
        ),
        literal(
            literal = 200L,
            location = MatchAfterImmediately()
        ),
        opcode(
            opcode = Opcode.IF_EQ,
            location = MatchAfterImmediately()
        ),
        fieldAccess(
            opcode = Opcode.IPUT,
            definingClass = "this",
            type = "I",
            location = MatchAfterImmediately()
        )
    )
)

/**
 * The queue transition that decides between the fullscreen player and the forced miniplayer.
 * Anchor (9.46.51): `Ldefpackage/ijp;->i(Labsr;Lhzn;)V`, the boolean after the first
 * `Boolean.booleanValue()` feeds `Lacay;->I(Z)V` (the miniplayer flag setter).
 */
internal object MinimizedPlayerFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "L"),
    filters = listOf(
        string("w_st")
    )
)

/**
 * Matches the watch-while layout method that hands a view array to the static accessibility
 * traversal-order helper. The view array is extended with the injected miniplayer buttons.
 * Anchor (9.46.51): `Lcom/google/android/apps/youtube/music/watchpage/ui/WatchWhileLayout;->au()V`
 * calling `Lkwe;->aV([Landroid/view/View;)V`.
 */
internal object MppWatchWhileLayoutFingerprint : Fingerprint(
    definingClass = WATCH_WHILE_LAYOUT_CLASS,
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        opcode(Opcode.NEW_ARRAY),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("[Landroid/view/View;"),
            returnType = "V"
        )
    )
)

/**
 * The enum that declares the swipe gesture logging constant.
 * Anchor (9.46.51): the enum with the string constant
 * `INTERACTION_LOGGING_GESTURE_TYPE_SWIPE`; instruction match 1 is its static field.
 */
internal object InteractionLoggingEnumFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        string("INTERACTION_LOGGING_GESTURE_TYPE_SWIPE"),
        fieldAccess(
            opcode = Opcode.SPUT_OBJECT,
            definingClass = "this"
        )
    )
)

/**
 * Matches MusicActivity.onCreate and the widget logging setup it runs when the activity was
 * opened from a widget. The instruction matches carry the references the runtime swipe-dismiss
 * bridge replays: match 1 is the peer's provider field, match 2 its getter, match 3 the
 * gesture host class, match 5 the callback class, match 7 the event id factory, match 8 the
 * callback constructor and match 9 the gesture registration call.
 * Anchor (9.46.51): `Lcom/google/android/apps/youtube/music/activities/MusicActivity;->onCreate`,
 * the peer is `Ldefpackage/ggb;`, the gesture host `Lxho;`, the callback `Lxhl;`.
 */
internal object MusicActivityWidgetFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/apps/youtube/music/activities/MusicActivity;",
    name = "onCreate",
    filters = listOf(
        string("widget_key"),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            location = MatchAfterWithin(5)
        ),
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            parameters = listOf(),
            returnType = "Ljava/lang/Object;",
            location = MatchAfterWithin(3)
        ),
        opcode(
            opcode = Opcode.CHECK_CAST,
            location = MatchAfterWithin(3)
        ),
        fieldAccess(
            opcode = Opcode.SGET_OBJECT,
            location = MatchAfterWithin(3)
        ),
        opcode(
            opcode = Opcode.NEW_INSTANCE,
            location = MatchAfterWithin(3)
        ),
        literal(
            literal = 79500L,
            location = MatchAfterWithin(3)
        ),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("I"),
            location = MatchAfterWithin(3)
        ),
        methodCall(
            opcode = Opcode.INVOKE_DIRECT,
            name = "<init>",
            parameters = listOf("L"),
            location = MatchAfterWithin(3)
        ),
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            parameters = listOf("L", "L", "L"),
            returnType = "V",
            location = MatchAfterWithin(3)
        ),
    )
)

/**
 * 9.03+
 * The miniplayer method that shows the "Nothing is playing" text.
 * Anchor (9.46.51): `Ldefpackage/jsi;->t()V` setting R.string.mini_player_default_text.
 */
internal object MiniPlayerDefaultTextFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    returnType = "V",
    filters = listOf(
        resourceLiteral(ResourceType.STRING, "mini_player_default_text")
    )
)

/**
 * Matches the method that shows the cold start miniplayer text,
 * by calling [MiniPlayerDefaultTextFingerprint] on the miniplayer.
 * The first instruction match compares the object passed to the method.
 * Anchor (9.46.51): `Ldefpackage/jri;->a(Ljava/lang/Object;)V`, case 19 of the miniplayer
 * state observer, where the player state enum is compared before `jsi.t()` is called.
 */
internal object ColdStartMiniplayerTextFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Ljava/lang/Object;"),
    returnType = "V",
    filters = listOf(
        opcode(Opcode.IF_NE),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = WATCH_WHILE_LAYOUT_CLASS,
            location = MatchAfterImmediately()
        ),
        // The called method is public and final, so it is only called with invoke-virtual.
        methodCall(reference = { MiniPlayerDefaultTextFingerprint.run() })
    )
)

/**
 * 9.02 - 9.42
 * The player page behavior method that starts a page motion. Not reachable on 9.46.51.
 */
@RequireAppVersion(maxVersion = "9.42.99")
internal object PlayerPageBehaviorFingerprint : Fingerprint(
    definingClass = PLAYER_PAGE_BEHAVIOR_CLASS,
    accessFlags = listOf(AccessFlags.FINAL),
    parameters = listOf(),
    returnType = "V",
    filters = listOf(
        opcode(Opcode.CONST_4),
        opcode(Opcode.IPUT_BOOLEAN),
        opcode(Opcode.RETURN_VOID)
    )
)

/**
 * 9.43+
 * The first instruction match reads the player page motion field.
 * Anchor (9.46.51): `Lcom/google/android/apps/youtube/music/watchpage/ui/PlayerPageBehavior;->onInterceptTouchEvent`,
 * the motion field is the boolean read by `if (this.d)`.
 */
internal object PlayerPageBehaviorInterceptTouchEventFingerprint : Fingerprint(
    definingClass = PLAYER_PAGE_BEHAVIOR_CLASS,
    name = "onInterceptTouchEvent",
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_BOOLEAN,
            definingClass = "this"
        ),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            location = MatchAfterImmediately()
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            parameters = listOf("Landroid/view/MotionEvent;"),
            returnType = "V",
            location = MatchAfterImmediately()
        )
    )
)

/**
 * 9.43+
 * Anchor (9.46.51): `Lcom/google/android/apps/youtube/music/watchpage/ui/PlayerPageBehavior;->onTouchEvent`.
 */
internal object PlayerPageBehaviorTouchEventFingerprint : Fingerprint(
    definingClass = PLAYER_PAGE_BEHAVIOR_CLASS,
    name = "onTouchEvent"
)

/**
 * Matches the watch-while layout method that creates the bottom sheet callback of the
 * warm start miniplayer. The last instruction match is the new-instance of that callback,
 * whose class is the defining class of [WarmStartMiniplayerTextFingerprint].
 * Anchor (9.46.51): `Lcom/google/android/apps/youtube/music/watchpage/ui/WatchWhileLayout;->E()V`,
 * where the `Ljtc;` bottom sheet callback is created.
 */
internal object WatchWhileLayoutFingerprint : Fingerprint(
    definingClass = WATCH_WHILE_LAYOUT_CLASS,
    returnType = "V",
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = PLAYER_PAGE_BEHAVIOR_CLASS
        ),
        opcode(
            opcode = Opcode.NEW_INSTANCE,
            location = MatchAfterWithin(3)
        )
    )
)

/**
 * Matches the method of the warm start miniplayer ([WatchWhileLayoutFingerprint] creates it)
 * that changes the player bottom sheet when the miniplayer text is shown.
 * The last instruction match calls the bottom sheet.
 * Anchor (9.46.51): `Ldefpackage/jtc;->b(Landroid/view/View;I)V`.
 */
internal object WarmStartMiniplayerTextFingerprint : Fingerprint(
    definingClass = {
        WatchWhileLayoutFingerprint.instructionMatches.last().instruction.classRef!!.descriptor
    },
    parameters = { listOf("Landroid/view/View;", "I") },
    filters = listOf(
        fieldAccess(
            opcodes = listOf(Opcode.IGET_OBJECT),
            definingClass = { WATCH_WHILE_LAYOUT_CLASS }
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            definingClass = "Lcom/google/android/material/bottomsheet/BottomSheetBehavior;",
            parameters = listOf("Z"),
            returnType = "V",
            location = MatchAfterWithin(5)
        )
    )
)

/**
 * Matches the watch-while layout method that applies a new player state.
 * Every player state change (DISMISSED, MINIMIZED, MAXIMIZED_NOW_PLAYING...) passes through it.
 * The first parameter is the player state enum.
 * Anchor (9.46.51): `Lcom/google/android/apps/youtube/music/watchpage/ui/WatchWhileLayout;->I(Lhzi;F)V`.
 */
internal object WatchWhileLayoutSetPlayerStateFingerprint : Fingerprint(
    definingClass = WATCH_WHILE_LAYOUT_CLASS,
    returnType = "V",
    parameters = listOf("L", "F")
)

/**
 * Matches the watch-while layout method that requests a new player state,
 * by moving the player bottom sheet to the matching position.
 * Anchor (9.46.51): `Lcom/google/android/apps/youtube/music/watchpage/ui/WatchWhileLayout;->P(Lhzi;)V`,
 * which ends in `LPlayerPageBehavior;->S(I)V` on the bottom sheet.
 */
internal object WatchWhileLayoutRequestPlayerStateFingerprint : Fingerprint(
    definingClass = WATCH_WHILE_LAYOUT_CLASS,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = { listOf(WatchWhileLayoutSetPlayerStateFingerprint.run().paramTypes.first().descriptor) },
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            definingClass = "Lcom/google/android/material/bottomsheet/BottomSheetBehavior;",
            parameters = listOf("I"),
            returnType = "V"
        )
    )
)

/**
 * Matches the watch-while dismiss callback (swipe-dismiss or "Dismiss queue"),
 * identified by an IGET_OBJECT of the MusicActivity peer's AtomicBoolean and
 * a following `AtomicBoolean.set(Z)`. The peer class comes from [MusicActivityWidgetFingerprint].
 * Anchor (9.46.51): the public final no-argument method of the MusicActivity peer
 * (`Ldefpackage/ggb;`) that reads its AtomicBoolean field and calls `AtomicBoolean.set(true)`.
 */
internal object WatchWhileDismissedFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    returnType = "V",
    filters = listOf(
        fieldAccess(
            opcodes = listOf(Opcode.IGET_OBJECT),
            definingClass = {
                MusicActivityWidgetFingerprint.instructionMatches[1].instruction.fieldRef!!.declaredClass.descriptor
            },
            type = "Ljava/util/concurrent/atomic/AtomicBoolean;"
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = "Ljava/util/concurrent/atomic/AtomicBoolean;->set(Z)V",
            location = MatchAfterWithin(3)
        )
    )
)
