package io.github.nexalloy.morphe.youtube.layout.livechat

import app.morphe.extension.shared.settings.preference.ColorPickerPreference
import app.morphe.extension.shared.settings.preference.SeekBarPreference
import app.morphe.extension.youtube.patches.LiveChatTranslatePatch
import io.github.nexalloy.morphe.shared.misc.litho.relayout.lithoRelayoutPatch
import io.github.nexalloy.morphe.shared.misc.settings.preference.InputType
import io.github.nexalloy.morphe.shared.misc.settings.preference.ListPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.TextPreference
import io.github.nexalloy.morphe.shared.misc.textcomponent.hookLithoSpannableString
import io.github.nexalloy.morphe.shared.misc.textcomponent.lithoSpannableStringPatch
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch

val LiveChatTranslate = patch(
    name = "Translate live chat",
    description = "Adds an option to translate live chat and live chat replay messages.",
) {
    dependsOn(
        lithoSpannableStringPatch,
        // Shows the translation in the chat rows that are already on screen.
        lithoRelayoutPatch,
    )

    PreferenceScreen.PLAYER.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_live_chat_translate_screen",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference("morphe_live_chat_translate", summary = true),
                ListPreference(
                    "morphe_live_chat_translate_language",
                    entriesKey = "morphe_post_translate_language_entries",
                    entryValuesKey = "morphe_post_translate_language_entry_values",
                ),
                TextPreference("morphe_live_chat_translate_exclude"),
                ListPreference(
                    "morphe_live_chat_translate_provider",
                    titleKey = "morphe_vot_translation_service_title"
                ),
                TextPreference("morphe_live_chat_translate_deepl_api_key"),
                SwitchPreference("morphe_live_chat_translate_copy_button", summary = true),
                ListPreference("morphe_live_chat_translate_style"),
                SwitchPreference("morphe_live_chat_translate_bold", summary = true),
                SwitchPreference("morphe_live_chat_translate_use_color", summary = true),
                TextPreference(
                    "morphe_live_chat_translate_color",
                    tag = ColorPickerPreference::class.java,
                    inputType = InputType.TEXT_CAP_CHARACTERS,
                ),
                NonInteractivePreference(
                    "morphe_live_chat_translate_max_length",
                    tag = SeekBarPreference::class.java,
                    selectable = true,
                ),
            )
        )
    )

    // Chat rows are Litho texts, which are replaced when created and when reused.
    hookLithoSpannableString(LiveChatTranslatePatch::onLithoTextLoaded)
}
