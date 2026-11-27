package io.github.nexalloy.morphe.reddit.interaction.dialogs

import app.morphe.extension.reddit.patches.RemoveSubRedditDialogPatch
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook

internal val RemoveSubredditDialog = patch(
    name = "Remove subreddit dialog",
    description = "Adds options to suppress subreddit notification prompts and repeat NSFW warnings.",
) {
    val frequentUpdates = FrequentUpdatesHandlerFingerprint.method
    val loginCheck = ::frequentUpdatesLoginCheck.method
    val nsfwAlert = NSFWAlertEmitFingerprint.method
    val visitedCheck = ::nsfwVisitedCheck.method
    val dialogs = ::nsfwAlertDialogMethods.dexMethodList.map { it.toMethod() }
    val createDialogs = ::nsfwAlertCreateMethods.dexMethodList.map { it.toMethod() }
    check(dialogs.size == createDialogs.size)

    frequentUpdates.hookMethod(scopedHook(loginCheck) {
        after {
            if (innerDepth == 0 && !it.hasThrowable()) {
                it.result = RemoveSubRedditDialogPatch.spoofLoggedInStatus(it.result as Boolean)
            }
        }
    })
    nsfwAlert.hookMethod(scopedHook(visitedCheck) {
        after {
            if (innerDepth == 0 && !it.hasThrowable()) {
                it.result = RemoveSubRedditDialogPatch.spoofHasBeenVisitedStatus(it.result as Boolean)
            }
        }
    })
    for ((show, create) in dialogs.zip(createDialogs)) {
        show.hookMethod(scopedHook(create) {
            after {
                if (innerDepth == 0 && !it.hasThrowable()) {
                    RemoveSubRedditDialogPatch.dismissNSFWDialog(it.result ?: return@after)
                }
            }
        })
    }
    setExtensionIsPatchIncluded(RemoveSubRedditDialogPatch::class.java)
}
