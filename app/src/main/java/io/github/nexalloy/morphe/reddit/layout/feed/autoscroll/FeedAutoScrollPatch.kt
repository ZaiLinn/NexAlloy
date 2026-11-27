package io.github.nexalloy.morphe.reddit.layout.feed.autoscroll

import android.app.Activity
import android.os.Looper
import android.view.View
import app.morphe.extension.reddit.patches.FeedAutoScrollPatch
import app.morphe.extension.reddit.settings.Settings
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.ResourceType
import io.github.nexalloy.morphe.getResourceId
import io.github.nexalloy.morphe.reddit.layout.feed.NavBarStateChangeFingerprint
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.WeakHashMap

/** Port of Morphe #4958 for the Compose list and staggered-grid feeds. */
internal val feedAutoScroll = patch(
    name = "Feed auto-scroll",
    description = "Adds a button that gradually scrolls the feed.",
) {
    // The setting needs an app restart, so the composable and touch hooks are
    // not installed at all while it is off. The hooks sit on the feed list and
    // grid composables, which re-run on every recomposition, so the per-call
    // bridge overhead of a no-op hook is measurable during scrolling.
    if (!app.morphe.extension.reddit.settings.Settings.FEED_AUTO_SCROLL.get()) return@patch

    val list = ::listFeedComposable.method
    val grid = GridFeedFingerprint.method
    val gridStateCreator = ::gridStateCreator.method
    val visibilityEvent = FeedVisibilityEventFingerprint.constructor
    val activityResume = MainActivityOnResumeFingerprint.method
    val activityPause = MainActivityOnPauseFingerprint.method
    val barVisibility = NavBarStateChangeFingerprint.method
    val pagerInflate = PostDetailPagerInflateFingerprint.method
    val inflater = ::feedPostDetailInflater.method
    val screenAttach = ScreenAttachFingerprint.method
    val screenDetach = ScreenDetachFingerprint.method
    val pagerLayout = getResourceId(ResourceType.LAYOUT, "fragment_pager")

    val flowField = ::feedVisibilityFlowField.field.apply { isAccessible = true }
    val readVisibility = flowField.type.getMethod("getValue").apply { isAccessible = true }
    val feedType = classLoader.loadClass("com.reddit.feeds.data.FeedType")
    val listStateIndex = list.parameterTypes.indexOfFirst {
        it.name.startsWith("androidx.compose.foundation.lazy.")
    }
    val listContextIndex = list.parameterTypes.indexOf(flowField.declaringClass)
    val feedTypeIndex = list.parameterTypes.indexOf(feedType)
    val gridContextIndex = grid.parameterTypes.indexOf(flowField.declaringClass)
    check(listStateIndex >= 0 && listContextIndex >= 0 && feedTypeIndex >= 0 && gridContextIndex >= 0)

    fun dispatchDelta(stateClass: Class<*>): Method = stateClass.declaredMethods.single {
        it.returnType == Float::class.javaPrimitiveType &&
            it.parameterTypes.contentEquals(arrayOf(Float::class.javaPrimitiveType))
    }.apply { isAccessible = true }

    val listDispatchDelta = dispatchDelta(list.parameterTypes[listStateIndex])
    val gridDispatchDelta = dispatchDelta(gridStateCreator.returnType)

    // Upstream keys its feed map by flow and holds a weak reference to the scroll state.
    // Keep one wrapper per live host object, with a weak back-reference to avoid retaining it.
    val flows = WeakHashMap<Any, FeedAutoScrollPatch.VisibilityFlow>()
    val states = WeakHashMap<Any, FeedAutoScrollPatch.ScrollableState>()

    fun adaptFeed(context: Any, state: Any, dispatch: Method):
        Pair<FeedAutoScrollPatch.FeedContext, FeedAutoScrollPatch.ScrollableState>? {
        val flow = flowField.get(context) ?: return null
        val flowAdapter = flows.getOrPut(flow) {
            val reference = WeakReference(flow)
            FeedAutoScrollPatch.VisibilityFlow { reference.get()?.let(readVisibility::invoke) }
        }
        val stateAdapter = states.getOrPut(state) {
            val reference = WeakReference(state)
            FeedAutoScrollPatch.ScrollableState { delta ->
                reference.get()?.let { dispatch.invoke(it, delta) as Float } ?: 0f
            }
        }
        return FeedAutoScrollPatch.FeedContext { flowAdapter } to stateAdapter
    }

    fun canRegisterFeed() =
        Looper.myLooper() == Looper.getMainLooper() && Settings.FEED_AUTO_SCROLL.get()

    list.hookMethod {
        before {
            if (!canRegisterFeed()) return@before
            val context = it.args[listContextIndex] ?: return@before
            val state = it.args[listStateIndex] ?: return@before
            val (adaptedContext, adaptedState) = adaptFeed(context, state, listDispatchDelta)
                ?: return@before
            FeedAutoScrollPatch.onListFeed(adaptedContext, adaptedState, it.args[feedTypeIndex] as? Enum<*>)
        }
    }
    grid.hookMethod(scopedHook(gridStateCreator) {
        after {
            if (innerDepth != 0 || it.hasThrowable() || !canRegisterFeed()) return@after
            val context = outerParam.args[gridContextIndex] ?: return@after
            val state = it.result ?: return@after
            val (adaptedContext, adaptedState) = adaptFeed(context, state, gridDispatchDelta)
                ?: return@after
            FeedAutoScrollPatch.onGridFeed(adaptedContext, adaptedState)
        }
    })
    visibilityEvent.hookMethod {
        after {
            if (!it.hasThrowable() && canRegisterFeed()) FeedAutoScrollPatch.onFeedVisibilityChanged()
        }
    }

    activityResume.hookMethod {
        after {
            if (!it.hasThrowable()) FeedAutoScrollPatch.onMainActivityResumed(it.thisObject as Activity)
        }
    }
    activityPause.hookMethod {
        before { FeedAutoScrollPatch.onMainActivityPaused() }
    }
    barVisibility.hookMethod {
        before { FeedAutoScrollPatch.onNavBarStateChange(it.args[0] as Float) }
    }
    pagerInflate.hookMethod(scopedHook(inflater) {
        after {
            if (innerDepth == 0 && !it.hasThrowable() && it.args[0] == pagerLayout) {
                (it.result as? View)?.let(FeedAutoScrollPatch::onPostDetailPagerViewCreated)
            }
        }
    })
    screenAttach.hookMethod {
        before { FeedAutoScrollPatch.onScreenAttached(it.thisObject) }
    }
    screenDetach.hookMethod {
        before { FeedAutoScrollPatch.onScreenDetached(it.thisObject) }
    }
    setExtensionIsPatchIncluded(FeedAutoScrollPatch::class.java)
}
