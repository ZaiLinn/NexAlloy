/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Runtime port of "Enable swipe to dismiss miniplayer"
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/music/layout/miniplayer/EnableSwipeToDismissMiniplayerPatch.kt).
 *
 * The upstream patch injects a helper method into the watch-while dismiss callback class and
 * adds the WatchWhileLayoutAccess interface to the watch-while layout. At runtime the helper
 * is a reflection bridge over the same references the generated smali uses, and the interface
 * is an adapter that invokes the resolved request method of the layout.
 */

package io.github.nexalloy.morphe.music.layout.miniplayer

import app.morphe.extension.music.patches.CrossfadePatch
import app.morphe.extension.music.patches.EnableSwipeToDismissMiniplayerPatch
import app.morphe.extension.music.patches.EnableSwipeToDismissMiniplayerPatch.WatchWhileLayoutAccess
import app.morphe.extension.shared.Logger
import io.github.nexalloy.ScopedHook
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.music.misc.playservice.is_9_43_or_greater
import io.github.nexalloy.morphe.music.misc.playservice.versionCheckPatch
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.music.video.information.musicVideoIdHook
import io.github.nexalloy.morphe.music.video.information.musicVideoInformationPatch
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * The widget gesture registration references of MusicActivity.onCreate, resolved the same way
 * the upstream helper method uses them. The dismiss callback replays the swipe gesture
 * registration of the widget path to dismiss the miniplayer through the app's own pipeline.
 * Anchor (9.46.51): the peer provider field `ggb.R`, its getter, the gesture host `xho`, the
 * callback class `xhl`, the event factory `xic.b(I)`, the callback constructor and the
 * registration call `xho.o(Ljava/lang/Enum;Ljava/lang/Object;Ljava/lang/Object;)V`.
 */
internal val widgetGesturePeerField = findFieldDirect {
    MusicActivityWidgetFingerprint.instructionMatches[1].instruction.fieldRef!!
}

internal val widgetGestureHostGetter = findMethodDirect {
    MusicActivityWidgetFingerprint.instructionMatches[2].instruction.methodRef!!
}

internal val widgetGestureEventFactory = findMethodDirect {
    MusicActivityWidgetFingerprint.instructionMatches[7].instruction.methodRef!!
}

internal val widgetGestureCallbackConstructor = findMethodDirect {
    MusicActivityWidgetFingerprint.instructionMatches[8].instruction.methodRef!!
}

internal val widgetGestureRegistrationMethod = findMethodDirect {
    MusicActivityWidgetFingerprint.instructionMatches[9].instruction.methodRef!!
}

/**
 * The swipe gesture enum constant of the interaction logging enum.
 * Anchor (9.46.51): the enum's `INTERACTION_LOGGING_GESTURE_TYPE_SWIPE` constant.
 */
internal val interactionLoggingSwipeField = findFieldDirect {
    InteractionLoggingEnumFingerprint.instructionMatches[1].instruction.fieldRef!!
}

/**
 * The bottom sheet call of the warm start miniplayer region the upstream patch skips
 * (the last instruction match of the warm start callback).
 * Anchor (9.46.51): `Ljtb;->b(Landroid/view/View;I)V` calling `LPlayerPageBehavior;->R(Z)V`.
 */
internal val warmStartBottomSheetMethod = findMethodDirect {
    WarmStartMiniplayerTextFingerprint.instructionMatches.last().instruction.methodRef!!
}

/**
 * The player page motion field, only read by the touch methods (9.43+).
 * Anchor (9.46.51): the boolean read by `PlayerPageBehavior.onInterceptTouchEvent` (`this.d`).
 */
internal val playerPageMotionField = findFieldDirect {
    PlayerPageBehaviorInterceptTouchEventFingerprint.instructionMatches.first().instruction.fieldRef!!
}

/**
 * The runtime replacement of the WatchWhileLayoutAccess interface the upstream patch adds to
 * the watch-while layout. patch_requestPlayerState forwards to the request method the
 * interface method is generated from.
 */
private class RuntimeWatchWhileLayoutAccess(
    watchWhileLayout: Any,
    private val requestPlayerState: Method,
) : WatchWhileLayoutAccess {

    private val layoutRef = WeakReference(watchWhileLayout)

    override fun patch_requestPlayerState(state: Enum<*>) {
        layoutRef.get()?.let { layout ->
            runCatching { requestPlayerState.invoke(layout, state) }
        }
    }
}

/**
 * Runtime port of "Enable swipe to dismiss miniplayer".
 */
val enableSwipeToDismissMiniplayerPatch = patch(
    name = "Enable swipe to dismiss miniplayer",
    description = "Adds an option to dismiss the miniplayer by swiping down on it.",
) {
    dependsOn(
        versionCheckPatch,
        musicVideoInformationPatch,
    )

    PreferenceScreen.addMiniplayerPreferences(
        SwitchPreference("morphe_music_enable_swipe_to_dismiss_miniplayer", summary = true)
    )

    fun failClosed(hook: String, block: () -> Unit) {
        runCatching(block).onFailure {
            Logger.printException({ "Swipe to dismiss hook failed: $hook" }, it)
        }
    }

    // region Dismiss miniplayer by swiping down

    failClosed("dismiss callback install") {
        val peerField: Field = ::widgetGesturePeerField.field.apply { isAccessible = true }
        val hostGetter: Method = ::widgetGestureHostGetter.method
        val swipeEnumField: Field = ::interactionLoggingSwipeField.field.apply { isAccessible = true }
        val eventFactory: Method = ::widgetGestureEventFactory.method
        val callbackConstructor: Constructor<*> =
            ::widgetGestureCallbackConstructor.constructor.apply { isAccessible = true }
        // The registration call is an invoke-interface whose reference names the gesture
        // host interface, but the method is declared on one of its superinterfaces, so
        // the lookup walks the inherited public methods instead of the declared ones.
        val registration: Method = ::widgetGestureRegistrationMethod.dexMethod.let { ref ->
            val owner = classLoader.loadClass(ref.className)
            owner.methods.single { method ->
                method.name == ref.name &&
                    method.parameterTypes.map { it.name } == ref.paramTypeNames.toList()
            }
        }

        /**
         * The runtime replica of the patcher-generated `patch_swipeToDismissMiniplayer`
         * helper: the same widget references, replayed with reflection. The upstream smali
         * resolves the gesture host through the peer's provider, builds the swipe-dismiss
         * callback with the 0x878b event and registers it with the swipe gesture of the
         * interaction logging enum.
         */
        fun swipeToDismissMiniplayer(peer: Any) {
            val provider = peerField.get(peer) ?: return
            val host = hostGetter.invoke(provider) ?: return
            val swipeEnum = swipeEnumField.get(null) ?: return
            val event = eventFactory.invoke(null, 0x878b)
            val callback = callbackConstructor.newInstance(event)
            registration.invoke(host, swipeEnum, callback, event)
        }

        WatchWhileDismissedFingerprint.hookMethod {
            before {
                failClosed("dismiss callback") {
                    if (EnableSwipeToDismissMiniplayerPatch.enableSwipeToDismissMiniplayer()) {
                        // The upstream injection replaces the callback body with the helper.
                        swipeToDismissMiniplayer(it.thisObject ?: return@failClosed)
                        it.setObjectExtra("morphe_swipeDismissed", true)
                        it.result = null
                    }
                }
            }
            after {
                if (it.getObjectExtra("morphe_swipeDismissed") == null) {
                    failClosed("dismiss callbacks") {
                        CrossfadePatch.onQueueDismissed()
                        EnableSwipeToDismissMiniplayerPatch.onMiniplayerDismissed()
                    }
                }
                it.setObjectExtra("morphe_swipeDismissed", null)
            }
        }
    }

    // endregion

    // region Keep the miniplayer hidden after dismissing it, until a song is played

    // After a dismiss, the app asks again for the miniplayer in some cases (such as a tab
    // change or the next app start), and the miniplayer comes back with "Nothing is playing".
    // The dismissed state is saved by the extension, so it is also kept after the app is closed.
    failClosed("player state hooks install") {
        WatchWhileLayoutSetPlayerStateFingerprint.hookMethod {
            before {
                val state = it.args[0] as? Enum<*> ?: return@before
                failClosed("player state changed") {
                    EnableSwipeToDismissMiniplayerPatch.onPlayerStateChanged(state)
                }
            }
        }

        val requestPlayerState: Method = WatchWhileLayoutRequestPlayerStateFingerprint.method

        WatchWhileLayoutRequestPlayerStateFingerprint.hookMethod {
            before {
                val state = it.args[0] as? Enum<*> ?: return@before
                failClosed("player state request") {
                    val layout = it.thisObject ?: return@failClosed
                    val access = RuntimeWatchWhileLayoutAccess(layout, requestPlayerState)
                    if (EnableSwipeToDismissMiniplayerPatch.ignorePlayerStateRequest(access, state)) {
                        it.result = null
                    }
                }
            }
        }
    }

    // The extension requests the miniplayer again when a song is played after dismissing.
    musicVideoIdHook(EnableSwipeToDismissMiniplayerPatch::newVideoLoaded)

    // endregion

    // region Hide cold start miniplayer text (R.string.mini_player_default_text)

    // The upstream injection replaces the compared object of the cold start check with the
    // extension result, which is null while the setting is on, so the text branch is skipped.
    failClosed("cold start text install") {
        ColdStartMiniplayerTextFingerprint.hookMethod {
            before {
                failClosed("cold start text") {
                    it.args[0] = EnableSwipeToDismissMiniplayerPatch.enableSwipeToDismissMiniplayer(it.args[0])
                }
            }
        }
    }

    // endregion

    // region Hide warm start miniplayer text (R.string.mini_player_default_text)

    // The upstream injection skips the section of the warm start callback that changes the
    // player bottom sheet when the miniplayer text is shown (from the watch-while layout
    // field read to past the bottom sheet call). At runtime the same section is suppressed
    // by skipping its two calls for the duration of the callback: the player state request
    // is dropped exactly like the direct requests of the watch-while layout while the
    // miniplayer is dismissed, and the bottom sheet call is skipped while the setting is on.
    failClosed("warm start text install") {
        val requestPlayerState: Method = WatchWhileLayoutRequestPlayerStateFingerprint.method
        val bottomSheet: Method = ::warmStartBottomSheetMethod.method

        val warmStartHook = object : ScopedHook() {}

        warmStartHook.hookInnerMethod(
            WatchWhileLayoutSetPlayerStateFingerprint.method,
            { param ->
                val state = param.args[0] as? Enum<*>
                val layout = outerParam.thisObject
                if (state != null && layout != null) {
                    failClosed("warm start text") {
                        val access = RuntimeWatchWhileLayoutAccess(layout, requestPlayerState)
                        if (EnableSwipeToDismissMiniplayerPatch.ignorePlayerStateRequest(access, state)) {
                            param.result = null
                        }
                    }
                }
            },
            { }
        )

        warmStartHook.hookInnerMethod(
            bottomSheet,
            { param ->
                if (EnableSwipeToDismissMiniplayerPatch.enableSwipeToDismissMiniplayer()) {
                    param.result = null
                }
            },
            { }
        )

        WarmStartMiniplayerTextFingerprint.hookMethod(warmStartHook)
    }

    // endregion

    // region Disable player page motion event

    if (is_9_43_or_greater) {
        // The motion field is now set directly from several places, but it is only read by
        // the touch methods. Clear it before they run.
        failClosed("player page motion install") {
            val motionField: Field = ::playerPageMotionField.field.apply { isAccessible = true }

            listOf(
                PlayerPageBehaviorInterceptTouchEventFingerprint,
                PlayerPageBehaviorTouchEventFingerprint
            ).forEach { fingerprint ->
                fingerprint.hookMethod {
                    before {
                        if (EnableSwipeToDismissMiniplayerPatch.enableSwipeToDismissMiniplayer()) {
                            motionField.setBoolean(it.thisObject, false)
                        }
                    }
                }
            }
        }
    } else {
        Logger.printDebug {
            "Swipe to dismiss: the player page behavior of this host version is not supported yet"
        }
    }

    // endregion
}
