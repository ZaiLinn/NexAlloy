package io.github.nexalloy.morphe.youtube.layout.captions.language

import app.morphe.extension.youtube.patches.CaptionCookiesPatch
import app.morphe.extension.youtube.settings.preference.GetCaptionCookiesPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.InputType
import io.github.nexalloy.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.TextPreference
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook
import io.github.nexalloy.morphe.youtube.layout.captions.core.settingsMenuCaptionGroup

val CaptionCookies = patch(
    name = "Caption cookies",
    description = "Adds an option to set cookies in Timed Text API (Caption Data API) requests.",
) {
    settingsMenuCaptionGroup.addAll(listOf(
        SwitchPreference("morphe_set_caption_cookies", summary = true),
        TextPreference(
            "morphe_caption_cookies",
            inputType = InputType.TEXT_MULTI_LINE,
        ),
        NonInteractivePreference(
            key = "morphe_get_caption_cookies",
            tag = GetCaptionCookiesPreference::class.java,
        ),
    ))

    // The Timed Text request is built with the Cronet url request builder.
    // Set the caption cookies of the built request, if the request requires them.
    // LSPosed cannot hook the abstract CronetEngine call-site member. Hook its
    // concrete implementation, avoiding the synthetic covariant bridge method.
    val builderAbi = ::cronetUrlRequestBuilderMethod.method
    val builderImplementation = classLoader.loadClass(
        "org.chromium.net.impl.CronetUrlRequestContext"
    ).declaredMethods.single {
        it.name == builderAbi.name &&
            it.parameterTypes.contentEquals(builderAbi.parameterTypes) &&
            !it.isBridge && !java.lang.reflect.Modifier.isAbstract(it.modifiers)
    }
    TimedTextUrlFingerprint.hookMethod(scopedHook(builderImplementation) {
        before {
            if (innerDepth == 0) {
                CaptionCookiesPatch.setRequireCookies(it.args[0] as? String)
            }
        }

        after {
            if (innerDepth != 0) return@after
            val requestBuilder = it.result ?: return@after
            if (!CaptionCookiesPatch.getRequireCookies()) return@after

            runCatching {
                val addHeader = requestBuilder.javaClass.getMethod(
                    "addHeader", String::class.java, String::class.java
                )

                // Set Cookie.
                addHeader.invoke(requestBuilder, "Cookie", CaptionCookiesPatch.getCookies())

                // Set User-Agent.
                addHeader.invoke(requestBuilder, "User-Agent", CaptionCookiesPatch.getUserAgent())
            }
        }
    })
}
