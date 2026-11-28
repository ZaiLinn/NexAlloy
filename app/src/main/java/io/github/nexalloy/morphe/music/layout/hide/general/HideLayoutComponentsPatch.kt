/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Runtime port of "Hide layout components"
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/music/layout/hide/general/HideLayoutComponentsPatch.kt).
 *
 * The upstream patch injects hide calls at the instructions its fingerprints match. At runtime
 * the same extension entry points are reached by hooking the matched methods and, for the view
 * lookups, hooking the lookup call while it runs inside the matched method. The shelf removal
 * of the 'You may also like' section, which the upstream patch adds as two generated methods,
 * runs in the constructor hook of the section list wrapper while the render method is on the
 * stack, the same point the upstream patch inserts its call at.
 */

package io.github.nexalloy.morphe.music.layout.hide.general

import android.support.v7.widget.RecyclerView
import android.view.View
import app.morphe.extension.music.patches.components.CommentsFilter
import app.morphe.extension.music.patches.components.CustomFilter
import app.morphe.extension.music.patches.components.LayoutComponentsFilter
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.ResourceType
import io.github.nexalloy.morphe.getResourceId
import io.github.nexalloy.morphe.music.misc.litho.filter.lithoFilterPatch
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.shared.misc.litho.filter.addLithoFilter
import io.github.nexalloy.morphe.shared.misc.settings.preference.InputType
import io.github.nexalloy.morphe.shared.misc.settings.preference.PreferenceCategory
import io.github.nexalloy.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.TextPreference
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

val hideLayoutComponentsPatch = patch(
    name = "Hide layout components",
    description = "Adds options to hide general layout components.",
) {
    dependsOn(lithoFilterPatch)

    PreferenceScreen.FEED.addPreferences(
        SwitchPreference(
            "morphe_music_hide_podcast_episode_download_button",
            titleKey = "morphe_music_hide_download_button_title",
            summary = true
        ),
        SwitchPreference("morphe_music_hide_explore_shelf"),
        SwitchPreference("morphe_music_hide_grid_shelves"),
        SwitchPreference("morphe_music_hide_filter_bar"),
        SwitchPreference("morphe_music_hide_horizontal_shelves"),
        SwitchPreference("morphe_music_hide_list_shelves"),
        SwitchPreference("morphe_music_hide_new_button", summary = true),
        SwitchPreference("morphe_music_hide_new_from_shelf"),
        SwitchPreference("morphe_music_hide_playlist_shelves"),
        SwitchPreference("morphe_music_hide_speed_dial_shelf"),
        SwitchPreference("morphe_music_hide_you_may_also_like_section"),
        SwitchPreference("morphe_music_hide_your_downloads_shelf")
    )

    PreferenceScreen.GENERAL.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_music_custom_filter_screen",
            titleKey = "morphe_custom_filter_screen_title",
            summaryKey = "morphe_custom_filter_screen_summary",
            sorting = Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference(
                    key = "morphe_music_custom_filter",
                    titleKey = "morphe_custom_filter_title"
                ),
                TextPreference(
                    key = "morphe_music_custom_filter_strings",
                    titleKey = "morphe_custom_filter_screen_title",
                    summaryKey = "morphe_custom_filter_strings_summary",
                    inputType = InputType.TEXT_MULTI_LINE
                )
            )
        )
    )

    PreferenceScreen.PLAYER.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_music_comments_screen",
            titleKey = "morphe_music_comments_screen_title",
            summaryKey = "morphe_music_comments_screen_summary",
            sorting = Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference("morphe_music_hide_comments_community_guidelines"),
                SwitchPreference("morphe_music_hide_comments_context"),
                SwitchPreference("morphe_music_hide_comments_emoji_button"),
                SwitchPreference("morphe_music_hide_comments_gif_button"),
                SwitchPreference("morphe_music_hide_comments_gifs_and_images", summary = true),
                SwitchPreference("morphe_music_hide_comments_info_button"),
                SwitchPreference("morphe_music_hide_comments_timestamp_button")
            )
        ),
        SwitchPreference("morphe_music_hide_audio_video_toggle"),
        PreferenceCategory(
            titleKey = "morphe_music_hide_lyrics_panel_category_title",
            preferences = setOf(
                SwitchPreference(
                    "morphe_music_hide_lyrics_share_button",
                    titleKey = "morphe_music_hide_share_button_title"
                ),
                SwitchPreference("morphe_music_hide_lyrics_translate_button")
            )
        ),
        SwitchPreference("morphe_music_hide_repeat_button"),
        SwitchPreference("morphe_music_hide_shuffle_button")
    )

    addLithoFilter(CommentsFilter())
    addLithoFilter(CustomFilter())
    addLithoFilter(LayoutComponentsFilter())

    // The view lookup call the hide calls of the upstream patch are injected after.
    val findViewById = View::class.java.getDeclaredMethod(
        "findViewById", Int::class.javaPrimitiveType
    )

    /**
     * Hooks every method of [methods] and calls [hide] with the result of the view lookup of
     * [id] that runs inside it, the same view the upstream patch passes to its injected call.
     */
    fun hookViewLookupHides(methods: List<DexMethod>, id: Int, hide: (View) -> Unit) {
        methods.forEach { method ->
            method.toMember().hookMethod(scopedHook(findViewById) {
                after {
                    if (it.hasThrowable()) return@after
                    if (it.result == null || (it.args[0] as? Int) != id) return@after
                    hide(it.result as View)
                }
            })
        }
    }

    // region hide audio / video toggle

    val pillContainerId = getResourceId(ResourceType.ID, "audio_video_switch_pill_container")
    hookViewLookupHides(
        ::audioVideoSwitchPillMethods.dexMethodList,
        pillContainerId,
        LayoutComponentsFilter::hideAudioVideoToggle
    )

    // endregion

    // region hide filter bar

    // The upstream patch hides the result of the static inflate helper the render method of
    // the chip cloud calls; the same call is intercepted inside the render method.
    val chipCloudLayoutId = getResourceId(ResourceType.LAYOUT, "chip_cloud")
    ChipCloudFingerprint.hookMethod(scopedHook(::chipCloudInflateHelperMethod.member) {
        after {
            if (it.hasThrowable() || it.result == null) return@after
            if (it.args.none { arg -> arg == chipCloudLayoutId }) return@after
            LayoutComponentsFilter.hideFilterBar(it.result as View)
        }
    })

    // endregion

    // region hide comments info button

    val informationButtonId = getResourceId(ResourceType.ID, "information_button")
    hookViewLookupHides(
        listOf(::informationButtonMethod.dexMethod),
        informationButtonId,
        CommentsFilter::hideCommentsInfoButton
    )

    // endregion

    // region hide repeat button

    val overlayQueueLoopButtonId = getResourceId(ResourceType.ID, "overlay_queue_loop_button_view")
    val playbackQueueLoopButtonId = getResourceId(ResourceType.ID, "playback_queue_loop_button_view")
    hookViewLookupHides(
        ::overlayQueueLoopButtonMethods.dexMethodList,
        overlayQueueLoopButtonId,
        LayoutComponentsFilter::hideRepeatButton
    )
    hookViewLookupHides(
        ::playbackQueueLoopButtonMethods.dexMethodList,
        playbackQueueLoopButtonId,
        LayoutComponentsFilter::hideRepeatButton
    )

    // endregion

    // region hide shuffle button

    val overlayQueueShuffleButtonId = getResourceId(ResourceType.ID, "overlay_queue_shuffle_button_view")
    val playbackQueueShuffleButtonId = getResourceId(ResourceType.ID, "playback_queue_shuffle_button_view")
    hookViewLookupHides(
        ::overlayQueueShuffleButtonMethods.dexMethodList,
        overlayQueueShuffleButtonId,
        LayoutComponentsFilter::hideShuffleButton
    )
    hookViewLookupHides(
        ::playbackQueueShuffleButtonMethods.dexMethodList,
        playbackQueueShuffleButtonId,
        LayoutComponentsFilter::hideShuffleButton
    )

    // endregion

    // region hide the floating New button in the Library tab

    val floatingActionButtonId = getResourceId(ResourceType.ID, "floating_action_button")
    NewButtonFingerprint.hookMethod(scopedHook(findViewById) {
        after {
            if (it.hasThrowable()) return@after
            if (it.result == null || (it.args[0] as? Int) != floatingActionButtonId) return@after
            LayoutComponentsFilter.hideNewButton(it.result as View)
        }
    })

    // endregion

    // region hide 'You may also like' section in search

    // Each shelf item holds one renderer: a search history entry, a suggested search,
    // or another renderer used outside the search page.
    val shelfItemsField: Field = ::musicShelfItemsField.field.apply { isAccessible = true }
    val searchHistoryField: Field = ::musicShelfSearchHistoryField.field.apply { isAccessible = true }
    val suggestedSearchField: Field = ::musicShelfSuggestedSearchField.field.apply { isAccessible = true }
    val shelfItemsFieldType: Class<*> = shelfItemsField.type
    val shelfType: Class<*> = shelfItemsField.declaringClass

    // The section list wrapper converts a section list item to its renderer.
    val sectionListItemType = SectionListItemRendererFingerprint.method.parameterTypes.first()
    val sectionListItemShelfField = sectionListItemType.declaredFields.first {
        !Modifier.isStatic(it.modifiers) && it.type == shelfType
    }.apply { isAccessible = true }

    // The section list, the only parameter of the wrapper the render method creates.
    val sectionListType: Class<*> = ::searchSuggestionsWrapperConstructor.constructor.parameterTypes.first()
    val sectionListContentsFields = sectionListType.declaredFields.filter {
        !Modifier.isStatic(it.modifiers) && it.type == shelfItemsFieldType
    }.apply { forEach { field -> field.isAccessible = true } }

    val recyclerViewField = SearchSuggestionsRenderFingerprint.declaredClass.declaredFields.first {
        !Modifier.isStatic(it.modifiers) && it.type == RecyclerView::class.java
    }.apply { isAccessible = true }

    // Copies a proto list into a mutable list with the given capacity.
    val protoListMutableCopyMethod: Method = ProtoListMutableCopyFingerprint.method.apply {
        isAccessible = true
    }

    /**
     * A section is removed when its shelf holds no search history entry and at least one
     * suggested search, the same shelves the remove method of the upstream patch drops.
     */
    fun isSuggestedSearchOnlyShelf(item: Any?): Boolean {
        if (item == null || !sectionListItemType.isInstance(item)) return false
        val shelf = sectionListItemShelfField.get(item) ?: return false
        val items = shelfItemsField.get(shelf) as? List<*> ?: return false
        var suggestedSearchOnly = false
        for (shelfItem in items) {
            if (searchHistoryField.get(shelfItem) != null) return false
            if (suggestedSearchField.get(shelfItem) != null) suggestedSearchOnly = true
            else return false
        }
        return suggestedSearchOnly
    }

    fun removeSuggestedSearchShelves(list: List<*>): Any? {
        val kept = list.filterNot(::isSuggestedSearchOnlyShelf)
        if (kept.size == list.size) return null
        // The upstream patch copies the list with its own size as the capacity of the copy.
        val mutableCopy = protoListMutableCopyMethod.invoke(list, list.size) as MutableList<Any?>
        mutableCopy.addAll(kept)
        return mutableCopy
    }

    // The upstream patch inserts the shelf removal right before the wrapper of the section
    // list is created. The constructor hook below runs at the same point, while the render
    // method is on the stack; the instance it reads the suggestions list view from is the
    // receiver of the render method.
    SearchSuggestionsRenderFingerprint.hookMethod(scopedHook(::searchSuggestionsWrapperConstructor.member) {
        before {
            if (innerDepth != 0) return@before
            val renderInstance = outerParam.thisObject ?: outerParam.args.firstOrNull()
                ?: return@before
            val sectionList = it.args.firstOrNull() ?: return@before
            val suggestionsList = recyclerViewField.get(renderInstance) as? View
            if (!LayoutComponentsFilter.hideYouMayAlsoLikeSection(suggestionsList)) return@before
            for (field in sectionListContentsFields) {
                val contents = field.get(sectionList) as? List<*> ?: continue
                val filtered = removeSuggestedSearchShelves(contents) ?: continue
                field.set(sectionList, filtered)
            }
        }
    })

    // endregion
}
