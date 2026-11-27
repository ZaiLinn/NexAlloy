package io.github.nexalloy.morphe.reddit.interaction.media

import app.morphe.extension.reddit.patches.HideMediaViewerComponentsPatch
import app.morphe.extension.reddit.patches.MediaViewerBlackBackgroundPatch
import app.morphe.extension.reddit.patches.MediaViewerFadePatch
import app.morphe.extension.reddit.patches.SwipeUpForCommentsPatch
import de.robv.android.xposed.XC_MethodReplacement
import io.github.nexalloy.HookDsl
import io.github.nexalloy.IScopedHookCallback
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook
import java.lang.reflect.Member

internal val SwipeUpForComments = patch(
    name = "Swipe up for comments",
    description = "Opens comments by swiping up and uses sideways swipes for the next post.",
) {
    val discriminator = ::verticalChainingDiscriminator.field.apply { isAccessible = true }
    VerticalChainingFingerprint.hookMethod {
        after {
            // R8 merged multiple settings into one lambda. Case 7 is the chaining check.
            if (!it.hasThrowable() && discriminator.getInt(it.thisObject) == 7) {
                it.result = SwipeUpForCommentsPatch.allowVerticalChaining(it.result as Boolean)
            }
        }
    }
    setExtensionIsPatchIncluded(SwipeUpForCommentsPatch::class.java)
}

internal val MediaViewerFade = patch(
    name = "Media viewer fade",
    description = "Controls the dark fade behind captions in the media viewer.",
) {
    val chrome = MediaViewerChromeFingerprint.method
    val colorCopy = ::mediaViewerColorCopy.method
    val styleIndex = chrome.parameterTypes.indexOfFirst {
        it.name == "com.reddit.fullbleedplayer.FbpChromeScrimStyle"
    }
    check(styleIndex >= 0)
    val legacyStyle = chrome.parameterTypes[styleIndex].getField("LEGACY_GRADIENT").get(null)
    chrome.hookMethod(scopedHook(colorCopy) {
        before {
            if (innerDepth == 0) it.args[1] = MediaViewerFadePatch.scaleFadeAlpha(it.args[1] as Float)
        }
    })
    chrome.hookMethod {
        before {
            if (MediaViewerFadePatch.useLegacyFadeStyle()) it.args[styleIndex] = legacyStyle
        }
    }
    setExtensionIsPatchIncluded(MediaViewerFadePatch::class.java)
}

internal val MediaViewerBlackBackground = patch(
    name = "Media viewer black background",
    description = "Uses black behind images and videos in the media viewer.",
) {
    val backgroundMethods = ::mediaViewerBackgroundMethods.dexMethodList.map { it.toMethod() }
        .plus(MediaViewerBottomSheetMenuFingerprint.method)
        .distinct()
    val backgroundCalls = ::mediaViewerBackgroundCalls.dexMethodList.map { it.toMethod() }
    val screenDiscriminator = ::inlineMediaViewerBackgroundDiscriminator.field.apply { isAccessible = true }
    val gradient = MediaViewerTopGradientFingerprint.method
    val gradientColor = ::mediaViewerTopGradientColor.method
    val player = VideoPlayerBackgroundFingerprint.method
    val readLocal = ::videoPlayerReadCompositionLocal.method.apply { isAccessible = true }
    val localContext = ::androidLocalContext.field.apply { isAccessible = true }
    val playerBackground = ::videoPlayerBackgroundCall.method
    val composerIndex = player.parameterTypes.indexOfLast { it.isAssignableFrom(readLocal.declaringClass) }
    check(composerIndex >= 0)

    val blackBackgrounds = backgroundCalls.map { method ->
        val behavior: HookDsl<IScopedHookCallback>.() -> Unit = {
            before { param ->
                if (innerDepth != 0) return@before
                val screen = outerParam.thisObject
                // Reddit's alternate player screen shares an R8 lambda with unrelated screens.
                if (screenDiscriminator.declaringClass.isInstance(screen) &&
                    screenDiscriminator.getInt(screen) != 13
                ) return@before
                param.args[1] = MediaViewerBlackBackgroundPatch.getBackgroundColor(param.args[1] as Long)
            }
        }
        (method as Member) to behavior
    }.toTypedArray()
    for (method in backgroundMethods) method.hookMethod(scopedHook(*blackBackgrounds))
    gradient.hookMethod(scopedHook(gradientColor) {
        after {
            if (innerDepth == 0 && !it.hasThrowable()) {
                it.result = MediaViewerBlackBackgroundPatch.getBackgroundColor(it.result as Long)
            }
        }
    })
    player.hookMethod(scopedHook(playerBackground) {
        before {
            if (innerDepth != 0) return@before
            val original = it.args[1] as Long
            if (MediaViewerBlackBackgroundPatch.getBackgroundColor(original) == original) return@before
            val composer = outerParam.args[composerIndex]
            if (!readLocal.declaringClass.isInstance(composer)) return@before
            val context = readLocal.invoke(composer, localContext.get(null))
            it.args[1] = MediaViewerBlackBackgroundPatch.getVideoPlayerBackgroundColor(original, context)
        }
    })
    setExtensionIsPatchIncluded(MediaViewerBlackBackgroundPatch::class.java)
}

internal val HideMediaViewerComponents = patch(
    name = "Hide media viewer components",
    description = "Adds options to hide the conversation dock or the media viewer overlay.",
) {
    val creator = CreateFullBleedChromeStateFingerprint.method
    val visible = ::mediaChromeVisibleField.field.apply { isAccessible = true }
    val dock = JoinConversationControlsFingerprint.method
    val dockLayout = JoinConversationLayoutFingerprint.method
    val bottomPadding = ::joinConversationBottomPadding.method
    val statusBarPadding = ::mediaViewerStatusBarPadding.method.apply { isAccessible = true }
    val page = MediaViewerPageFingerprint.method
    val modifierIndex = page.parameterTypes.indexOf(statusBarPadding.parameterTypes.single())
    check(modifierIndex >= 0)

    creator.hookMethod {
        after {
            if (!it.hasThrowable() && HideMediaViewerComponentsPatch.hideOverlay()) {
                it.result?.let { state -> visible.setBoolean(state, false) }
            }
        }
    }
    dock.hookMethod {
        before {
            if (!HideMediaViewerComponentsPatch.showJoinConversationButton(true)) it.result = null
        }
    }
    dockLayout.hookMethod(scopedHook(bottomPadding) {
        before {
            if (innerDepth == 0 && !HideMediaViewerComponentsPatch.showJoinConversationButton(true)) {
                it.args[4] = 0f
            }
        }
    })
    page.hookMethod {
        before {
            if (!HideMediaViewerComponentsPatch.skipStatusBarPadding(true)) {
                it.args[modifierIndex] = statusBarPadding.invoke(null, it.args[modifierIndex])
            }
        }
    }
    HideMediaViewerComponentsPatch::class.java.getDeclaredMethod("isHideOverlayIncluded")
        .hookMethod(XC_MethodReplacement.returnConstant(true))
    setExtensionIsPatchIncluded(HideMediaViewerComponentsPatch::class.java)
}
