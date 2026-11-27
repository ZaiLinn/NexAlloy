package io.github.nexalloy.morphe.reddit.layout.post

import app.morphe.extension.reddit.patches.FlipPostActionBarPatch
import app.morphe.extension.reddit.patches.FlipPostActionBarPatch.RowArrangement
import app.morphe.extension.reddit.patches.ShowViewCountPatch
import app.morphe.extension.reddit.settings.Settings
import io.github.nexalloy.HookDsl
import io.github.nexalloy.IScopedHookCallback
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.reddit.misc.flag.hookRedditFeatureFlag
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook
import java.lang.reflect.Member
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy

val RedditCustomizePostLayout = patch(
    name = "Customize post layout",
    description = "Controls post view counts and the order of the vote and comment buttons.",
) {
    val arrange = ArrangementHorizontalArrangeFingerprint.method
    val arrangementInterface = arrange.declaringClass
    val actionBar = PostActionBarRowFingerprint.method
    val vote = ::composePostVoteButtons.method.apply { isAccessible = true }
    val comment = ::composePostCommentButton.method
    val buttonsRow = ::composePostButtonsRow.method
    val commentRow = ::composePostCommentRow.method
    check(Modifier.isStatic(vote.modifiers))
    check(buttonsRow.parameterTypes[0].isAssignableFrom(arrangementInterface))
    check(commentRow.parameterTypes[0].isAssignableFrom(arrangementInterface))

    fun arrangeRow(delegate: RowArrangement): Any =
        Proxy.newProxyInstance(classLoader, arrayOf(arrangementInterface)) { proxy, method, args ->
            when {
                method == arrange -> {
                    delegate.arrange(
                        args[1] as Int, args[2] as IntArray, (args[3] as? Enum<*>)?.name == "Rtl",
                        args[4] as IntArray,
                    )
                    null
                }

                method.name == "equals" -> proxy === args?.firstOrNull()
                method.name == "hashCode" -> System.identityHashCode(proxy)
                method.name == "toString" -> delegate.toString()
                else -> throw UnsupportedOperationException(method.toString())
            }
        }

    val buttonsArrangement by lazy { arrangeRow(FlipPostActionBarPatch.getButtonsRowArrangement(null) as RowArrangement) }
    val commentArrangement by lazy { arrangeRow(FlipPostActionBarPatch.getCommentRowArrangement(null) as RowArrangement) }
    val actionBarHooks = mutableListOf<Pair<Member, HookDsl<IScopedHookCallback>.() -> Unit>>(
        vote to {
            before {
                if (innerDepth == 0 && FlipPostActionBarPatch.moveVoteButtons()) {
                    outerParam.setObjectExtra("redditMovedVote", it.args.copyOf())
                    it.result = null
                }
            }
        },
        comment to {
            before {
                if (innerDepth != 0) return@before
                @Suppress("UNCHECKED_CAST")
                val voteArgs = outerParam.getObjectExtra("redditMovedVote") as? Array<Any?> ?: return@before
                if (!Settings.HIDE_POST_VOTE_BUTTONS.get()) {
                    voteArgs[2] = null
                    voteArgs[3] = it.args[6]
                    voteArgs[4] = 0
                    vote.invoke(null, *voteArgs)
                }
            }
        },
    )
    val replaceArrangement: (Boolean) -> Any = { isComment ->
        if (isComment) commentArrangement else buttonsArrangement
    }
    if (buttonsRow == commentRow) {
        actionBarHooks += buttonsRow to {
            before {
                if (innerDepth == 0 && FlipPostActionBarPatch.moveVoteButtons()) {
                    val isComment = outerParam.getObjectExtra("redditActionBarRow") == true
                    it.args[0] = replaceArrangement(isComment)
                    outerParam.setObjectExtra("redditActionBarRow", true)
                }
            }
        }
    } else {
        for ((row, isComment) in listOf(buttonsRow to false, commentRow to true)) {
            actionBarHooks += row to {
                before {
                    if (innerDepth == 0 && FlipPostActionBarPatch.moveVoteButtons()) {
                        it.args[0] = replaceArrangement(isComment)
                    }
                }
            }
        }
    }

    actionBar.hookMethod(scopedHook(*actionBarHooks.toTypedArray()))
    hookRedditFeatureFlag(ShowViewCountPatch::showViewCount)
    setExtensionIsPatchIncluded(ShowViewCountPatch::class.java)
    setExtensionIsPatchIncluded(FlipPostActionBarPatch::class.java)
}
