package io.github.nexalloy.morphe.reddit.layout.feed

import android.view.View
import app.morphe.extension.reddit.patches.DisableBottomBarAutoHidePatch
import app.morphe.extension.reddit.patches.DisableHomeFeedAutoRefreshPatch
import app.morphe.extension.reddit.patches.DisablePostDetailSwipePatch
import app.morphe.extension.reddit.patches.DisableRefreshOnBackPatch
import app.morphe.extension.reddit.patches.KeepFeedPositionPatch
import io.github.nexalloy.HookDsl
import io.github.nexalloy.IScopedHookCallback
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook

/** Port of the automatic refresh event check in Morphe #5244. */
internal val disableHomeFeedAutoRefresh = patch(
    name = "Disable home feed auto-refresh",
    description = "Stops the home feed from refreshing and jumping to the top when returning to Reddit.",
) {
    val eventType = classLoader.loadClass("com.reddit.feeds.ui.events.OnAutoRefreshFeed")
    val eventDispatch = ::autoRefreshDispatchMethod.method
    check(eventDispatch.returnType == Void.TYPE)

    AutoRefreshFeedFingerprint.hookMethod(scopedHook(eventDispatch) {
        before {
            if (innerDepth == 0 && eventType.isInstance(it.args[0]) &&
                DisableHomeFeedAutoRefreshPatch.skipAutoRefresh()
            ) {
                it.result = null
            }
        }
    })
    setExtensionIsPatchIncluded(DisableHomeFeedAutoRefreshPatch::class.java)
}

/** Port of the home back-to-exit refresh check in Morphe #5245. */
internal val disableRefreshOnBack = patch(
    name = "Disable refresh on back to exit",
    description = "Lets Back exit from the home feed without first scrolling to the top and reloading.",
) {
    val disableBackRefresh: HookDsl<IScopedHookCallback>.() -> Unit = {
        after {
            if (innerDepth == 0 && !it.hasThrowable()) {
                it.result = DisableRefreshOnBackPatch.refreshOnBack(it.result as Boolean)
            }
        }
    }
    HomeBackPressedFingerprint.hookMethod(
        scopedHook(*::homeRefreshOnBackCallbackMethods.dexMethodList.map {
            it.toMethod() to disableBackRefresh
        }.toTypedArray()),
    )
    setExtensionIsPatchIncluded(DisableRefreshOnBackPatch::class.java)
}

/** Port of the last-visited post check in Morphe #3516. */
internal val keepFeedPosition = patch(
    name = "Keep feed position",
    description = "Keeps the current scroll position after closing a post, image or video.",
) {
    RememberOpenedPostFingerprint.hookMethod {
        before {
            if (KeepFeedPositionPatch.skipRememberOpenedPost()) it.result = null
        }
    }
    setExtensionIsPatchIncluded(KeepFeedPositionPatch::class.java)
}

/** Port of the Post Detail pager setup hook in Morphe #5295. */
internal val disablePostDetailSwipe = patch(
    name = "Disable post detail swipe",
    description = "Stops swiping sideways in a post from opening another post.",
) {
    PostDetailPagerSetupFingerprint.hookMethod(scopedHook(::postDetailPagerAdapterMethod.method) {
        after {
            val pager = it.thisObject as? View ?: return@after
            if (innerDepth == 0 && !it.hasThrowable()) {
                DisablePostDetailSwipePatch.onPostDetailPagerCreated(pager)
            }
        }
    })
    setExtensionIsPatchIncluded(DisablePostDetailSwipePatch::class.java)
}

/** Port of the navigation bar visibility adjustment in Morphe #5242. */
internal val disableBottomBarAutoHide = patch(
    name = "Disable bottom bar auto-hide",
    description = "Keeps the bottom navigation bar visible while scrolling.",
) {
    NavBarStateChangeFingerprint.hookMethod {
        before {
            it.args[0] = DisableBottomBarAutoHidePatch.getNavBarShownFraction(it.args[0] as Float)
        }
    }
    setExtensionIsPatchIncluded(DisableBottomBarAutoHidePatch::class.java)
}
