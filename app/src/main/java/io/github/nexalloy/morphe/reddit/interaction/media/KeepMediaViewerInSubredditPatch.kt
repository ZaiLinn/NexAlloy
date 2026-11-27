/*
 * Adapted from Morphe's Keep media viewer in subreddit patch.
 * https://github.com/MorpheApp/morphe-patches/pull/5155
 */

package io.github.nexalloy.morphe.reddit.interaction.media

import app.morphe.extension.reddit.patches.KeepMediaViewerInSubredditPatch
import app.morphe.extension.reddit.settings.Settings
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook

internal val KeepMediaViewerInSubreddit = patch(
    name = "Keep media viewer in subreddit",
    description = "Limits media viewer swipes to posts from the original subreddit.",
) {
    val mediaContext = classLoader.loadClass("com.reddit.domain.model.media.MediaContext")
    val getListingType = mediaContext.getMethod("getListingType")
    val getSeedSubredditIds = mediaContext.getMethod("getSeedSubredditIds")
    val listingType = getListingType.returnType
    val subreddit = listingType.getField("SUBREDDIT").get(null)
    val userSubmitted = listingType.getField("USER_SUBMITTED").get(null)
    val submittedPosts = listingType.getField("SUBMITTED_POSTS").get(null)
    val link = classLoader.loadClass("com.reddit.domain.model.Link")
    val getSubredditId = link.getMethod("getSubredditId")
    val listingConstructor = ::mediaRequestListingConstructor.constructor

    val requests = listOf(
        RecommendedMediaRequestFingerprint.method to "getRecommendedMedia",
        RecommendedVideosRequestFingerprint.method to "getRecommendedVideos",
    ).map { (method, name) ->
        val continuation = classLoader.loadClass("com.reddit.data.remote.RemoteGqlLinkDataSource\$$name\$1")
        val contextField = continuation.getDeclaredField("L\$1").apply { isAccessible = true }
        val contextIndex = method.parameterTypes.indexOf(mediaContext)
        check(contextIndex >= 0)
        Triple(method, contextField, contextIndex)
    }

    fun subredditOf(context: Any?, type: Any?): String? {
        if (context == null || type == null || type === userSubmitted || type === submittedPosts) return null
        val ids = getSeedSubredditIds.invoke(context) as? List<*> ?: return null
        return ids.singleOrNull() as? String
    }

    for ((request, continuationContext, contextIndex) in requests) {
        request.hookMethod(scopedHook(
            getListingType to {
                after {
                    if (innerDepth == 0 && !it.hasThrowable() && Settings.KEEP_MEDIA_VIEWER_IN_SUBREDDIT.get() &&
                        subredditOf(it.thisObject, it.result) != null
                    ) {
                        it.result = subreddit
                    }
                }
            },
            listingConstructor to {
                before {
                    if (innerDepth != 0 || !Settings.KEEP_MEDIA_VIEWER_IN_SUBREDDIT.get()) return@before
                    val continuation = outerParam.args.lastOrNull()
                    val context = outerParam.args[contextIndex] ?: continuation
                        ?.takeIf(continuationContext.declaringClass::isInstance)
                        ?.let(continuationContext::get)
                        ?: return@before
                    // On resumption the input arguments are null, but Reddit keeps the media context
                    // in its own continuation. Use that context for the matching page.
                    val id = subredditOf(context, getListingType.invoke(context)) ?: return@before
                    val posts = it.args[0] as? List<*> ?: return@before
                    var foundMatchingPost = false
                    val filtered = posts.filter { post ->
                        if (!link.isInstance(post)) return@filter true
                        val matches = id == getSubredditId.invoke(post)
                        if (matches) foundMatchingPost = true
                        matches
                    }
                    if (filtered.size != posts.size) it.args[0] = filtered
                    if (posts.isNotEmpty() && !foundMatchingPost) it.args[1] = null
                }
            },
        ))
    }
    setExtensionIsPatchIncluded(KeepMediaViewerInSubredditPatch::class.java)
}
