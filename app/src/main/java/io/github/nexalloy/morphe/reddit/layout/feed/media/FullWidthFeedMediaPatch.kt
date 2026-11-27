package io.github.nexalloy.morphe.reddit.layout.feed.media

import app.morphe.extension.reddit.patches.FullWidthFeedMediaPatch
import app.morphe.extension.reddit.settings.Settings
import io.github.nexalloy.HookDsl
import io.github.nexalloy.IScopedHookCallback
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook

/** Port of Morphe #3516 for feed and post-detail media in Reddit 2026.39.1. */
internal val fullWidthFeedMedia = patch(
    name = "Full width feed media",
    description = "Shows images, videos and galleries edge to edge in the feed and post details.",
) {
    // This setting already requires a restart. Keep the feature visible in settings,
    // but avoid hooking shared density/shape methods while it is disabled.
    setExtensionIsPatchIncluded(FullWidthFeedMediaPatch::class.java)
    if (!Settings.FULL_WIDTH_FEED_MEDIA.get()) return@patch

    val styleGetters = ::mediaInsetGetterMethods.dexMethodList.map { it.toMethod() }
    val densityRound = ::mediaDensityRound.method
    val mediaSizes = listOf(
        FeedImageSizeFingerprint.method,
        FeedImageMaxHeightFingerprint.method,
        FeedVideoHeightFingerprint.method,
        FeedGalleryHeightFingerprint.method,
    )
    val youtube = YouTubeVideoSectionContentFingerprint.method
    val youtubeSection = YouTubeVideoSectionConstructorFingerprint.constructor
    val youtubePadding = ::feedYouTubePaddingMethod.method
    val youtubeShape = ::feedYouTubeShapeMethod.method
    val imageProps = PostImagePropsConstructorFingerprint.constructor
    val contentLambda = PostContentLambdaConstructorLegacyFingerprint.constructor
    val postContent = ::postContentField.field.apply { isAccessible = true }
    val videoContent = ::gifAndVideoContent.clazz
    val video = PostDetailVideoFingerprint.method
    val gallery = PostDetailGalleryLegacyFingerprint.method
    val embeddedVideo = PostDetailEmbedVideoFingerprint.method
    val paddingDp = ::postDetailHorizontalPaddingMethod.method
    val embeddedShape = ::postDetailEmbeddedShapeMethod.method

    val mediaInset: HookDsl<IScopedHookCallback>.() -> Unit = {
        before {
            // The other conversions in the same composable keep their regular density.
            if (innerDepth == 0 && it.args[0] == 32f && Settings.FULL_WIDTH_FEED_MEDIA.get()) it.result = 0
        }
    }
    val skipShape: HookDsl<IScopedHookCallback>.() -> Unit = {
        before {
            if (innerDepth == 0 && Settings.FULL_WIDTH_FEED_MEDIA.get()) it.result = it.args[0]
        }
    }

    styleGetters.forEach { getter ->
        getter.hookMethod {
            after {
                if (!it.hasThrowable()) {
                    it.result = FullWidthFeedMediaPatch.isMediaInsetEnabled(it.result as Boolean)
                }
            }
        }
    }
    val insetHook = scopedHook(densityRound to mediaInset)
    mediaSizes.forEach { it.hookMethod(insetHook) }
    youtube.hookMethod(scopedHook(
        densityRound to mediaInset,
        youtubeShape to skipShape,
        youtubePadding to {
            before {
                if (innerDepth == 0 && it.args[1] == 16f) {
                    it.args[1] = FullWidthFeedMediaPatch.getMediaSidePadding(16f)
                }
            }
        },
    ))
    youtubeSection.hookMethod {
        before { it.args[5] = FullWidthFeedMediaPatch.isMediaInsetEnabled(it.args[5] as Boolean) }
    }
    imageProps.hookMethod {
        before { it.args[5] = FullWidthFeedMediaPatch.isMediaInsetEnabled(it.args[5] as Boolean) }
    }
    contentLambda.hookMethod {
        before {
            if (it.args[4] == true && Settings.FULL_WIDTH_FEED_MEDIA.get() &&
                videoContent.isInstance(postContent.get(it.args[0]))
            ) {
                it.args[4] = false
            }
        }
    }
    video.hookMethod {
        before {
            val width = it.args[2] as Int
            val height = it.args[3] as Int
            it.args[3] = FullWidthFeedMediaPatch.getPostVideoHeight(width, height)
            it.args[2] = FullWidthFeedMediaPatch.getPostVideoWidth(width)
            it.args[14] = FullWidthFeedMediaPatch.isMediaInsetEnabled(it.args[14] as Boolean)
        }
    }
    gallery.hookMethod {
        before { it.args[13] = FullWidthFeedMediaPatch.isMediaInsetEnabled(it.args[13] as Boolean) }
    }
    embeddedVideo.hookMethod(scopedHook(
        embeddedShape to skipShape,
        paddingDp to {
            after {
                if (innerDepth == 0 && !it.hasThrowable()) {
                    it.result = FullWidthFeedMediaPatch.getMediaSidePadding(it.result as Float)
                }
            }
        },
    ))
}
