package io.github.nexalloy.morphe.reddit.misc.sharecontent

import android.app.Activity
import android.content.Intent
import app.morphe.extension.reddit.patches.SharePostContentPatch
import app.morphe.extension.reddit.patches.SharePostContentPatch.PostContent
import app.morphe.extension.reddit.settings.Settings
import app.morphe.extension.reddit.utils.PostMedia
import app.morphe.extension.shared.Logger
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook
import java.util.concurrent.atomic.AtomicReference

val RedditSharePostContent = patch(
    name = "Share post content",
    description = "Shares the text, image or video of a post from Reddit's share menu.",
) {
    val subjects = ::sharePostSubjects.dexMethodList.map { it.toMethod() }
    val targets = ::sharePostTargets.dexMethodList.map { it.toMethod() }
    val linkType = classLoader.loadClass("com.reddit.domain.model.Link")
    val getSubjectSubreddit = linkType.getMethod("getSubredditNamePrefixed")
    val startActivity = Activity::class.java.getMethod("startActivity", Intent::class.java)
    val readPostContent = PostContentAdapter(linkType, classLoader)
    val pendingPost = AtomicReference<Any>()

    for (subject in subjects) {
        subject.hookMethod { before { pendingPost.set(null) } }
        subject.hookMethod(scopedHook(getSubjectSubreddit) {
            before {
                if (innerDepth == 0 && Settings.SHARE_POST_CONTENT.get()) {
                    pendingPost.set(it.thisObject)
                }
            }
        })
    }

    for (target in targets) {
        target.hookMethod(scopedHook(startActivity) {
            before {
                if (innerDepth != 0 || !Settings.SHARE_POST_CONTENT.get()) return@before
                val post = pendingPost.getAndSet(null) ?: return@before
                val activity = it.thisObject as Activity
                val chooser = it.args[0] as? Intent ?: return@before
                val content = runCatching { readPostContent(post) }.getOrElse { exception ->
                    Logger.printException({ "Could not read shared Reddit post" }, exception)
                    return@before
                }
                if (SharePostContentPatch.onShare(activity, chooser, content)) it.result = null
            }
        })
    }
    setExtensionIsPatchIncluded(SharePostContentPatch::class.java)
}

private class PostContentAdapter(linkType: Class<*>, classLoader: ClassLoader) {
    private val getCrossposts = linkType.getMethod("getCrossPostParentList")
    private val getMedia = linkType.getMethod("getMedia")
    private val getVideo = getMedia.returnType.getMethod("getRedditVideo")
    private val getVideoUrl = getVideo.returnType.getMethod("getDownloadUrl")
    private val getGallery = linkType.getMethod("getGallery")
    private val getItems = getGallery.returnType.getMethod("getItems")
    private val getItemUrl = classLoader.loadClass("com.reddit.domain.model.PostGalleryItem").getMethod("getUrl")
    private val getUrl = linkType.getMethod("getUrl")
    private val getHint = linkType.getMethod("getPostHint")
    private val isSelf = linkType.getMethod("isSelf")
    private val getTitle = linkType.getMethod("getTitle")
    private val getText = linkType.getMethod("getSelftext")

    operator fun invoke(link: Any): PostContent {
        val post = (getCrossposts.invoke(link) as? List<*>)?.firstOrNull() ?: link
        val videoUrl = getMedia.invoke(post)?.let { getVideo.invoke(it) }
            ?.let { getVideoUrl.invoke(it) as? String }
        val items = getGallery.invoke(post)?.let { getItems.invoke(it) as? List<*> }
        val galleryUrls = items.orEmpty().mapNotNull { item ->
            (getItemUrl.invoke(item) as? String)?.trim()?.takeIf(String::isNotEmpty)
                ?.let(PostMedia::toDownloadUrl)
        }
        return PostContent(
            videoUrl, galleryUrls, getUrl.invoke(post) as? String, getHint.invoke(post) as? String,
            isSelf.invoke(post) == true, getTitle.invoke(post) as? String, getText.invoke(post) as? String,
        )
    }
}
