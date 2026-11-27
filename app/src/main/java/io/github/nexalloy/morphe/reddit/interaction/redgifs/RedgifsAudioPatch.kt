package io.github.nexalloy.morphe.reddit.interaction.redgifs

import android.net.Uri
import app.morphe.extension.reddit.patches.RedgifsAudioPatch
import app.morphe.extension.reddit.settings.Settings
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook

internal val RedgifsAudio = patch(
    name = "Redgifs audio",
    description = "Plays original Redgifs videos with audio and retains Reddit's silent video as a fallback.",
) {
    val linkConstructor = RedgifsLinkConstructorFingerprint.constructor
    val link = linkConstructor.declaringClass
    val getUrl = link.getMethod("getUrl")
    val getPostId = link.getMethod("getKindWithId")
    val getPreview = link.getMethod("getPreview")
    val getMedia = link.getMethod("getMedia")
    val getVideoPreview = getPreview.returnType.getMethod("getRedditVideoPreview")
    val getMediaVideo = getMedia.returnType.getMethod("getRedditVideo")
    val videoUrls = listOf("getDashUrl", "getHlsUrl", "getFallBackUrl", "getDownloadUrl")
        .map { getVideoPreview.returnType.getMethod(it) }
    val metadataConstructor = MetadataCellConstructorFingerprint.constructor
    val metadataId = ::redgifsMetadataIdField.field.apply { isAccessible = true }
    val metadataPath = ::redgifsMetadataPathField.field.apply { isAccessible = true }
    val feedVideoConstructor = VideoElementConstructorFingerprint.constructor
    val feedVideoId = ::redgifsVideoPostIdField.field.apply { isAccessible = true }
    val feedVideoUrl = ::redgifsVideoUrlField.field.apply { isAccessible = true }
    val playerUrls = VideoUrlsConstructorFingerprint.constructor
    val domainUrls = DomainVideoUrlsConstructorFingerprint.constructor
    val dataSpec = DataSpecConstructorFingerprint.constructor
    val download = DownloadVideoFingerprint.method
    val downloadRequest = ::redgifsDownloadRequestConstructor.constructor

    linkConstructor.hookMethod {
        after {
            if (it.hasThrowable() || !Settings.REDGIFS_AUDIO.get()) return@after
            val post = it.thisObject
            val url = getUrl.invoke(post) as? String ?: return@after
            if (!url.contains("redgifs.com/")) return@after
            val preview = getPreview.invoke(post)?.let { getVideoPreview.invoke(it) }
            val media = getMedia.invoke(post)?.let { getMediaVideo.invoke(it) }
            if (preview == null && media == null) return@after
            val id = getPostId.invoke(post) as? String ?: return@after
            // The extension's feed hooks take plain strings and can also process values
            // read reflectively from Reddit's Link model.
            RedgifsAudioPatch.onFeedMetadataCellCreated("MetadataCell-$id", url)
            for (video in listOfNotNull(preview, media)) {
                for (accessor in videoUrls) {
                    RedgifsAudioPatch.onFeedVideoCreated(id, accessor.invoke(video) as? String)
                }
            }
        }
    }
    metadataConstructor.hookMethod {
        after {
            if (!it.hasThrowable()) {
                RedgifsAudioPatch.onFeedMetadataCellCreated(
                    metadataId.get(it.thisObject) as? String,
                    metadataPath.get(it.thisObject) as? String,
                )
            }
        }
    }
    feedVideoConstructor.hookMethod {
        after {
            if (!it.hasThrowable()) {
                RedgifsAudioPatch.onFeedVideoCreated(
                    feedVideoId.get(it.thisObject) as? String,
                    feedVideoUrl.get(it.thisObject) as? String,
                )
            }
        }
    }
    playerUrls.hookMethod {
        before { it.args[0] = RedgifsAudioPatch.getPlaybackUrl(it.args[0] as? String) }
    }
    domainUrls.hookMethod {
        before {
            val original = it.args[0] as? String ?: return@before
            val playback = RedgifsAudioPatch.getPlaybackUrl(original)
            if (playback != original) {
                it.args[0] = playback
                it.args[1] = null
                it.args[2] = null
            }
        }
    }
    dataSpec.hookMethod {
        before {
            val uri = it.args[0] as? Uri ?: return@before
            it.args[0] = RedgifsAudioPatch.resolvePlaceholderUri(uri)
        }
    }
    download.hookMethod(scopedHook(downloadRequest) {
        before {
            val uri = it.args[0] as? Uri ?: return@before
            if (innerDepth == 0) it.args[0] = RedgifsAudioPatch.getDownloadUri(uri)
        }
    })
    setExtensionIsPatchIncluded(RedgifsAudioPatch::class.java)
}
