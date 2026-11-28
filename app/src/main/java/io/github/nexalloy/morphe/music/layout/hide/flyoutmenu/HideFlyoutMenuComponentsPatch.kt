/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Runtime port of "Hide flyout menu components"
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/music/layout/hide/flyoutmenu/HideFlyoutMenuComponentsPatch.kt).
 */

package io.github.nexalloy.morphe.music.layout.hide.flyoutmenu

import android.view.View
import android.view.ViewGroup
import app.morphe.extension.music.patches.HideFlyoutMenuComponentsPatch
import app.morphe.extension.music.patches.components.PlayerFlyoutMenuComponentsFilter
import de.robv.android.xposed.XC_MethodHook
import io.github.nexalloy.ScopedHook
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.ResourceType
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.getResourceId
import io.github.nexalloy.morphe.music.misc.litho.filter.lithoFilterPatch
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.shared.misc.litho.filter.addLithoFilter
import io.github.nexalloy.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * The renderer field of the flyout menu item, the icon getter it is passed to, the icon type
 * field of the returned icon and the enum factory of the icon type. Resolved the same way the
 * upstream patch resolves them, from the icon binding method.
 */
internal val menuItemRendererField = findFieldDirect {
    MenuItemIconFingerprint.instructionMatches[1].instruction.fieldRef!!
}

internal val menuItemIconMethod = findMethodDirect {
    MenuItemIconFingerprint.instructionMatches[2].instruction.methodRef!!
}

internal val menuItemIconTypeField = findFieldDirect {
    MenuItemIconFingerprint.instructionMatches[4].instruction.fieldRef!!
}

internal val menuItemIconTypeEnumMethod = findMethodDirect {
    MenuItemIconFingerprint.instructionMatches[5].instruction.methodRef!!
}

/**
 * The container field of the flyout menu item, whose addView call is skipped for hidden items.
 * Anchor (9.46.51): `Ljfp;->n`, the FrameLayout the item view is added to.
 */
internal val menuItemContainerField = findFieldDirect {
    MenuItemFingerprint.instructionMatches[1].instruction.fieldRef!!
}

/**
 * Runtime port of "Hide flyout menu components".
 *
 * The upstream patch injects a branch into the item binding method that resolves the icon type
 * enum of the item and jumps past the addView when the extension reports it hidden. At runtime
 * the same enum resolution runs in the binding method hook, and the addView of the item
 * container is skipped for the duration of the binding when the item is hidden, so the parts
 * of the method before the upstream injection point (the toggle mutation bookkeeping) still
 * run exactly like they do with the patcher patch.
 */
val hideFlyoutMenuComponentsPatch = patch(
    name = "Hide flyout menu components",
    description = "Adds options to hide individual items from the player and queue flyout menus.",
) {
    dependsOn(lithoFilterPatch)

    PreferenceScreen.PLAYER.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_music_hide_flyout_menu_components_screen",
            preferences = setOf(
                SwitchPreference("morphe_music_hide_flyout_menu_3_column_component"),
                SwitchPreference("morphe_music_hide_flyout_menu_like_dislike"),
                SwitchPreference("morphe_music_hide_flyout_menu_taste_match"),
                SwitchPreference("morphe_music_hide_flyout_menu_add_to_listen_later"),
                SwitchPreference("morphe_music_hide_flyout_menu_add_to_queue"),
                SwitchPreference("morphe_music_hide_flyout_menu_captions"),
                SwitchPreference("morphe_music_hide_flyout_menu_delete_playlist"),
                SwitchPreference("morphe_music_hide_flyout_menu_dismiss_queue"),
                SwitchPreference("morphe_music_hide_flyout_menu_dont_recommend_artist"),
                SwitchPreference("morphe_music_hide_flyout_menu_download"),
                SwitchPreference("morphe_music_hide_flyout_menu_edit_playlist"),
                SwitchPreference("morphe_music_hide_flyout_menu_go_to_album"),
                SwitchPreference("morphe_music_hide_flyout_menu_go_to_artist"),
                SwitchPreference("morphe_music_hide_flyout_menu_go_to_episode"),
                SwitchPreference("morphe_music_hide_flyout_menu_go_to_podcast"),
                SwitchPreference("morphe_music_hide_flyout_menu_help"),
                SwitchPreference("morphe_music_hide_flyout_menu_mark_episode_as_played"),
                SwitchPreference("morphe_music_hide_flyout_menu_not_interested"),
                SwitchPreference("morphe_music_hide_flyout_menu_pin_to_speed_dial"),
                SwitchPreference("morphe_music_hide_flyout_menu_play_next"),
                SwitchPreference("morphe_music_hide_flyout_menu_quality"),
                SwitchPreference("morphe_music_hide_flyout_menu_remove_from_library"),
                SwitchPreference("morphe_music_hide_flyout_menu_remove_from_playlist"),
                SwitchPreference("morphe_music_hide_flyout_menu_report"),
                SwitchPreference("morphe_music_hide_flyout_menu_save_episode_for_later_save_to_library"),
                SwitchPreference("morphe_music_hide_flyout_menu_save_to_playlist"),
                SwitchPreference("morphe_music_hide_flyout_menu_share"),
                SwitchPreference("morphe_music_hide_flyout_menu_shuffle_play"),
                SwitchPreference("morphe_music_hide_flyout_menu_sleep_timer"),
                SwitchPreference("morphe_music_hide_flyout_menu_start_radio"),
                SwitchPreference("morphe_music_hide_flyout_menu_stats_for_nerds"),
                SwitchPreference("morphe_music_hide_flyout_menu_subscribe"),
                SwitchPreference("morphe_music_hide_flyout_menu_unpin_from_speed_dial"),
                SwitchPreference("morphe_music_hide_flyout_menu_view_song_credit")
            )
        )
    )

    val rendererField: Field = ::menuItemRendererField.field
    val iconMethod: Method = ::menuItemIconMethod.method
    val iconTypeField: Field = ::menuItemIconTypeField.field
    val iconTypeEnumMethod: Method = ::menuItemIconTypeEnumMethod.method
    val containerField: Field = ::menuItemContainerField.field

    // The hidden decision of the currently binding menu item. Computed in the outer hook,
    // consumed by the addView hook, like the branch the upstream patch injects.
    val hiddenItem = ThreadLocal.withInitial { false }

    fun resolveIconType(item: Any?): Enum<*>? {
        if (item == null) return null
        val renderer = rendererField.get(item) ?: return null
        val icon = iconMethod.invoke(null, renderer) ?: return null
        if (!iconTypeField.declaringClass.isInstance(icon)) return null
        return iconTypeEnumMethod.invoke(null, iconTypeField.getInt(icon)) as? Enum<*>
    }

    val menuItemHook = object : ScopedHook() {
        override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
            super.beforeHookedMethod(param)
            hiddenItem.set(
                runCatching {
                    val iconType = resolveIconType(param.thisObject)
                    iconType != null && HideFlyoutMenuComponentsPatch.hideComponents(iconType)
                }.getOrDefault(false)
            )
        }

        override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
            hiddenItem.set(false)
            super.afterHookedMethod(param)
        }
    }

    menuItemHook.hookInnerMethod(
        ViewGroup::class.java.getMethod("addView", View::class.java),
        { param ->
            val bindingItem = outerParam.thisObject
            if (hiddenItem.get() && bindingItem != null && runCatching {
                    param.thisObject === containerField.get(bindingItem)
                }.getOrDefault(false)
            ) {
                // Skip adding the hidden item view to the menu, like the injected branch does.
                param.result = null
            }
        },
        { }
    )

    MenuItemFingerprint.hookMethod(menuItemHook)

    // Legacy View-level hide for older YT Music versions where like/dislike is a
    // native child of `end_buttons_container`. From ~9.x the container is empty and
    // the button is drawn via Litho (see `like_toggle_button.` filter in
    // PlayerFlyoutMenuComponentsFilter). Kept side-by-side as a fallback for
    // pre-Litho builds; the two hooks share HIDE_FLYOUT_MENU_LIKE_DISLIKE and are
    // mutually harmless when both fire.
    val endButtonsContainerId = getResourceId(ResourceType.ID, "end_buttons_container")

    EndButtonsContainerFingerprint.hookMethod(
        scopedHook(
            View::class.java.getDeclaredMethod("findViewById", Int::class.javaPrimitiveType)
        ) {
            after {
                if (it.args[0] == endButtonsContainerId) {
                    HideFlyoutMenuComponentsPatch.hideLikeDislikeContainer(it.result as? View ?: return@after)
                }
            }
        }
    )

    addLithoFilter(PlayerFlyoutMenuComponentsFilter())
}
