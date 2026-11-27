package io.github.nexalloy.morphe.youtube.layout.hide.playerflyoutmenu

import android.view.View
import android.widget.ListView
import app.morphe.extension.youtube.patches.HidePlayerFlyoutMenuPatch
import app.morphe.extension.youtube.patches.components.PlayerFlyoutMenuComponentsFilter
import app.morphe.extension.youtube.settings.preference.HideAudioFlyoutMenuPreference
import io.github.nexalloy.morphe.ResourceType
import io.github.nexalloy.morphe.getResourceId
import io.github.nexalloy.morphe.shared.misc.litho.filter.addLithoFilter
import io.github.nexalloy.morphe.shared.misc.litho.node.hookTreeNodeResult
import io.github.nexalloy.morphe.shared.misc.proto.hookElement
import io.github.nexalloy.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.misc.litho.filter.LithoFilter
import io.github.nexalloy.morphe.youtube.misc.litho.node.TreeNodeElementHook
import io.github.nexalloy.morphe.youtube.misc.playertype.PlayerTypeHook
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.morphe.youtube.misc.playservice.is_21_44_or_greater
import io.github.nexalloy.morphe.youtube.misc.proto.elementProtoParserHookPatch
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook

val HidePlayerFlyoutMenuComponents = patch(
    name = "Hide player flyout menu components",
    description = "Adds options to hide menu components that appear when pressing the gear icon in the video player.",
) {
    dependsOn(
        LithoFilter,
        PlayerTypeHook,
        TreeNodeElementHook,
        elementProtoParserHookPatch,
        VersionCheck,
    )

    PreferenceScreen.PLAYER.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_hide_player_flyout",
            preferences = setOf(
                SwitchPreference("morphe_hide_player_flyout_additional_settings"),
                SwitchPreference("morphe_hide_player_flyout_ambient_mode"),
                SwitchPreference(
                    key = "morphe_hide_player_flyout_audio_track",
                    tag = HideAudioFlyoutMenuPreference::class.java
                ),
                SwitchPreference(
                    key = "morphe_hide_player_flyout_audio_track_footer",
                    tag = HideAudioFlyoutMenuPreference::class.java
                ),
                SwitchPreference("morphe_hide_player_flyout_captions"),
                SwitchPreference("morphe_hide_player_flyout_captions_footer"),
                SwitchPreference("morphe_hide_player_flyout_captions_header"),
                SwitchPreference("morphe_hide_player_flyout_help"),
                SwitchPreference("morphe_hide_player_flyout_listen_with_youtube_music"),
                SwitchPreference("morphe_hide_player_flyout_lock_screen"),
                SwitchPreference("morphe_hide_player_flyout_loop_video"),
                SwitchPreference("morphe_hide_player_flyout_on_the_go"),
                SwitchPreference("morphe_hide_player_flyout_quality"),
                SwitchPreference("morphe_hide_player_flyout_quality_footer"),
                SwitchPreference("morphe_hide_player_flyout_quality_header"),
                SwitchPreference("morphe_hide_player_flyout_sleep_timer"),
                SwitchPreference("morphe_hide_player_flyout_speed"),
                SwitchPreference("morphe_hide_player_flyout_stable_volume"),
                SwitchPreference("morphe_hide_player_flyout_watch_in_vr")
            )
        )
    )

    // The Shorts player also uses a flyout menu, but with its own settings.
    PreferenceScreen.SHORTS.addPreferences(
        SwitchPreference(
            key = "morphe_hide_shorts_flyout_audio_track",
            titleKey = "morphe_hide_player_flyout_audio_track_title",
            tag = HideAudioFlyoutMenuPreference::class.java
        ),
        SwitchPreference(
            key = "morphe_hide_shorts_flyout_captions",
            titleKey = "morphe_hide_player_flyout_captions_title"
        )
    )

    // The native (non-Litho) flyout menu components, such as the quality and captions sheets.
    addLithoFilter(PlayerFlyoutMenuComponentsFilter())

    // The headers of the native bottom sheets are parsed proto elements. Every parsed element
    // is passed to the extension, which clears the header of the matched elements.
    hookElement(HidePlayerFlyoutMenuPatch::hideNativeBottomSheetHeader)

    // The footers of the native bottom sheets are removed from the converted element lists.
    hookTreeNodeResult(
        HidePlayerFlyoutMenuPatch::hideNativeBottomSheetFooter,
        isLazilyConvertedElement = false
    )

    // region Patch for the old (non-Litho) bottom sheets

    // Upstream replaces the calls that add the header and footer views of the old bottom
    // sheets with extension calls, which add an empty view instead when the setting is enabled.
    // At runtime the calls are scoped-hooked, the extension is called with the arguments of
    // the original call, and the original call is skipped by setting its result, as the
    // extension invokes it itself with the substituted view. Calls the extension makes while
    // handling the original call are skipped by the inner depth guard.
    val listViewAddFooterView = ListView::class.java.getMethod(
        "addFooterView", View::class.java, Any::class.java, Boolean::class.javaPrimitiveType
    )
    val listViewAddHeaderView = ListView::class.java.getMethod(
        "addHeaderView", View::class.java, Any::class.java, Boolean::class.javaPrimitiveType
    )
    val viewFindViewById = View::class.java.getMethod(
        "findViewById", Int::class.javaPrimitiveType
    )
    val bottomSheetTitleId = getResourceId(ResourceType.ID, "bottom_sheet_title")

    if (is_21_44_or_greater) {
        ::captionsOldBottomSheetFooterMethods.dexMethodList.forEach { method ->
            method.hookMethod(scopedHook(listViewAddFooterView) {
                before {
                    if (innerDepth == 0) {
                        HidePlayerFlyoutMenuPatch.hideCaptionsOldBottomSheetFooter(
                            it.thisObject as ListView,
                            it.args[0] as View,
                            it.args[1],
                            it.args[2] as Boolean
                        )
                        it.result = null // The extension already added the footer view.
                    }
                }
            })
        }

        CaptionsOldBottomSheetHeaderFingerprint.hookMethod(scopedHook(viewFindViewById) {
            before {
                if (innerDepth == 0 && it.args[0] == bottomSheetTitleId) {
                    // The extension finds the header view itself and removes it from its parent
                    // when the setting is enabled.
                    it.result = HidePlayerFlyoutMenuPatch.hideCaptionsOldBottomSheetHeader(
                        it.thisObject as View,
                        it.args[0] as Int
                    )
                }
            }
        })
    } else {
        ::captionsOldBottomSheetLayoutInflaterMethods.dexMethodList.forEach { method ->
            method.hookMethod(scopedHook(listViewAddFooterView) {
                before {
                    if (innerDepth == 0) {
                        HidePlayerFlyoutMenuPatch.hideCaptionsOldBottomSheetFooter(
                            it.thisObject as ListView,
                            it.args[0] as View,
                            it.args[1],
                            it.args[2] as Boolean
                        )
                        it.result = null // The extension already added the footer view.
                    }
                }
            })
            method.hookMethod(scopedHook(viewFindViewById) {
                before {
                    if (innerDepth == 0 && it.args[0] == bottomSheetTitleId) {
                        it.result = HidePlayerFlyoutMenuPatch.hideCaptionsOldBottomSheetHeader(
                            it.thisObject as View,
                            it.args[0] as Int
                        )
                    }
                }
            })
        }
    }

    ::qualityOldBottomSheetLayoutInflaterMethods.dexMethodList.forEach { method ->
        method.hookMethod(scopedHook(listViewAddFooterView) {
            before {
                if (innerDepth == 0) {
                    HidePlayerFlyoutMenuPatch.hideQualityOldBottomSheetFooter(
                        it.thisObject as ListView,
                        it.args[0] as View,
                        it.args[1],
                        it.args[2] as Boolean
                    )
                    it.result = null // The extension already added the footer view.
                }
            }
        })
        method.hookMethod(scopedHook(listViewAddHeaderView) {
            before {
                if (innerDepth == 0) {
                    HidePlayerFlyoutMenuPatch.hideQualityOldBottomSheetHeader(
                        it.thisObject as ListView,
                        it.args[0] as View,
                        it.args[1],
                        it.args[2] as Boolean
                    )
                    it.result = null // The extension already added the header view.
                }
            }
        })
    }

    // endregion
}
