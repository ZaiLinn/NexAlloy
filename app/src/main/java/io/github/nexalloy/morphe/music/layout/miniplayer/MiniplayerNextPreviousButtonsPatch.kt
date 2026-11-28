/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Runtime port of "Miniplayer previous and next buttons"
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/music/layout/miniplayer/MiniplayerNextPreviousButtonsPatch.kt).
 *
 * The upstream patch injects the two buttons into the host miniplayer layout XML, referencing
 * host resources only (the skip drawables, the button size and spacing dimens, the
 * accessibility strings and the MusicPlayerButton style). A runtime module cannot edit host
 * layout XML, so the same views are created programmatically from the same host resources
 * and inserted at the same positions (the previous button in front of the play/pause button,
 * the next button as the last child of the mini player), before the code that reads them
 * runs. The ids the extension looks the buttons up with live in the module resources.
 */

package io.github.nexalloy.morphe.music.layout.miniplayer

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import app.morphe.extension.music.patches.MiniplayerPreviousNextButtonsPatch
import app.morphe.extension.music.patches.lyrics.session.MiniplayerLyrics
import app.morphe.extension.shared.ResourceUtils
import app.morphe.extension.shared.ResourceType
import app.morphe.extension.shared.Logger
import de.robv.android.xposed.XC_MethodHook
import io.github.nexalloy.ScopedHook
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.music.misc.playservice.is_9_28_or_greater
import io.github.nexalloy.morphe.music.misc.playservice.versionCheckPatch
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.patch
import java.lang.reflect.Method

/**
 * The static helper the watch-while layout hands its view array to, which the upstream patch
 * extends with the injected buttons. The array sets the accessibility traversal order.
 * Anchor (9.46.51): `Lkwe;->aV([Landroid/view/View;)V`.
 */
internal val traversalOrderSetMethod = findMethodDirect {
    MppWatchWhileLayoutFingerprint.instructionMatches.last().instruction.methodRef!!
}

/**
 * Creates one of the injected buttons. The attributes are the ones the upstream layout patch
 * sets on the TouchImageView tag: the padding and size dimens, the skip drawable, the scale
 * type, the content description and the MusicPlayerButton style, which only sets the
 * actionBarItemBackground background and makes the view clickable and focusable.
 */
private fun createMiniplayerButton(
    context: android.content.Context,
    buttonId: Int,
    drawableName: String,
    descriptionName: String,
): ImageView {
    val buttonClass = runCatching {
        context.classLoader.loadClass("com.google.android.libraries.youtube.common.ui.TouchImageView")
    }.getOrNull()

    val button = if (buttonClass != null) {
        runCatching { buttonClass.getConstructor(android.content.Context::class.java).newInstance(context) }
            .getOrNull() as? ImageView
    } else {
        null
    } ?: ImageView(context)

    val resources = context.resources
    val size = resources.getDimensionPixelSize(ResourceUtils.getIdentifier(ResourceType.DIMEN, "remix_generic_button_size"))
    val padding = resources.getDimensionPixelSize(ResourceUtils.getIdentifier(ResourceType.DIMEN, "item_medium_spacing"))

    button.id = buttonId
    button.setPadding(padding, padding, padding, padding)
    button.scaleType = ImageView.ScaleType.FIT_CENTER
    button.setImageResource(ResourceUtils.getIdentifier(ResourceType.DRAWABLE, drawableName))
    button.contentDescription = resources.getString(
        ResourceUtils.getIdentifier(ResourceType.STRING, descriptionName)
    )
    button.isClickable = true
    button.isFocusable = true

    // The MusicPlayerButton style: android:background=?attr/actionBarItemBackground.
    runCatching {
        val typedValue = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.actionBarItemBackground, typedValue, true)) {
            val background = if (typedValue.resourceId != 0) {
                resources.getDrawable(typedValue.resourceId, context.theme)
            } else {
                null
            }
            background?.let { button.background = it }
        }
    }

    val layoutParams = FrameLayout.LayoutParams(size, size)
    button.layoutParams = layoutParams

    return button
}

/**
 * Injects the previous and next buttons into the mini player of the watch-while layout, at the
 * positions the upstream layout patch inserts the XML elements at: the previous button right
 * before the play/pause button of its parent, the next button as the last child of the mini
 * player container.
 */
private fun injectMiniplayerButtons(watchWhileLayout: View) {
    val miniPlayerId = ResourceUtils.getIdentifier(ResourceType.ID, "mini_player")
    val playPauseId = ResourceUtils.getIdentifier(ResourceType.ID, "mini_player_play_pause_replay_button")
    val previousButtonId = ResourceUtils.getIdentifier(ResourceType.ID, "mini_player_previous_button")
    val nextButtonId = ResourceUtils.getIdentifier(ResourceType.ID, "mini_player_next_button")

    val miniPlayer = watchWhileLayout.findViewById<ViewGroup>(miniPlayerId) ?: return
    val playPause = watchWhileLayout.findViewById<View>(playPauseId) ?: return
    if (miniPlayer.findViewById<View>(previousButtonId) != null &&
        miniPlayer.findViewById<View>(nextButtonId) != null
    ) {
        return // Already injected.
    }

    val context = watchWhileLayout.context
    val drawableName = if (is_9_28_or_greater) {
        "yt_fill_experimental_skip_previous_vd_theme_36"
    } else {
        "music_player_prev"
    }
    val nextDrawableName = if (is_9_28_or_greater) {
        "yt_fill_experimental_skip_next_vd_theme_36"
    } else {
        "music_player_next"
    }

    val previousButton = createMiniplayerButton(
        context, previousButtonId, drawableName, "accessibility_previous"
    )
    val nextButton = createMiniplayerButton(
        context, nextButtonId, nextDrawableName, "accessibility_next"
    )

    val parent = playPause.parent as? ViewGroup
    if (parent == null) {
        miniPlayer.addView(previousButton)
    } else {
        parent.addView(previousButton, parent.indexOfChild(playPause))
    }
    miniPlayer.addView(nextButton)
}

/**
 * Runtime port of "Miniplayer previous and next buttons".
 */
val miniplayerPreviousNextButtonsPatch = patch(
    name = "Miniplayer previous and next buttons",
    description = "Adds options to show previous and next track buttons in the miniplayer.",
) {
    dependsOn(versionCheckPatch)

    PreferenceScreen.addMiniplayerPreferences(
        SwitchPreference("morphe_music_miniplayer_next_button"),
        SwitchPreference("morphe_music_miniplayer_previous_button"),
    )

    fun failClosed(hook: String, block: () -> Unit) {
        runCatching(block).onFailure {
            Logger.printException({ "Miniplayer buttons hook failed: $hook" }, it)
        }
    }

    // region 1 - Miniplayer constructor: register onClick listeners for both buttons.
    // Upstream injects before the play/pause view lookup with the parent view; the extension
    // looks both buttons up with findViewById, so the whole watch-while layout works as well.
    MiniPlayerConstructorFingerprint.hookMethod {
        after {
            val watchWhileLayout = it.args.firstOrNull() as? View ?: return@after
            failClosed("button listeners") {
                MiniplayerPreviousNextButtonsPatch.setPreviousNextButtonOnClickListener(watchWhileLayout)
                // The lyrics miniplayer session also listens for the miniplayer view.
                MiniplayerLyrics.onMiniPlayerViewCreated(watchWhileLayout)
            }
        }
    }

    // region 2 - onFinishInflate: inject the buttons and extend the view array.
    // Upstream inserts the views into the layout XML and replaces the array of the traversal
    // order helper. At runtime the buttons are injected before the layout method runs, and
    // the array is extended for the duration of the method.
    failClosed("traversal order install") {
        val traversalOrder: Method = ::traversalOrderSetMethod.method

        val traversalHook = object : ScopedHook() {
            override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                super.beforeHookedMethod(param)
                failClosed("button injection") {
                    injectMiniplayerButtons(param.thisObject as View)
                }
            }
        }

        traversalHook.hookInnerMethod(
            traversalOrder,
            { param ->
                val watchWhileLayout = outerParam.thisObject as? View
                if (watchWhileLayout != null) {
                    @Suppress("UNCHECKED_CAST")
                    val views = param.args[0] as? Array<View>
                    if (views != null) {
                        param.args[0] = MiniplayerPreviousNextButtonsPatch.setPreviousNextButton(
                            watchWhileLayout, views
                        )
                    }
                }
            },
            { }
        )

        MppWatchWhileLayoutFingerprint.hookMethod(traversalHook)
    }
}
