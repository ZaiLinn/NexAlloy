/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 *
 * Runtime port of the Morphe patcher patch of the same name
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/youtube/layout/snackbar/SnackbarPatch.kt).
 *
 * Deviations from the patcher patch:
 * - The patcher patch overrides the result of the snackbar skip check of the display method with
 *   `hideSnackbar(original)`. In YouTube 21.45.82 the matched interface call (`kim.o(kin)` of
 *   `khv.i`) has show semantics instead of skip semantics, so forcing it to true would show the
 *   snackbar. The runtime port instead skips the `BottomUiContainer` display call the method
 *   makes, which hides the snackbar while the callbacks of the method still run.
 * - The Litho snackbar holder is not hooked (see Fingerprints.kt); the Litho snackbar view
 *   itself is hidden instead, which has the same effect.
 */

package io.github.nexalloy.morphe.youtube.layout.snackbar

import android.view.View
import android.widget.FrameLayout
import app.morphe.extension.shared.settings.preference.ColorPickerPreference
import app.morphe.extension.shared.settings.preference.ColorPickerWithOpacitySliderPreference
import app.morphe.extension.youtube.patches.SnackbarPatch
import app.morphe.extension.youtube.patches.spans.SnackbarFilter
import io.github.nexalloy.morphe.shared.layout.theme.lithoColorHookPatch
import io.github.nexalloy.morphe.shared.layout.theme.lithoDrawableColorHook
import io.github.nexalloy.morphe.shared.misc.settings.preference.InputType
import io.github.nexalloy.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.TextPreference
import io.github.nexalloy.morphe.shared.misc.spans.addSpanFilter
import io.github.nexalloy.morphe.shared.misc.spans.inclusiveSpanPatch
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.morphe.youtube.misc.playservice.is_21_30_or_greater
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook

val snackbarPatch = patch(
    name = "Snackbar",
    description = "Adds options to hide the snackbar or change its colors and shape.",
) {
    dependsOn(
        VersionCheck,
        inclusiveSpanPatch,
        lithoColorHookPatch { is_21_30_or_greater },
    )

    PreferenceScreen.GENERAL.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_snackbar_screen",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference("morphe_hide_snackbar"),
                SwitchPreference("morphe_custom_snackbar_theme", summary = true),
                TextPreference(
                    "morphe_custom_snackbar_color_dark",
                    tag = ColorPickerPreference::class.java,
                    inputType = InputType.TEXT_CAP_CHARACTERS
                ),
                TextPreference(
                    "morphe_custom_snackbar_color_light",
                    tag = ColorPickerPreference::class.java,
                    inputType = InputType.TEXT_CAP_CHARACTERS
                ),
                TextPreference(
                    "morphe_custom_snackbar_stroke_color",
                    tag = ColorPickerWithOpacitySliderPreference::class.java,
                    inputType = InputType.TEXT_CAP_CHARACTERS
                ),
                TextPreference(
                    "morphe_custom_snackbar_corner_radius",
                    inputType = InputType.NUMBER
                )
            )
        )
    )

    // region hide snackbar

    // Let YouTube skip displaying the snackbar while still running its callbacks of the
    // display method. The patcher patch overrides the skip check of the method instead,
    // see the header comment.
    SnackbarDisplayFingerprint.hookMethod(scopedHook(::snackbarDisplayCall.method) {
        before {
            if (SnackbarPatch.hideSnackbar(false)) it.result = null
        }
    })

    // The view of a Litho snackbar. The patcher patch hides the view that holds it,
    // and passes it to the custom theme; hiding the snackbar view itself has the same effect.
    LithoSnackbarFingerprint.hookMethod {
        after {
            val snackbar = it.result as? FrameLayout ?: return@after
            SnackbarPatch.onLithoSnackbarCreated(snackbar)
            SnackbarPatch.hideSnackbarView(snackbar)
        }
    }

    // endregion

    // region custom snackbar theme

    // Called when a snackbar that is not drawn by Litho is created,
    // before its content is added.
    arrayOf(
        QuantumSnackbarFingerprint,
        MaterialSnackbarFingerprint,
        AppSnackbarFingerprint,
        YouTubeSnackbarFingerprint
    ).forEach { fingerprint ->
        fingerprint.hookMethod {
            after { SnackbarPatch.onSnackbarCreated(it.thisObject as View) }
        }
    }

    // A bar with a message and a button that promotes a feature, shown in place of a snackbar.
    MealbarFingerprint.hookMethod {
        after { SnackbarPatch.onSnackbarCreated(it.thisObject as View) }
    }

    // The background of a Litho snackbar is drawn by the Litho component, which covers
    // the background of the view that holds it. The view is changed by onLithoSnackbarCreated,
    // and the component here.
    lithoDrawableColorHook(SnackbarPatch::getLithoColor)

    addSpanFilter(SnackbarFilter())

    // endregion
}
