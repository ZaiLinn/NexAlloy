/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4213
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.settings.preference;

import android.content.Context;
import android.preference.PreferenceManager;
import android.util.AttributeSet;

import androidx.annotation.NonNull;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import app.morphe.extension.shared.StringRef;
import app.morphe.extension.shared.settings.preference.CustomDialogListPreference;
import app.morphe.extension.youtube.patches.AutoTranslateCaptionsPatch;
import app.morphe.extension.youtube.settings.Settings;

/**
 * Localized language list of the preferred and the secondary caption language.
 */
@SuppressWarnings({"unused", "deprecation"})
public final class CaptionLanguagePreference extends CustomDialogListPreference {

    public CaptionLanguagePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * The key is assigned by the module after the constructor runs, so all key-dependent
     * setup must wait until the preference is attached to the hierarchy.
     */
    @Override
    protected void onAttachedToHierarchy(PreferenceManager preferenceManager) {
        super.onAttachedToHierarchy(preferenceManager);

        final boolean preferredLanguage = Settings.PREFERRED_CAPTION_LANGUAGE.key.equals(getKey());

        List<CharSequence> labels = new ArrayList<>();
        List<CharSequence> values = new ArrayList<>();
        labels.add(StringRef.str("morphe_preferred_caption_language_entry_off"));
        values.add("off");
        if (preferredLanguage) {
            labels.add(StringRef.str("morphe_language_DEFAULT"));
            values.add("default");
        }

        Locale locale = getContext().getResources().getConfiguration().getLocales().get(0);
        String selected = preferredLanguage
                ? Settings.PREFERRED_CAPTION_LANGUAGE.get()
                : Settings.SECONDARY_CAPTION_LANGUAGE.get();
        String[] codes = AutoTranslateCaptionsPatch.translationLanguages(selected);
        Map<String, String> names = new HashMap<>();
        for (String code : codes) {
            names.put(code, AutoTranslateCaptionsPatch.translationLanguageName(code, locale));
        }
        Collator collator = Collator.getInstance(locale);
        Arrays.sort(codes, (first, second) -> collator.compare(names.get(first), names.get(second)));
        for (String code : codes) {
            labels.add(names.get(code));
            values.add(code);
        }

        setEntries(labels.toArray(new CharSequence[0]));
        setEntryValues(values.toArray(new CharSequence[0]));
    }

    @NonNull
    @Override
    public CharSequence getSummary() {
        // Computed here rather than in the constructor, because the key is not yet assigned then.
        final String summaryKey = getKey() + "_summary";
        CharSequence selected = getEntry();
        if (selected == null || "off".equalsIgnoreCase(getValue())) {
            return StringRef.str(summaryKey);
        }
        return selected + "\n" + StringRef.str(summaryKey);
    }
}
