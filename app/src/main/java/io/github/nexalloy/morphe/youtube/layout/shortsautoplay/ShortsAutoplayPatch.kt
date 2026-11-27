/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */
package io.github.nexalloy.morphe.youtube.layout.shortsautoplay

import app.morphe.extension.youtube.patches.ShortsAutoplayPatch
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook
import java.util.concurrent.atomic.AtomicBoolean

internal val shortsAutoplayPatch = patch(
    name = "Shorts autoplay",
    description = "Adds options to automatically play the next Short.",
) {
    dependsOn(VersionCheck)

    // The upstream patcher injects the enum capture into the enum class initializer.
    // <clinit> cannot be hooked at runtime (the class loader invokes it directly),
    // so the enum values are captured lazily on the first loop behavior lookup instead.
    val ytEnumInitialized = AtomicBoolean(false)

    // Override the loop behavior at every lookup inside the playback repeat method.
    // The upstream patcher injects the call after each lookup call site; at runtime
    // the lookup method is hooked while the repeat method is on the stack.
    ReelPlaybackRepeatFingerprint.hookMethod(scopedHook(::reelBehaviorLookupMethod.member) {
        before {
            if (!ytEnumInitialized.getAndSet(true)) {
                // Resolve the enum class via the dex descriptor: <clinit> is not
                // reachable through Class#getDeclaredMethods, so the fingerprint's
                // java reflection accessor cannot be used here.
                val enumClass = classLoader.loadClass(ReelEnumConstructorFingerprint.dexMethod.className)
                enumClass.enumConstants?.first()?.let {
                    ShortsAutoplayPatch.setYTShortsRepeatEnum(it as Enum<*>)
                }
            }
        }
        after {
            // The original behavior can be null, in which case the extension
            // substitutes the unknown enum value.
            val overridden = ShortsAutoplayPatch.changeShortsRepeatBehavior(it.result as? Enum<*>)
            if (overridden !== it.result) it.result = overridden
        }
    })

    PreferenceScreen.SHORTS.addPreferences(
        SwitchPreference("morphe_shorts_autoplay", summary = true),
        SwitchPreference("morphe_shorts_autoplay_background", summary = true),
    )
}
