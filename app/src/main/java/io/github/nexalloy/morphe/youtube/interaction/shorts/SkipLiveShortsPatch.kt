/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3847
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package io.github.nexalloy.morphe.youtube.interaction.shorts

import app.morphe.extension.youtube.patches.SkipLiveShortsPatch
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.youtube.video.information.VideoInformationPatch
import io.github.nexalloy.patch
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicReference

internal val skipLiveShortsPatch = patch(
    name = "Skip live Shorts",
    description = "Automatically skips live streams that appear in the Shorts feed.",
) {
    dependsOn(VersionCheck, VideoInformationPatch)

    // The upstream patcher adds the extension interface to the Shorts player class and
    // hands it to the extension. At runtime a proxy implements the interface instead, and
    // performs the same user action the app uses for autoplaying the next Short.
    // DexKit's field instances are not accessibility-modified, and the controller field
    // is private.
    val actionConstructor = ::skipUserActionConstructor.constructor
    val controllerMethod = ::skipReelSequenceControllerMethod.method
    val controllerField = ::skipReelSequenceControllerField.field.apply { isAccessible = true }
    val controllerInterface = SkipLiveShortsPatch.ReelSequenceController::class.java
    val lastPlayer = AtomicReference<Any?>()

    ReelPlaybackFingerprint.hookMethod {
        after {
            val player = it.thisObject ?: return@after
            if (lastPlayer.getAndSet(player) === player) return@after

            val controller = Proxy.newProxyInstance(
                controllerInterface.classLoader,
                arrayOf(controllerInterface),
            ) { _, method, _ ->
                if (method.name == "patch_skipToNextShort") {
                    val action = actionConstructor.newInstance(3, null, null, false)
                    controllerMethod.invoke(controllerField.get(player), action)
                }
                null
            }
            SkipLiveShortsPatch.setReelSequenceController(
                controller as SkipLiveShortsPatch.ReelSequenceController
            )
        }
    }

    PreferenceScreen.SHORTS.addPreferences(
        SwitchPreference("morphe_skip_live_shorts", summary = true)
    )
}
