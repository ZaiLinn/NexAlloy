package io.github.nexalloy.morphe.reddit.misc.privacy

import android.content.ClipData
import android.content.Intent
import app.morphe.extension.reddit.patches.SanitizeSharingLinksPatch
import app.morphe.extension.shared.Logger
import io.github.nexalloy.hookMethod
import io.github.nexalloy.patch

val SanitizeSharingLinks = patch(
    name = "Sanitize sharing links",
    description = "Adds an option to sanitize sharing links by removing tracking query parameters."
) {
    ShareLinkFormatterFingerprint.hookMethod {
        before {
            if (SanitizeSharingLinksPatch.stripQueryParameters()) {
                it.result = it.args[0]
            }
        }
    }

    // Fallback for the share paths that bypass the formatter above: the clipboard and the
    // ACTION_SEND extras hold the same link text, but never pass it through the formatter.
    // Unlike the formatter hook, which empties the whole query, the extension's cleaner
    // removes only the known tracking parameters and only from Reddit links.

    // The framework method every clipboard write of the app goes through, including the
    // deprecated ClipboardManager.setText(). The items are only replaced when a text of
    // the clip holds a Reddit link with a tracking parameter; the untouched clip and its
    // non-text items (URI, Intent) pass through as the same objects.
    runCatching {
        android.content.ClipboardManager::class.java
            .getDeclaredMethod("setPrimaryClip", ClipData::class.java)
            .hookMethod {
                before {
                    val clip = it.args[0] as? ClipData ?: return@before
                    val itemCount = clip.itemCount
                    if (itemCount == 0) return@before

                    // Detection pass first, so the common clip without a Reddit link returns
                    // without allocating anything.
                    var holdsTrackedLink = false
                    for (index in 0 until itemCount) {
                        val text = clip.getItemAt(index).text
                        if (text is String && SanitizeSharingLinksPatch.cleanShareText(text) !== text) {
                            holdsTrackedLink = true
                            break
                        }
                    }
                    if (!holdsTrackedLink) return@before

                    // Rebuild pass. The copy keeps the original description (label, MIME
                    // types, extras) and the unchanged items as the same objects.
                    val description = clip.description
                    var cleanedClip: ClipData? = null
                    for (index in 0 until itemCount) {
                        val item = clip.getItemAt(index)
                        val text = item.text
                        val newItem = if (text is String) {
                            val cleaned = SanitizeSharingLinksPatch.cleanShareText(text)
                            if (cleaned !== text) {
                                ClipData.Item(cleaned, item.htmlText, item.intent, item.uri)
                            } else {
                                item
                            }
                        } else {
                            item
                        }

                        if (cleanedClip == null) {
                            cleanedClip = ClipData(description, newItem)
                        } else {
                            cleanedClip.addItem(newItem)
                        }
                    }
                    it.args[0] = cleanedClip
                }
            }
    }.onFailure { Logger.printException({ "Sanitize sharing links: clipboard hook failed" }, it) }

    // Share sheets and third-party share targets receive the link text through the
    // EXTRA_TEXT extra. The extra name check runs for every putExtra call of the app and
    // stays first, so the hook costs a single String comparison on the hot path.
    runCatching {
        val intentClass = Intent::class.java
        val putExtraMethods = listOf(
            intentClass.getDeclaredMethod("putExtra", String::class.java, String::class.java),
            intentClass.getDeclaredMethod("putExtra", String::class.java, CharSequence::class.java),
        )
        for (putExtra in putExtraMethods) {
            putExtra.hookMethod {
                before {
                    if (it.args[0] != Intent.EXTRA_TEXT) return@before
                    // A styled CharSequence is rare in the share flow and is left alone,
                    // as replacing it with the cleaned plain String would drop its spans.
                    val text = it.args[1] as? String ?: return@before
                    val cleaned = SanitizeSharingLinksPatch.cleanShareText(text)
                    if (cleaned !== text) {
                        it.args[1] = cleaned
                    }
                }
            }
        }
    }.onFailure { Logger.printException({ "Sanitize sharing links: putExtra hook failed" }, it) }
}
