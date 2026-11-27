package io.github.nexalloy.morphe.youtube.layout.hide.playeroverlaybuttons

import android.view.View
import android.widget.ImageView
import app.morphe.extension.shared.ResourceUtils.getIdIdentifier
import app.morphe.extension.shared.settings.preference.SeekBarPreference
import app.morphe.extension.youtube.patches.HidePlayerOverlayButtonsPatch
import io.github.nexalloy.morphe.getResourceId
import io.github.nexalloy.morphe.ResourceType
import io.github.nexalloy.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import io.github.nexalloy.morphe.youtube.insertLiteralOverride
import io.github.nexalloy.morphe.youtube.layout.buttons.overlay.addPlayerOverlayPreferences
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.morphe.youtube.misc.playservice.is_21_35_or_greater
import io.github.nexalloy.morphe.youtube.misc.playservice.is_21_42_or_greater
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook
import java.lang.reflect.Method

val HidePlayerOverlayButtons = patch(
    name = "Hide player overlay buttons",
    description = "Adds options to hide the player Cast, Autoplay, Captions, Previous & Next buttons, " +
            "and to hide or change the opacity of the player control buttons background.",
) {
    dependsOn(VersionCheck)

    addPlayerOverlayPreferences(
        noTitleUnsortedPreferenceCategory(
            SwitchPreference("morphe_hide_autoplay_button"),
            SwitchPreference("morphe_hide_captions_button"),
            SwitchPreference("morphe_hide_cast_button"),
            SwitchPreference("morphe_hide_collapse_button"),
            SwitchPreference("morphe_hide_fullscreen_button"),
            SwitchPreference("morphe_hide_player_control_buttons_background", summary = true),
            SwitchPreference("morphe_hide_player_previous_next_buttons"),
            SwitchPreference("morphe_hide_settings_button"),
        ),
        NonInteractivePreference(
            key = "morphe_player_control_buttons_background_opacity",
            tag = SeekBarPreference::class.java,
        )
    )

    // region Hide player previous/next & settings button.

    // Verify resources exist.
    getResourceId(ResourceType.ID, "player_control_next_button_touch_area")
    getResourceId(ResourceType.ID, "player_control_previous_button_touch_area")
    getResourceId(ResourceType.ID, "player_overflow_button")
    getResourceId(ResourceType.ID, "media_route_button")

    // Upstream inserts the extension calls with the receiver view of the matched call.
    // At runtime the call is scoped-hooked and the extension is called with its receiver.
    LayoutConstructorFingerprint.hookMethod(scopedHook(::playerControlsFindViewMethod.method) {
        before {
            if (innerDepth != 0) return@before
            val view = it.thisObject as View
            HidePlayerOverlayButtonsPatch.hideCastButton(view)
            HidePlayerOverlayButtonsPatch.hidePreviousNextButtons(view)
            HidePlayerOverlayButtonsPatch.hideSettingsButton(view)
        }
    })

    // endregion

    // region Hide cast button.

    if (is_21_42_or_greater) {
        // Upstream wraps the visibility argument of the setVisibility calls with the
        // extension method. At runtime the setVisibility calls are scoped-hooked and
        // the argument is wrapped instead.
        OverlayCastButtonVisibilityFingerprint.hookMethod(scopedHook(::castVisibilitySetMethod.method) {
            before {
                if (innerDepth == 0) {
                    it.args[0] = HidePlayerOverlayButtonsPatch.hideCastButton(it.args[0] as Int)
                }
            }
        })
    } else if (is_21_35_or_greater) {
        OverlayCastButtonVisibilityLegacy2135Fingerprint.hookMethod(
            scopedHook(::castVisibilityLegacySetMethod.method) {
                before {
                    if (innerDepth == 0) {
                        it.args[0] = HidePlayerOverlayButtonsPatch.hideCastButton(it.args[0] as Int)
                    }
                }
            }
        )
    } else {
        // 21.34 and lower inserts the extension call at a control flow label, which cannot
        // be reproduced at runtime. The supported target (21.45.82) does not reach this branch.
    }

    // The feature flags of the cast button in the player and the cast button in the feed.
    // Unlike upstream, which replaces the literal of the matched methods only, the runtime
    // literal override applies to every reader of the flag.
    insertLiteralOverride(45690091L, HidePlayerOverlayButtonsPatch::hideCastButton)
    insertLiteralOverride(45690090L) { HidePlayerOverlayButtonsPatch.hideCastButton(it) }

    // endregion

    // region Hide captions button.

    // Upstream inserts the extension call with the TouchImageView that was just read from a
    // field. At runtime the view is read from the field of the receiver of the method.
    val subtitleButtonField = ::subtitleButtonViewField.field
    SubtitleButtonControllerFingerprint.hookMethod {
        after {
            if (it.hasThrowable()) return@after
            val holder = subtitleButtonField.declaringClass
            val receiver = (listOf(it.thisObject) + it.args.toList())
                .firstOrNull { holder.isInstance(it) } ?: return@after
            val button = subtitleButtonField.get(receiver) as? ImageView ?: return@after
            HidePlayerOverlayButtonsPatch.hideCaptionsButton(button)
        }
    }

    // endregion

    // region Hide collapse button.

    val titleAnchorId = getIdIdentifier("title_anchor")
    val collapseButtonId = getIdIdentifier("player_collapse_button")

    // Upstream inserts the extension call with the title anchor view and the collapse button
    // view right after they are produced. At runtime the producing calls are scoped-hooked and
    // the extension is called with their result.
    TitleAnchorFingerprint.hookMethod(scopedHook(::titleAnchorViewMethod.method) {
        after {
            if (innerDepth != 0) return@after
            val view = it.result as? View ?: return@after
            if (view.id == titleAnchorId) {
                HidePlayerOverlayButtonsPatch.setTitleAnchorStartMargin(view)
            }
        }
    })
    TitleAnchorFingerprint.hookMethod(scopedHook(::collapseButtonViewMethod.method) {
        after {
            if (innerDepth != 0) return@after
            val button = it.result as? ImageView ?: return@after
            if (button.id == collapseButtonId) {
                HidePlayerOverlayButtonsPatch.hideCollapseButton(button)
            }
        }
    })

    // endregion

    // region Hide fullscreen button.

    // Upstream replaces the view register with the return value of the extension and returns
    // from the method when it returns null. At runtime the producing call is scoped-hooked and
    // its result is replaced instead; the button is already set to GONE in that case.
    FullscreenButtonFingerprint.hookMethod(scopedHook(::fullscreenButtonViewMethod.method) {
        after {
            if (innerDepth != 0) return@after
            val button = it.result as? ImageView ?: return@after
            if (button.id == getResourceId(ResourceType.ID, "fullscreen_button")) {
                it.result = HidePlayerOverlayButtonsPatch.hideFullscreenButton(button)
            }
        }
    })

    // endregion

    // region Hide player control buttons background.

    // Upstream replaces the result of the inflate call with the return value of the extension.
    // At runtime the inflate call is scoped-hooked and its result is replaced instead.
    InflateControlsGroupLayoutStubFingerprint.hookMethod(scopedHook(::controlsGroupInflateMethod.method) {
        after {
            if (innerDepth == 0) {
                it.result = HidePlayerOverlayButtonsPatch.styleControlButtonsBackground(it.result as? View)
            }
        }
    })

    // endregion

    // region Hide autoplay button.

    // Upstream jumps over the code that inflates and adds the autoplay toggle button when it is
    // hidden. At runtime the inflate call of the view stub is scoped-hooked and skipped instead.
    val skippedAutoplayToggleResult = skippedResult(::autoplayToggleShowMethod.method)
    LayoutConstructorFingerprint.hookMethod(scopedHook(::autoplayToggleShowMethod.method) {
        before {
            if (innerDepth == 0 && HidePlayerOverlayButtonsPatch.hideAutoplayButton()) {
                it.result = skippedAutoplayToggleResult
            }
        }
    })

    // endregion
}

/** The default value of a method return type, used to skip the original method body. */
private fun skippedResult(method: Method): Any? = when (method.returnType) {
    java.lang.Boolean.TYPE -> false
    java.lang.Byte.TYPE, java.lang.Character.TYPE, java.lang.Short.TYPE, Integer.TYPE -> 0
    java.lang.Long.TYPE -> 0L
    java.lang.Float.TYPE -> 0f
    java.lang.Double.TYPE -> 0.0
    else -> null
}
