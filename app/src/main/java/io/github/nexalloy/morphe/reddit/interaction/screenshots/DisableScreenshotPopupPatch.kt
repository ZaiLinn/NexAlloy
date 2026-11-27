package io.github.nexalloy.morphe.reddit.interaction.screenshots

import app.morphe.extension.reddit.patches.DisableScreenshotPopupPatch
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch
import java.lang.reflect.Modifier

internal val DisableScreenshotPopup = patch(
    name = "Disable screenshot popup",
    description = "Adds an option to disable the popup that appears when taking a screenshot.",
) {
    val unitClass = classLoader.loadClass("kotlin.Unit")
    val hostUnit = unitClass.declaredFields.single {
        Modifier.isStatic(it.modifiers) && it.type == unitClass
    }.apply { isAccessible = true }.get(null)
    val handlers = listOf(
        ScreenshotShareSheetFingerprint.method,
        RedditScreenshotTriggerSharingListenerFingerprint.method,
        ScreenshotTakenBannerFingerprint.method,
    )
    // These coroutines exist solely to show a screenshot banner or open the screenshot share sheet.
    for (handler in handlers) {
        handler.hookMethod {
            before {
                if (!DisableScreenshotPopupPatch.disableScreenshotPopup(true)) it.result = hostUnit
            }
        }
    }
    setExtensionIsPatchIncluded(DisableScreenshotPopupPatch::class.java)
}
