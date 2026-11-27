package io.github.nexalloy.morphe.reddit.misc.openlink

import android.app.Activity
import android.net.Uri
import app.morphe.extension.reddit.patches.OpenLinksDirectlyPatch
import app.morphe.extension.reddit.patches.OpenLinksExternallyPatch
import de.robv.android.xposed.callbacks.XCallback
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch

val OpenLinksDirectly = patch(
    name = "Open links directly",
    description = "Skips Reddit external link redirects.",
) {
    // Run before the external-browser hook so either browser receives the destination URL.
    ::screenNavigatorOpenLink.method.hookMethod {
        priority = XCallback.PRIORITY_HIGHEST
        before {
            val uri = it.args[1] as? Uri ?: return@before
            it.args[1] = OpenLinksDirectlyPatch.parseRedirectUri(uri)
        }
    }
    setExtensionIsPatchIncluded(OpenLinksDirectlyPatch::class.java)
}

val OpenLinksExternally = patch(
    name = "Open links externally",
    description = "Opens external links in the default browser.",
) {
    val navigate = ::screenNavigatorOpenLink.method
    val createViewer = FbpActivityOnCreateFingerprint.method
    val createArticle = ArticleConstructorFingerprint.constructor
    check(createArticle.parameterTypes.getOrNull(2) == String::class.java)

    navigate.hookMethod {
        before {
            val activity = it.args[0] as? Activity ?: return@before
            val uri = it.args[1] as? Uri ?: return@before
            if (OpenLinksExternallyPatch.openLinksExternally(activity, uri)) it.result = null
        }
    }
    createViewer.hookMethod {
        before { OpenLinksExternallyPatch.setActivity(it.thisObject as Activity) }
    }
    createArticle.hookMethod {
        before { OpenLinksExternallyPatch.openLinksExternally(it.args[2] as? String) }
    }
    setExtensionIsPatchIncluded(OpenLinksExternallyPatch::class.java)
}
