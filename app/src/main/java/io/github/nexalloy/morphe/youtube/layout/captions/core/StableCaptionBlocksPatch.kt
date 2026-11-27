package io.github.nexalloy.morphe.youtube.layout.captions.core

import android.app.Dialog
import android.view.View
import android.widget.ListView
import app.morphe.extension.shared.ResourceUtils
import app.morphe.extension.youtube.patches.CaptionMenu
import app.morphe.extension.youtube.settings.preference.CaptionLanguagePreference
import app.morphe.extension.youtube.patches.StableCaptionBlocks as StableCaptionBlocksExtension
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.morphe.shared.misc.litho.node.hookTreeNodeResult
import io.github.nexalloy.morphe.shared.misc.settings.preference.ListPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.layout.flyout.FeedBottomSheetFlyoutFingerprint
import io.github.nexalloy.morphe.youtube.misc.litho.node.TreeNodeElementHook
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.morphe.youtube.misc.playservice.is_21_15_or_greater
import io.github.nexalloy.patch

/**
 * Runtime port of the upstream stableCaptionBlocksPatch.
 *
 * Shows automatic captions in stable blocks with optional bilingual translation.
 *
 * Porting status: the settings and the bilingual captions toggle of the captions bottom sheets
 * are ported. The block rendering core of the upstream patch cannot run in a runtime module:
 * it works by adding the extension Cue/Row/Window/Timeline interfaces to the host caption
 * classes during patching, and the host objects cannot be made to implement module interfaces
 * at runtime. The extension checks `instanceof` for those interfaces and falls back to the
 * native captions for host objects, so the unported parts degrade gracefully:
 * - the cue/row/window interfaces and their `patch_*` methods are not wired up,
 * - the caption capture path (upstream translatedCaptionPensPatch) is not ported, so caption
 *   tracks are never marked as automatic and no block state is created,
 * - the timeline constructor and caption sampler hooks are omitted, since without a block
 *   state they resolve to the native behavior.
 * The timeline state storage of [CaptionTrackCapture] is kept compatible with the upstream
 * `patch_blockState` field, if the capture path is ever ported.
 */
val StableCaptionBlocks = patch(
    name = "Stable caption blocks",
    description = "Shows automatic captions in stable blocks with optional bilingual translation.",
) {
    dependsOn(
        VersionCheck,
        CaptionTrackCapture,
        CaptionWindowMeasure,
        TreeNodeElementHook,
    )

    if (!is_21_15_or_greater) {
        return@patch
    }

    settingsMenuCaptionGroup.addAll(
        listOf(
            SwitchPreference("morphe_stable_caption_blocks", summary = true),
            ListPreference(
                "morphe_secondary_caption_language",
                tag = CaptionLanguagePreference::class.java,
                entriesKey = null,
                entryValuesKey = null,
            ),
        )
    )

    // Bilingual captions toggle of the old captions bottom sheet.
    // Upstream inserts the toggle after the list view cast; at runtime the list view is
    // resolved from the inflated root view after the method returns.
    val bottomSheetListViewId = ResourceUtils.getIdIdentifier("bottom_sheet_list_view")
    ::captionsBottomSheetListMethods.dexMethodList.forEach { method ->
        method.hookMethod {
            after { param ->
                val root = param.result as? View ?: return@after
                val listView = root.findViewById<ListView>(bottomSheetListViewId) ?: return@after
                CaptionMenu.addToggle(listView)
            }
        }
    }

    // Bilingual captions toggle of the Litho captions bottom sheet.
    hookTreeNodeResult(
        { path, list -> CaptionMenu.onCaptionSheet(path, list) },
        isLazilyConvertedElement = false
    )

    // Bilingual captions toggle of the bottom sheet dialogs, including the Litho captions sheet.
    FeedBottomSheetFlyoutFingerprint.hookMethod {
        after { param ->
            CaptionMenu.onDialog(param.result as? Dialog)
        }
    }

    // The extension checks this to enable the block features.
    setExtensionIsPatchIncluded(StableCaptionBlocksExtension::class.java)
}
