package io.github.nexalloy.morphe.reddit.layout.hide

import app.morphe.extension.reddit.patches.AutoModeratorCommentsPatch
import app.morphe.extension.reddit.patches.AutoModeratorCommentsPatch.AutoModeratorComments
import app.morphe.extension.reddit.patches.ColorizeCommentIndentLinesPatch
import app.morphe.extension.reddit.patches.HideCommentFlairsAndBadgesPatch
import app.morphe.extension.reddit.settings.Settings
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.reddit.interaction.comments.resolveCommentCollapsedSetter
import io.github.nexalloy.morphe.reddit.misc.settings.SettingsHook
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook

val RedditHideCommentFlairsAndBadges = patch(
    name = "Hide comment flairs and badges",
    description = "Hides user flairs and community badges next to comment authors."
) {
    dependsOn(SettingsHook)
    val flairText = CommentAuthorFlairTextFingerprint.method
    val flairRichText = CommentAuthorFlairRichTextFingerprint.method
    val badge = CommentAuthorAchievementsBadgeFingerprint.method

    flairText.hookMethod {
        after {
            if (!it.hasThrowable()) {
                val original = it.result as String?
                val filtered = HideCommentFlairsAndBadgesPatch.getUserFlairText(original)
                if (filtered !== original) it.result = filtered
            }
        }
    }
    flairRichText.hookMethod {
        after {
            if (!it.hasThrowable()) {
                val original = it.result as List<*>?
                val filtered = HideCommentFlairsAndBadgesPatch.getUserFlairRichText(original)
                if (filtered !== original) it.result = filtered
            }
        }
    }
    badge.hookMethod {
        after {
            if (!it.hasThrowable()) {
                val original = it.result
                val filtered = HideCommentFlairsAndBadgesPatch.getCommunityBadge(original)
                if (filtered !== original) it.result = filtered
            }
        }
    }
    setExtensionIsPatchIncluded(HideCommentFlairsAndBadgesPatch::class.java)
}

val RedditAutoModeratorComments = patch(
    name = "AutoModerator comments",
    description = "Collapses or hides comments from u/AutoModerator."
) {
    dependsOn(SettingsHook)
    val comment = classLoader.loadClass("com.reddit.domain.model.Comment")
    val commentInterface = classLoader.loadClass("com.reddit.domain.model.IComment")
    val getAuthor = comment.getMethod("getAuthor")
    val getDepth = commentInterface.getMethod("getDepth")
    val setCollapsed = resolveCommentCollapsedSetter()
    val constructor = CommentsResultConstructorFingerprint.constructor
    val commentsIndex = constructor.parameterTypes.indexOf(List::class.java)
    check(commentsIndex >= 0)

    fun isAutoModerator(item: Any?): Boolean =
        comment.isInstance(item) && "AutoModerator".equals(getAuthor.invoke(item) as? String, ignoreCase = true)

    constructor.hookMethod {
        before {
            val comments = it.args[commentsIndex] as? List<*> ?: return@before
            when (Settings.AUTOMODERATOR_COMMENTS.get()) {
                AutoModeratorComments.SHOW -> Unit
                AutoModeratorComments.COLLAPSE -> {
                    for (item in comments) {
                        if (item != null && isAutoModerator(item)) setCollapsed(item, true)
                    }
                }
                AutoModeratorComments.HIDE -> {
                    var hiddenDepth: Int? = null
                    val filtered = comments.filter { item ->
                        if (!commentInterface.isInstance(item)) return@filter true
                        val depth = getDepth.invoke(item) as Int
                        if (hiddenDepth?.let { depth > it } == true) return@filter false
                        val hide = isAutoModerator(item)
                        hiddenDepth = if (hide) depth else null
                        !hide
                    }
                    if (filtered.size != comments.size) it.args[commentsIndex] = filtered
                }
            }
        }
    }
    setExtensionIsPatchIncluded(AutoModeratorCommentsPatch::class.java)
}

val RedditColorizeCommentIndentLines = patch(
    name = "Colorize comment indent lines",
    description = "Assigns a color to each comment reply level."
) {
    dependsOn(SettingsHook)
    val drawIndent = CommentIndentLinesFingerprint.method
    val drawLine = ::commentIndentDrawLine.method
    drawIndent.hookMethod(scopedHook(drawLine) {
        before {
            val depth = (outerParam.getObjectExtra("redditCommentIndentDepth") as? Int ?: 0) + 1
            outerParam.setObjectExtra("redditCommentIndentDepth", depth)
            it.args[1] = ColorizeCommentIndentLinesPatch.getLineColor(depth, it.args[1] as Long)
            it.args[7] = ColorizeCommentIndentLinesPatch.getLineAlpha(it.args[7] as Float)
        }
    })
    setExtensionIsPatchIncluded(ColorizeCommentIndentLinesPatch::class.java)
}
