/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.settings;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toolbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import app.morphe.extension.reddit.settings.preference.RedditPreferenceFragment;
import app.morphe.extension.reddit.settings.preference.RedditPreferenceFragment.SearchEntry;
import app.morphe.extension.reddit.settings.preference.RedditSettingsStyle;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.theme.ThemeUtils;

/** Searches the actual patch-enabled preference screens without retaining probe widgets. */
@SuppressWarnings("deprecation")
public final class RedditSettingsSearchDialog extends DialogFragment {
    private static final String TAG = "morphe_settings_search";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private List<SearchEntry> entries = new ArrayList<>();
    private final List<SearchEntry> results = new ArrayList<>();
    private EditText query;
    private BaseAdapter adapter;

    public static void show(Activity activity, FragmentManager manager) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()
                || manager == null || manager.isStateSaved() || manager.findFragmentByTag(TAG) != null) return;
        Fragment main = manager.findFragmentByTag(RedditPreferenceFragment.MAIN_SCREEN_TAG);
        if (!(main instanceof RedditPreferenceFragment)) return;
        try {
            new RedditSettingsSearchDialog().show(manager, TAG);
        } catch (RuntimeException exception) {
            Logger.printException(() -> "Could not open settings search", exception);
        }
    }

    private int backgroundColor() {
        return RedditSettingsStyle.isModernStyle()
                ? RedditSettingsStyle.getBackgroundColor() : ThemeUtils.getAppBackgroundColor();
    }

    private int textColor() {
        return RedditSettingsStyle.isModernStyle()
                ? RedditSettingsStyle.getTextColor() : ThemeUtils.getAppForegroundColor();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setStyle(STYLE_NO_TITLE, Utils.isDarkModeEnabled()
                ? android.R.style.Theme_DeviceDefault_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
    }

    private final Runnable filter = new Runnable() {
        @Override
        public void run() {
            if (query == null || !isAdded()) return;
            Editable editable = query.getText();
            // IMEs can remove composing spans without another text mutation. Wait for commit,
            // retaining the whole current query, instead of losing an in-progress composition.
            if (BaseInputConnection.getComposingSpanStart(editable) >= 0
                    || BaseInputConnection.getComposingSpanEnd(editable) >= 0) {
                handler.postDelayed(this, 150);
                return;
            }
            String needle = editable.toString().trim().toLowerCase(Locale.ROOT);
            results.clear();
            for (SearchEntry entry : entries) {
                String haystack = (entry.title + "\n" + entry.summary + "\n" + entry.categoryTitle)
                        .toLowerCase(Locale.ROOT);
                if (needle.isEmpty() || haystack.contains(needle)) results.add(entry);
            }
            if (adapter != null) adapter.notifyDataSetChanged();
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
        Activity activity = getActivity();
        if (activity == null) return null;
        Fragment main = getFragmentManager() != null
                ? getFragmentManager().findFragmentByTag(RedditPreferenceFragment.MAIN_SCREEN_TAG) : null;
        if (main instanceof RedditPreferenceFragment source) entries = source.buildSearchIndex();

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(backgroundColor());
        root.setFitsSystemWindows(true);

        Toolbar toolbar = new Toolbar(activity);
        toolbar.setBackgroundColor(backgroundColor());
        Drawable back = ResourceUtils.getDrawable("icon_arrow_back");
        if (back != null) {
            back = back.mutate();
            back.setTint(textColor());
            toolbar.setNavigationIcon(back);
        }
        toolbar.setNavigationContentDescription(android.R.string.cancel);
        toolbar.setNavigationOnClickListener(view -> dismiss());
        query = new EditText(activity);
        query.setSingleLine(true);
        query.setTextColor(textColor());
        query.setHintTextColor((textColor() & 0x00FFFFFF) | 0x99000000);
        query.setHint("Search settings");
        query.setTextSize(18);
        toolbar.addView(query, new Toolbar.LayoutParams(-1, -1));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(56)));

        android.widget.FrameLayout body = new android.widget.FrameLayout(activity);
        ListView list = new ListView(activity);
        list.setDivider(null);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return results.size(); }
            @Override public SearchEntry getItem(int position) { return results.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                LinearLayout row;
                TextView title;
                TextView subtitle;
                if (recycled instanceof LinearLayout existing && existing.getChildCount() == 2) {
                    row = existing;
                    title = (TextView) row.getChildAt(0);
                    subtitle = (TextView) row.getChildAt(1);
                } else {
                    row = new LinearLayout(activity);
                    row.setOrientation(LinearLayout.VERTICAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setMinimumHeight(dp(76));
                    row.setPadding(dp(20), dp(12), dp(20), dp(12));
                    title = new TextView(activity);
                    title.setTextSize(16);
                    title.setTextColor(textColor());
                    row.addView(title, new LinearLayout.LayoutParams(-1, -2));
                    subtitle = new TextView(activity);
                    subtitle.setTextSize(13);
                    subtitle.setTextColor((textColor() & 0x00FFFFFF) | 0xBB000000);
                    subtitle.setMaxLines(4);
                    row.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));
                }
                SearchEntry entry = getItem(position);
                title.setText(entry.title);
                subtitle.setText(entry.categoryTitle + (entry.summary.length() > 0 ? " · " + entry.summary : ""));
                return row;
            }
        };
        list.setAdapter(adapter);
        TextView empty = new TextView(activity);
        empty.setText("无结果");
        empty.setTextColor(textColor());
        empty.setTextSize(16);
        empty.setGravity(Gravity.CENTER);
        body.addView(list, new android.widget.FrameLayout.LayoutParams(-1, -1));
        body.addView(empty, new android.widget.FrameLayout.LayoutParams(-1, -1));
        list.setEmptyView(empty);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= results.size()) return;
            SearchEntry entry = results.get(position);
            FragmentManager manager = getFragmentManager();
            Fragment currentMain = manager != null
                    ? manager.findFragmentByTag(RedditPreferenceFragment.MAIN_SCREEN_TAG) : null;
            if (!(currentMain instanceof RedditPreferenceFragment source) || manager.isStateSaved()) return;
            Object service = activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (service instanceof InputMethodManager keyboard && query != null) {
                keyboard.hideSoftInputFromWindow(query.getWindowToken(), 0);
            }
            dismiss();
            // This queue belongs to the activity, not the search view whose teardown
            // cancels its filtering callbacks as the dismiss transaction executes.
            View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
            if (decor != null) decor.post(() -> source.openSearchResult(entry));
        });
        query.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable text) {
                handler.removeCallbacks(filter);
                handler.postDelayed(filter, 150);
            }
        });
        filter.run();
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = getDialog() != null ? getDialog().getWindow() : null;
        if (window != null) {
            window.setLayout(-1, -1);
            window.setBackgroundDrawable(new ColorDrawable(backgroundColor()));
            window.setWindowAnimations(0);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        if (query != null) query.requestFocus();
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacksAndMessages(null);
        query = null;
        adapter = null;
        entries.clear();
        results.clear();
        super.onDestroyView();
    }
}
