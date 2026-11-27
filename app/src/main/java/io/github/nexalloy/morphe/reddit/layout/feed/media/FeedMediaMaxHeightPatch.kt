package io.github.nexalloy.morphe.reddit.layout.feed.media

import app.morphe.extension.reddit.patches.FeedMediaMaxHeightPatch
import io.github.nexalloy.MethodHookParam
import io.github.nexalloy.ScopedHook
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook

private const val DEFAULT_HEIGHT_RATIO = 4f / 3f
private const val DENSITY = "nexAlloyFeedMediaDensity"
private const val INSET = "nexAlloyFeedMediaInset"
private const val VIDEO = "nexAlloyFeedVideoSource"

private fun maxHeightRatio() = FeedMediaMaxHeightPatch.getMaxHeightRatio()
    .takeIf(Float::isFinite) ?: DEFAULT_HEIGHT_RATIO

/** Port of Morphe #3516 for the 2026.39.1 image, gallery and video sizing methods. */
internal val feedMediaMaxHeight = patch(
    name = "Feed media max height",
    description = "Changes the maximum height of feed images, videos and galleries.",
) {
    val galleryImageHeight = GalleryHeightFingerprint.method
    val galleryHeightForWidth = FeedGalleryHeightForWidthFingerprint.method
    val imageMaxHeight = FeedImageMaxHeightFingerprint.method
    val imageSize = FeedImageSizeFingerprint.method
    val videoHeight = FeedVideoHeightFingerprint.method
    val video = FeedVideoFingerprint.method
    val densityRound = ::mediaDensityRound.method
    val widthField = ::insetMediaViewportWidth.field.apply { isAccessible = true }
    val coercion = ::imageHeightCoercionMethod.method
    val (imageWidth, imageHeight) = ::galleryImageSizeFields.fieldList.onEach { it.isAccessible = true }
    val (videoData, sourceWidth, sourceHeight, toDp) = ::feedVideoAspectMethods.dexMethodList.map {
        it.toMethod().apply { isAccessible = true }
    }
    val (videoFactory, videoViewport) = ::feedVideoViewportMethods.dexMethodList.map { it.toMethod() }

    imageSize.hookMethod(scopedHook(coercion) {
        before {
            if (innerDepth != 0 || (outerParam.args[5] as? Float)?.let { it > 0f } == true) return@before
            it.args[0] = (it.args[0] as Double) / DEFAULT_HEIGHT_RATIO * maxHeightRatio()
        }
    })

    // Capture and apply in one callback per height method. Previously each method
    // registered a scope callback and a separate result callback.
    val insetCapture = object : ScopedHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            try {
                if (param.hasThrowable()) return
                val inset = param.getObjectExtra(INSET) as? Int ?: return
                val width = widthField.getInt(param.args[1]) - inset
                val ratio = maxHeightRatio()
                if (param.method == imageMaxHeight) {
                    param.result = minOf(param.args[0] as Int, (width * ratio).toInt())
                } else {
                    val density = param.getObjectExtra(DENSITY) ?: return
                    val source = videoData.invoke(param.args[0]) ?: return
                    val aspect = (sourceWidth.invoke(source) as Int).toFloat() / (sourceHeight.invoke(source) as Int)
                    val height = minOf((width / aspect).toInt(), (width * ratio).toInt())
                    param.result = toDp.invoke(density, height)
                }
            } finally {
                // Always clear the thread-local scope, including early returns and errors.
                super.afterHookedMethod(param)
            }
        }
    }
    insetCapture.hookInnerMethod(densityRound, {}, {
        if (innerDepth == 0 && !it.hasThrowable() && it.args[0] == 32f) {
            outerParam.setObjectExtra(DENSITY, it.thisObject)
            outerParam.setObjectExtra(INSET, it.result)
        }
    })
    imageMaxHeight.hookMethod(insetCapture)
    videoHeight.hookMethod(insetCapture)

    galleryImageHeight.hookMethod {
        before {
            val width = it.args[0] as Int
            val images = it.args[1] as? List<*> ?: return@before
            var widest = 0
            var tallest = 0
            for (image in images) {
                if (!imageWidth.declaringClass.isInstance(image)) return@before
                widest = maxOf(widest, imageWidth.getInt(image))
                tallest = maxOf(tallest, imageHeight.getInt(image))
            }
            val cap = width * maxHeightRatio()
            val scaled = if (widest == 0) cap else width * (tallest.toFloat() / widest)
            it.result = Math.round(minOf(scaled, cap))
        }
    }
    galleryHeightForWidth.hookMethod {
        after {
            if (it.hasThrowable() || it.args[0] != false) return@after
            val maxHeight = ((it.args[3] as Int) * maxHeightRatio()).toInt()
            it.result = minOf(it.args[1] as Int, maxHeight).toFloat()
        }
    }

    video.hookMethod(scopedHook(
        videoFactory to {
            after {
                if (innerDepth == 0 && !it.hasThrowable()) outerParam.setObjectExtra(VIDEO, it.result)
            }
        },
        videoViewport to {
            before {
                if (innerDepth != 0) return@before
                val source = outerParam.getObjectExtra(VIDEO)?.let { videoData.invoke(it) }
                    ?: return@before
                outerParam.setObjectExtra(VIDEO, null)
                val requestedCap = outerParam.args[20] as? Float ?: return@before
                val displayWidth = it.args[0] as Float
                if (!displayWidth.isFinite() || displayWidth <= 0f) return@before
                val width = sourceWidth.invoke(source) as Int
                val height = sourceHeight.invoke(source) as Int
                val ratio = maxHeightRatio()
                val naturalRatio = if (width > 0 && height > 0) height.toFloat() / width else ratio
                it.args[1] = displayWidth * minOf(naturalRatio, ratio, requestedCap)
            }
        },
    ))
    setExtensionIsPatchIncluded(FeedMediaMaxHeightPatch::class.java)
}
