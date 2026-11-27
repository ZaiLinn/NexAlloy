/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.reddit.settings.preference;

import static app.morphe.extension.shared.StringRef.str;

import android.app.Dialog;
import android.content.Context;
import android.preference.Preference;
import android.text.InputType;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.regex.PatternSyntaxException;

import app.morphe.extension.reddit.patches.KeywordFiltersPatch;
import app.morphe.extension.reddit.settings.Settings;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.theme.ThemeUtils;
import app.morphe.extension.shared.ui.CustomDialog;
import app.morphe.extension.shared.ui.Dim;

/**
 * Lists the community filters. Each filter is added, edited or removed in its own dialog.
 */
@SuppressWarnings("deprecation")
public final class CommunityFiltersPreference extends Preference {
    /**
     * Index of a new community filter.
     */
    private static final int NEW_RULE = -1;

    public CommunityFiltersPreference(Context context) {
        super(context);

        setTitle(str("morphe_community_filters_title"));
        setSummary(str("morphe_community_filters_summary"));
        setOnPreferenceClickListener(preference -> {
            showRulesDialog();
            return true;
        });
    }

    private void showRulesDialog() {
        Context context = getContext();
        List<String> rules = KeywordFiltersPatch.getRules(Settings.COMMUNITY_FILTERS.get());

        Pair<Dialog, LinearLayout> dialogPair = CustomDialog.create(
                context,
                str("morphe_community_filters_title"),
                rules.isEmpty() ? str("morphe_community_filters_empty") : null,
                null,
                str("morphe_community_filters_add"),
                () -> showRuleDialog(rules, NEW_RULE, ""),
                null,
                str("morphe_settings_about_close"),
                () -> {},
                true
        );
        Dialog dialog = dialogPair.first;

        if (!rules.isEmpty()) {
            ListView listView = new ListView(context);
            listView.setAdapter(new ArrayAdapter<>(context, 0, rules) {
                @NonNull
                @Override
                public View getView(int position, @Nullable View convertView, ViewGroup parent) {
                    TextView row = convertView instanceof TextView textView ? textView : new TextView(context);
                    row.setText(rules.get(position));
                    row.setTextSize(16);
                    row.setTextColor(ThemeUtils.getAppForegroundColor());
                    row.setPadding(Dim.dp8, Dim.dp12, Dim.dp8, Dim.dp12);
                    return row;
                }
            });
            listView.setDivider(null);
            listView.setOnItemClickListener((parent, view, position, id) -> {
                dialog.dismiss();
                showRuleDialog(rules, position, rules.get(position));
            });

            LinearLayout mainLayout = dialogPair.second;
            mainLayout.addView(listView, mainLayout.getChildCount() - 1,
                    new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));
        }

        dialog.show();
    }

    /**
     * @param index Index of the edited community filter, or {@link #NEW_RULE}.
     * @param text  Text to show in the editor.
     */
    private void showRuleDialog(List<String> rules, int index, String text) {
        Context context = getContext();
        EditText editText = CustomDialog.createEditText(context);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editText.setSingleLine(true);
        editText.setHint(str("morphe_community_filters_hint"));
        editText.setText(text);
        editText.setSelection(text.length());

        CustomDialog.create(
                context,
                str("morphe_community_filters_title"),
                null,
                editText,
                str("morphe_settings_save"),
                () -> {
                    String rule = editText.getText().toString().trim();
                    if (rule.isEmpty()) {
                        showRulesDialog();
                        return;
                    }

                    try {
                        // Community filters use the same regular expressions as keyword filters.
                        KeywordFiltersPatch.compile(rule);
                    } catch (PatternSyntaxException ex) {
                        Utils.showToastLong(str("morphe_community_filters_invalid", ex.getDescription()));
                        showRuleDialog(rules, index, rule);
                        return;
                    }

                    if (index == NEW_RULE) {
                        rules.add(rule);
                    } else {
                        rules.set(index, rule);
                    }
                    saveRules(rules);
                },
                this::showRulesDialog,
                index == NEW_RULE ? null : str("morphe_community_filters_remove"),
                () -> {
                    rules.remove(index);
                    saveRules(rules);
                },
                true
        ).first.show();
    }

    private void saveRules(List<String> rules) {
        Settings.COMMUNITY_FILTERS.save(String.join("\n", rules));
        Utils.showToastShort(str("morphe_community_filters_saved"));
        showRulesDialog();
    }
}
