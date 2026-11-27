package io.github.nexalloy.morphe.reddit.layout.hide

import app.morphe.extension.reddit.patches.HideInteractivePostsPatch
import app.morphe.extension.reddit.patches.HideNavigationButtonsPatch
import app.morphe.extension.reddit.settings.Settings
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.reddit.ad.ListingFingerprint
import io.github.nexalloy.morphe.reddit.layout.feed.FeedElementProcessorFingerprint
import io.github.nexalloy.morphe.reddit.misc.settings.SettingsHook
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch
import java.util.Collections

val RedditHideNavigationButtons = patch(
    name = "Hide navigation buttons",
    description = "Hides selected buttons in the bottom navigation bar."
) {
    dependsOn(SettingsHook)
    val buildButtons = BottomNavScreenListBuilderFingerprint.method
    val tabField = ::bottomNavTabField.field.apply { isAccessible = true }
    val immutableList = ::bottomNavImmutableList.method.apply { isAccessible = true }
    buildButtons.hookMethod {
        before {
            val tabs = it.args[0] as? Iterable<*> ?: return@before
            val visible = tabs.filter { item ->
                if (!tabField.declaringClass.isInstance(item)) return@filter true
                !HideNavigationButtonsPatch.hideNavigationTab(tabField.get(item) as? Enum<*>)
            }
            if (visible.size != tabs.count()) it.args[0] = immutableList.invoke(null, visible)
        }
    }
    setExtensionIsPatchIncluded(HideNavigationButtonsPatch::class.java)
}

private val feedTypesKeepingInteractivePosts = setOf(
    "SUBREDDIT", "GAMES", "ARENA", "CLUB", "SAVED_POSTS", "HISTORY",
    "SUBMITTED_POSTS", "SEARCH", "QUEUE", "PDP",
)

internal fun fullPostId(id: String): String = if (id.startsWith("t3_")) id else "t3_$id"

val RedditHideInteractivePosts = patch(
    name = "Hide interactive posts",
    description = "Hides interactive app posts from home and other recommended feeds."
) {
    dependsOn(SettingsHook)
    val customPost = CustomPostFeedElementConstructorFingerprint.constructor
    val feedElement = customPost.declaringClass.superclass
    val getLinkId = feedElement.getMethod("getLinkId")
    val processFeed = FeedElementProcessorFingerprint.method
    val link = classLoader.loadClass("com.reddit.domain.model.Link")
    val isCustom = link.getDeclaredField("isDevPlatformCustomPost").apply { isAccessible = true }
    val getFullId = link.getMethod("getKindWithId")
    val legacyListing = ListingFingerprint.constructor
    val children = legacyListing.declaringClass.getDeclaredField("children").apply { isAccessible = true }

    val trackedPosts = Collections.synchronizedMap(object : LinkedHashMap<String, Boolean>(1000, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean = size > 1000
    })

    customPost.hookMethod {
        after {
            if (!it.hasThrowable() && Settings.HIDE_INTERACTIVE_POSTS.get()) {
                val id = getLinkId.invoke(it.thisObject) as? String ?: return@after
                trackedPosts.putIfAbsent(fullPostId(id), false)
            }
        }
    }
    processFeed.hookMethod {
        before {
            if (!Settings.HIDE_INTERACTIVE_POSTS.get() || trackedPosts.isEmpty()) return@before
            val items = it.args[1] as? List<*> ?: return@before
            val keep = (it.args[0] as? Enum<*>)?.name in feedTypesKeepingInteractivePosts
            val filtered = items.filter { item ->
                if (!feedElement.isInstance(item)) return@filter true
                val id = (getLinkId.invoke(item) as? String)?.let(::fullPostId) ?: return@filter true
                if (!trackedPosts.containsKey(id)) return@filter true
                if (keep) trackedPosts[id] = true
                keep
            }
            if (filtered.size != items.size) it.args[1] = filtered
        }
    }
    legacyListing.hookMethod {
        after {
            if (it.hasThrowable() || !Settings.HIDE_INTERACTIVE_POSTS.get()) return@after
            val items = children.get(it.thisObject) as? List<*> ?: return@after
            if (items.size <= 1) return@after
            val filtered = items.filter { item ->
                if (!link.isInstance(item) || !isCustom.getBoolean(item)) return@filter true
                val id = (getFullId.invoke(item) as? String)?.let(::fullPostId)
                id != null && trackedPosts[id] == true
            }
            if (filtered.size != items.size) children.set(it.thisObject, filtered)
        }
    }
    setExtensionIsPatchIncluded(HideInteractivePostsPatch::class.java)
}
