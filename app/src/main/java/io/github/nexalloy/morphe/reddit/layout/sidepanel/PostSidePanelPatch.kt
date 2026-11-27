package io.github.nexalloy.morphe.reddit.layout.sidepanel

import android.content.Context
import android.view.View
import app.morphe.extension.reddit.patches.PostSidePanelPatch
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch

val RedditPostSidePanel = patch(
    name = "Post side panel",
    description = "Opens posts beside the feed on wide tablets.",
) {
    val presentation = PostDetailPresentationFingerprint.method
    val createDialog = ::panelPresentationConstructor.constructor.apply { isAccessible = true }
    val createView = ScreenCreateViewFingerprint.method
    val openWithTransition = OpenPostWithTransitionFingerprint.method
    val openWithoutTransition = ::openPostWithoutTransition.method.apply { isAccessible = true }
    val contextIndex = openWithTransition.parameterTypes.indexOfFirst { Context::class.java.isAssignableFrom(it) }
    val screenIndex = openWithTransition.parameterTypes.indexOfFirst {
        openWithoutTransition.parameterTypes[1].isAssignableFrom(it)
    }
    check(contextIndex >= 0 && screenIndex >= 0 && screenIndex != contextIndex)
    check(presentation.returnType.isAssignableFrom(createDialog.declaringClass))

    presentation.hookMethod {
        before {
            if (PostSidePanelPatch.usePanel(it.thisObject)) {
                it.result = createDialog.newInstance(true, null, null, 0x1e)
            }
        }
    }
    createView.hookMethod {
        after {
            if (!it.hasThrowable()) {
                val view = it.result as? View ?: return@after
                PostSidePanelPatch.onScreenViewCreated(it.thisObject, view)
            }
        }
    }
    openWithTransition.hookMethod {
        before {
            val screen = it.args[screenIndex] ?: return@before
            if (PostSidePanelPatch.usePanel(screen)) {
                openWithoutTransition.invoke(null, it.args[contextIndex], screen, null)
                it.result = null
            }
        }
    }
    setExtensionIsPatchIncluded(PostSidePanelPatch::class.java)
}
