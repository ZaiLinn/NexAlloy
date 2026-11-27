package io.github.nexalloy.morphe.youtube.video.quality.hidepremium

import app.morphe.extension.youtube.patches.playback.quality.HidePremiumVideoQualityPatch
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.video.information.VideoInformationPatch
import io.github.nexalloy.morphe.youtube.video.information.VideoQualityProxy
import io.github.nexalloy.morphe.youtube.video.quality.CurrentVideoFormatConstructorFingerprint
import io.github.nexalloy.morphe.youtube.video.quality.settingsMenuVideoQualityGroup
import io.github.nexalloy.patch
import java.lang.reflect.Array as ReflectArray

val HidePremiumVideoQuality = patch(
    name = "Hide premium video quality",
    description = "Adds an option to hide the premium video quality in the video quality menu.",
) {
    dependsOn(VideoInformationPatch)

    settingsMenuVideoQualityGroup.add(
        SwitchPreference("morphe_hide_premium_video_quality"),
    )

    val qualitiesField = ::currentVideoFormatQualitiesField.field

    // The upstream patch filters the qualities array with the extension before it is
    // assigned to the model field. At runtime the field is filtered as soon as the
    // constructor returns, before any reader of the model can observe it.
    CurrentVideoFormatConstructorFingerprint.hookMethod {
        after {
            val qualities = qualitiesField.get(it.thisObject) as? Array<*> ?: return@after
            val componentType = qualities.javaClass.componentType ?: return@after

            val proxies = Array<VideoQualityProxy?>(qualities.size) { index ->
                qualities[index]?.let { quality -> VideoQualityProxy(quality) }
            }

            val filtered = HidePremiumVideoQualityPatch.hidePremiumVideoQuality(proxies)

            // Keep the original array type, to avoid a ClassCastException in the host.
            val keptQualities = ReflectArray.newInstance(componentType, filtered.size)
            for (index in filtered.indices) {
                ReflectArray.set(keptQualities, index, (filtered[index] as VideoQualityProxy).quality)
            }

            qualitiesField.set(it.thisObject, keptQualities)
        }
    }
}
