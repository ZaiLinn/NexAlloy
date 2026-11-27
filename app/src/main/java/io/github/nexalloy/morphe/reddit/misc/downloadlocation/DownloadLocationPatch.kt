package io.github.nexalloy.morphe.reddit.misc.downloadlocation

import android.app.DownloadManager
import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import app.morphe.extension.reddit.patches.DownloadLocationPatch
import app.morphe.extension.reddit.patches.DownloadLocationPatch.ImageDownloadParams
import app.morphe.extension.reddit.settings.Settings
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook

val RedditDownloadLocation = patch(
    name = "Download location",
    description = "Customizes where Reddit saves media and how the files are named.",
) {
    val linkConstructor = DownloadLinkConstructorFingerprint.constructor
    val downloadImage = DownloadImageFingerprint.method
    val downloadVideo = DownloadVideoFingerprint.method
    val saveImage = SaveImageFingerprint.method
    val saveSharedImage = SaveToGalleryFingerprint.method
    val paramsField = ::imageDownloadParamsField.field.apply { isAccessible = true }
    val urlField = ::imageDownloadUrlField.field.apply { isAccessible = true }
    val modelField = ::imageDownloadModelField.field.apply { isAccessible = true }
    val subredditField = ::downloadModelSubreddit.field.apply { isAccessible = true }
    val authorField = ::downloadModelAuthor.field.apply { isAccessible = true }

    val linkClass = linkConstructor.declaringClass
    val getMedia = linkClass.getMethod("getMedia")
    val getSubreddit = linkClass.getMethod("getSubreddit")
    val getAuthor = linkClass.getMethod("getAuthor")
    val getVideo = getMedia.returnType.getMethod("getRedditVideo")
    val getDownloadUrl = getVideo.returnType.getMethod("getDownloadUrl")
    val isGif = getVideo.returnType.getMethod("isGif")

    val requestType = DownloadManager.Request::class.java
    val createRequest = requestType.getConstructor(Uri::class.java)
    val setDestination = requestType.getMethod(
        "setDestinationInExternalPublicDir", String::class.java, String::class.java,
    )
    val enqueue = DownloadManager::class.java.getMethod("enqueue", requestType)
    val insert = ContentResolver::class.java.getMethod(
        "insert", Uri::class.java, ContentValues::class.java,
    )

    linkConstructor.hookMethod {
        after {
            if (it.hasThrowable() || !hasDownloadOptions()) return@after
            val link = it.thisObject
            val media = getMedia.invoke(link)
            val video = media?.let { value -> getVideo.invoke(value) }
            DownloadLocationPatch.recordVideoPost(
                video?.let { value -> getDownloadUrl.invoke(value) as? String },
                getSubreddit.invoke(link) as? String,
                getAuthor.invoke(link) as? String,
                video != null && isGif.invoke(video) == true,
            )
        }
    }

    downloadImage.hookMethod {
        before {
            if (!hasDownloadOptions()) return@before
            val params = paramsField.get(it.thisObject) ?: return@before
            DownloadLocationPatch.onImageSave(object : ImageDownloadParams {
                override fun patch_getUrl() = urlField.get(params) as String
                override fun patch_getSubreddit() = modelField.get(params)?.let(subredditField::get) as? String
                override fun patch_getAuthor() = modelField.get(params)?.let(authorField::get) as? String
            })
        }
        after { DownloadLocationPatch.clearDownloadContext() }
    }

    downloadVideo.hookMethod(scopedHook(
        createRequest to {
            before {
                if (innerDepth == 0) DownloadLocationPatch.onVideoDownload(it.args[0] as? Uri)
            }
        },
        setDestination to {
            before {
                if (innerDepth != 0 || !hasDownloadOptions()) return@before
                it.result = DownloadLocationPatch.setDestinationInExternalPublicDir(
                    it.thisObject as DownloadManager.Request,
                    it.args[0] as String,
                    it.args[1] as String,
                )
            }
        },
        enqueue to {
            before {
                if (innerDepth != 0 || !hasDownloadOptions()) return@before
                it.result = DownloadLocationPatch.enqueue(
                    it.thisObject as DownloadManager, it.args[0] as DownloadManager.Request,
                )
            }
        },
    ))

    saveImage.hookMethod(scopedHook(insert) {
        before {
            if (innerDepth != 0 || !hasDownloadOptions()) return@before
            it.result = DownloadLocationPatch.insertImage(
                it.thisObject as ContentResolver, it.args[0] as Uri, it.args[1] as ContentValues,
            )
        }
    })
    saveSharedImage.hookMethod(scopedHook(insert) {
        before {
            if (innerDepth != 0 || !hasDownloadOptions()) return@before
            it.result = DownloadLocationPatch.insertSharedImage(
                it.thisObject as ContentResolver, it.args[0] as Uri, it.args[1] as ContentValues,
            )
        }
    })
    setExtensionIsPatchIncluded(DownloadLocationPatch::class.java)
}

private fun hasDownloadOptions() = Settings.DOWNLOAD_FOLDER.get().isNotEmpty() ||
    Settings.DOWNLOAD_PATH_PATTERN.get().isNotEmpty() || Settings.DOWNLOAD_ACCOUNTS.get().isNotEmpty()
