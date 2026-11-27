package io.github.nexalloy.morphe.youtube.misc.bypasslinkredirects

import android.content.Intent
import android.net.Uri
import app.morphe.extension.youtube.patches.BypassLinkRedirectsPatch
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch

private val inVideoCommentsUriMethod = ThreadLocal.withInitial { false }

val BypassLinkRedirects = patch(
    name = "Bypass link redirects",
    description = "Adds an option to bypass redirects and open the original link directly.",
) {
    PreferenceScreen.MISC.addPreferences(
        SwitchPreference("morphe_bypass_link_redirects", summary = true),
    )

    // Override URI for video descriptions and community posts.
    // The Uri is the method parameter, the same register the upstream patch rewrites.
    DescriptionAndPostUriFingerprint.hookMethod {
        before {
            it.args[1] = BypassLinkRedirectsPatch.parseRedirectUri(it.args[1] as Uri)
        }
    }

    // Override URI for video comments. The Uri is a local register of the command resolver,
    // which passes it to the VIEW intent constructor, so rewrite it there while the
    // command resolver runs.
    VideoCommentsUriFingerprint.hookMethod {
        before { inVideoCommentsUriMethod.set(true) }
        after { inVideoCommentsUriMethod.set(false) }
    }

    Intent::class.java.getConstructor(String::class.java, Uri::class.java).hookMethod {
        before {
            if (!inVideoCommentsUriMethod.get()) return@before
            val action = it.args[0] as? String ?: return@before
            if (action != Intent.ACTION_VIEW) return@before
            it.args[1] = BypassLinkRedirectsPatch.parseRedirectUri(it.args[1] as Uri)
        }
    }
}
