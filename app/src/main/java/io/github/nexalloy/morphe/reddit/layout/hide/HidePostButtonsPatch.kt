package io.github.nexalloy.morphe.reddit.layout.hide

import app.morphe.extension.reddit.patches.HidePostButtonsPatch
import app.morphe.extension.reddit.settings.Settings
import de.robv.android.xposed.XC_MethodReplacement
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.reddit.misc.settings.SettingsHook
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

val RedditHidePostButtons = patch(
    name = "Hide post buttons",
    description = "Hides individual post actions across feeds, post details and the media viewer."
) {
    dependsOn(SettingsHook)
    val feedJoin = PostJoinButtonFingerprint.method
    val legacyHeader = PostDetailLegacyHeaderFingerprint.method
    val detailHeader = PostDetailMetadataHeaderFingerprint.method
    val joinState = ::postDetailJoinState.clazz
    val commentAward = CommentAwardButtonFingerprint.method
    val commentScoreHidden = CommentScoreHiddenFingerprint.method
    val voteState = ActionBarVoteStateConstructorFingerprint.constructor
    val voteScoreHidden = ::voteScoreHiddenField.field.apply { isAccessible = true }
    val shareLabel = ::shareActionLabel.field.apply { isAccessible = true }
    val actionBar = ::postActionBarComposable.method
    val actionBarButton = ActionBarButtonFingerprint.method
    val actionBarVoteButtons = ActionBarVoteButtonsFingerprint.method
    val railButton = ::mediaViewerRailButton.method
    val railVoteButtons = MediaViewerRailVoteButtonsFingerprint.method
    val postSurfaces = listOf<Fingerprint>(
        PostFooterFingerprint,
        LinkFooterActionBarFingerprint,
        PostDetailActionBarFingerprint,
        MediaViewerActionBarFingerprint,
    ).map { it.method }

    feedJoin.hookMethod { before { if (HidePostButtonsPatch.hideJoinButton()) it.result = null } }

    for ((header, rendererIndices) in listOf(legacyHeader to listOf(1), detailHeader to listOf(5, 6))) {
        header.hookMethod {
            before {
                if (!HidePostButtonsPatch.hideJoinButton()) return@before
                for (index in rendererIndices) {
                    val renderer = it.args[index] ?: continue
                    val rendererInterface = header.parameterTypes[index]
                    it.args[index] = Proxy.newProxyInstance(classLoader, arrayOf(rendererInterface)) { proxy, method, args ->
                        when {
                            method.name == "equals" -> proxy === args?.firstOrNull()
                            method.name == "hashCode" -> System.identityHashCode(proxy)
                            method.returnType == Void.TYPE && joinState.isInstance(args?.firstOrNull()) -> null
                            else -> try {
                                method.invoke(renderer, *args.orEmpty())
                            } catch (ex: InvocationTargetException) {
                                throw ex.targetException
                            }
                        }
                    }
                }
            }
        }
    }
    commentAward.hookMethod {
        before { if (HidePostButtonsPatch.hideCommentAwardButton()) it.result = null }
    }
    commentScoreHidden.hookMethod {
        after {
            if (!it.hasThrowable() && Settings.HIDE_POST_VOTE_COUNT.get()) it.result = true
        }
    }
    voteState.hookMethod {
        after {
            if (!it.hasThrowable() && Settings.HIDE_POST_VOTE_COUNT.get()) {
                voteScoreHidden.setBoolean(it.thisObject, true)
            }
        }
    }
    railButton.hookMethod {
        before {
            if (HidePostButtonsPatch.hideMediaViewerButton(it.args[3] as? String)) it.result = null
        }
    }
    railVoteButtons.hookMethod {
        before { if (HidePostButtonsPatch.hideMediaViewerVoteButtons()) it.result = null }
    }

    val postActionBarScope = scopedHook(actionBar) {
        before {
            it.setObjectExtra("redditPostActionBar", true)
            it.args[4] = HidePostButtonsPatch.getRepostButton(it.args[4])
            if (Settings.HIDE_POST_SHARE_BUTTON.get()) it.args[5] = null
            it.args[6] = HidePostButtonsPatch.getModButton(it.args[6])
            if (HidePostButtonsPatch.hideActionBar(it.args[4], it.args[5], it.args[6])) {
                it.result = null
            }
        }
    }
    for (surface in postSurfaces) surface.hookMethod(postActionBarScope)

    actionBar.hookMethod(scopedHook(
        actionBarButton to {
            before {
                if (outerParam.getObjectExtra("redditPostActionBar") != true) return@before
                val state = it.args[0] ?: return@before
                if (state === outerParam.args[1] && Settings.HIDE_POST_COMMENT_BUTTON.get()) {
                    it.result = null
                } else if (state === outerParam.args[5] && Settings.HIDE_POST_SHARE_COUNT.get()) {
                    it.setObjectExtra("redditShareLabel", shareLabel.get(state))
                    shareLabel.set(state, null)
                }
            }
            after {
                it.getObjectExtra("redditShareLabel")?.let { label -> shareLabel.set(it.args[0], label) }
            }
        },
        actionBarVoteButtons to {
            before {
                if (outerParam.getObjectExtra("redditPostActionBar") == true && Settings.HIDE_POST_VOTE_BUTTONS.get()) {
                    it.result = null
                }
            }
        },
    ))

    setExtensionIsPatchIncluded(HidePostButtonsPatch::class.java)
    HidePostButtonsPatch::class.java.getDeclaredMethod("isActionBarIncluded")
        .hookMethod(XC_MethodReplacement.returnConstant(true))
}
