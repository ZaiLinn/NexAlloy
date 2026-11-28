/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.reddit.settings.preference;

import static app.morphe.extension.shared.StringRef.str;

import android.app.FragmentManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;

import java.util.ArrayList;
import java.util.List;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ListView;

import androidx.annotation.Nullable;

import app.morphe.extension.reddit.settings.RedditActivityHook;
import app.morphe.extension.reddit.settings.preference.categories.AdsPreferenceCategory;
import app.morphe.extension.reddit.settings.preference.categories.ConditionalPreferenceCategory;
import app.morphe.extension.reddit.settings.preference.categories.FiltersPreferenceCategory;
import app.morphe.extension.reddit.settings.preference.categories.LayoutPreferenceCategory;
import app.morphe.extension.reddit.settings.preference.categories.MiscellaneousPreferenceCategory;
import app.morphe.extension.reddit.settings.preference.categories.NavigationBarPreferenceCategory;
import app.morphe.extension.reddit.settings.preference.categories.PostButtonsPreferenceCategory;
import app.morphe.extension.reddit.settings.preference.categories.SidebarPreferenceCategory;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.settings.preference.AbstractPreferenceFragment;
import app.morphe.extension.shared.theme.ThemeUtils;

/**
 * Preference fragment for the Reddit Morphe settings.
 * <p>
 * The main screen lists the settings screens, and each screen opens its
 * category in place, with the toolbar title and the back stack tracking
 * the current level, like the settings of the other apps.
 */
@SuppressWarnings("deprecation")
public class RedditPreferenceFragment extends AbstractPreferenceFragment {

    /** Arguments key of the category key of a category screen. */
    private static final String EXTRA_CATEGORY = "morphe_settings_category";
    /** Arguments key of the title of a category screen. */
    private static final String EXTRA_CATEGORY_TITLE = "morphe_settings_category_title";
    private static final String EXTRA_TARGET_KEY = "morphe_settings_target_key";
    private static final String EXTRA_TARGET_POSITION = "morphe_settings_target_position";

    // Put the broad, frequently used categories first; keep infrequent utilities last.
    private static final String[][] SCREENS = {
            {"layout", "morphe_screen_layout_title", "morphe_settings_screen_04_general"},
            {"filters", "morphe_screen_filters_title", "morphe_settings_screen_03_feed"},
            {"ads", "morphe_screen_ads_title", "morphe_settings_screen_01_ads"},
            {"postButtons", "morphe_screen_post_buttons_title", "morphe_settings_screen_07_seekbar"},
            {"navigationBar", "morphe_screen_navigation_bar_title", "morphe_settings_screen_08_swipe_controls"},
            {"sidebar", "morphe_screen_sidebar_title", "morphe_settings_screen_00_about"},
            {"miscellaneous", "morphe_screen_miscellaneous_title", "morphe_settings_screen_11_misc"},
    };

    /** Tag of the main screen, which stays on the back stack under the category screens. */
    public static final String MAIN_SCREEN_TAG = "morphe_settings_main";

    /**
     * Restyles the rows if the app uses the Compose settings.
     */
    @Nullable
    private ViewTreeObserver.OnPreDrawListener styleListener;

    @Override
    protected void initialize() {
        Context context = getContext();

        PreferenceScreen preferenceScreen = getPreferenceManager().createPreferenceScreen(context);
        setPreferenceScreen(preferenceScreen);

        String category = getCategoryKey();
        if (category != null) {
            // Second level: the preferences of one category.
            newCategory(context, preferenceScreen, category);
        } else {
            for (String[] screen : SCREENS) {
                addScreenRow(preferenceScreen, screen[0], screen[1], screen[2]);
            }
        }
    }

    @Nullable
    private String getCategoryKey() {
        Bundle arguments = getArguments();
        if (arguments == null) return null;
        String category = arguments.getString(EXTRA_CATEGORY);
        return category != null && !category.isEmpty() ? category : null;
    }

    /**
     * Creates the category of [key] on [screen]. Returns null for an unknown key.
     */
    @Nullable
    private static ConditionalPreferenceCategory newCategory(
            Context context, PreferenceScreen screen, String key) {
        switch (key) {
            case "ads": return new AdsPreferenceCategory(context, screen);
            case "filters": return new FiltersPreferenceCategory(context, screen);
            case "navigationBar": return new NavigationBarPreferenceCategory(context, screen);
            case "sidebar": return new SidebarPreferenceCategory(context, screen);
            case "postButtons": return new PostButtonsPreferenceCategory(context, screen);
            case "layout": return new LayoutPreferenceCategory(context, screen);
            case "miscellaneous": return new MiscellaneousPreferenceCategory(context, screen);
            default: return null;
        }
    }

    /**
     * Adds the row of one settings screen, which opens the category of the screen.
     * A category that is hidden by its patch set does not get a row, like the
     * category itself on the flat screen of the previous layout.
     */
    private void addScreenRow(PreferenceScreen screen, String key, String titleKey, String iconKey) {
        Context context = getContext();

        // The category only attaches to a screen while its settings are used,
        // which also tells the visibility of the row.
        PreferenceScreen probe = getPreferenceManager().createPreferenceScreen(context);
        ConditionalPreferenceCategory category = newCategory(context, probe, key);
        if (category == null || probe.getPreferenceCount() == 0) return;

        CharSequence title = str(titleKey);
        Preference row = new Preference(context);
        row.setTitle(title);
        row.setIcon(getScreenIcon(iconKey));
        row.setOnPreferenceClickListener(preference -> {
            openCategory(key, title);
            return true;
        });
        screen.addPreference(row);
    }

    @Nullable
    private static Drawable getScreenIcon(String iconKey) {
        try {
            Drawable icon = ResourceUtils.getDrawable(iconKey);
            if (icon != null) {
                icon = icon.mutate();
                icon.setTint(RedditSettingsStyle.isModernStyle()
                        ? RedditSettingsStyle.getTextColor() : ThemeUtils.getAppForegroundColor());
            }
            return icon;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Opens the category screen of [key] over the main screen,
     * which stays on the back stack for the toolbar back button and the system back.
     */
    private void openCategory(String key, CharSequence title) {
        openCategory(key, title, null, -1);
    }

    private void openCategory(String key, CharSequence title, @Nullable String targetKey, int targetPosition) {
        FragmentManager fragmentManager = getFragmentManager();
        if (!isAdded() || fragmentManager == null || fragmentManager.isStateSaved()) return;
        // A category screen never opens another screen.
        if (fragmentManager.getBackStackEntryCount() > 0) return;

        RedditPreferenceFragment fragment = new RedditPreferenceFragment();
        Bundle arguments = new Bundle();
        arguments.putString(EXTRA_CATEGORY, key);
        arguments.putCharSequence(EXTRA_CATEGORY_TITLE, title);
        arguments.putString(EXTRA_TARGET_KEY, targetKey);
        arguments.putInt(EXTRA_TARGET_POSITION, targetPosition);
        fragment.setArguments(arguments);

        // Keep the main screen on the back stack.
        fragmentManager.beginTransaction()
                .add(getContainerId(), fragment, key)
                .hide(this)
                .addToBackStack(key)
                .commit();

        fragmentManager.executePendingTransactions();
    }

    private PreferenceScreen searchScreen;

    /** A complete, patch-filtered tree for the shared settings search controller. */
    public PreferenceScreen getPreferenceScreenForSearch() {
        if (searchScreen != null) return searchScreen;
        Context context = getContext();
        if (context == null || !isAdded()) return null;
        searchScreen = getPreferenceManager().createPreferenceScreen(context);
        for (String[] screen : SCREENS) {
            PreferenceScreen categoryScreen = getPreferenceManager().createPreferenceScreen(context);
            categoryScreen.setKey(screen[0]);
            categoryScreen.setTitle(str(screen[1]));
            ConditionalPreferenceCategory category = newCategory(context, categoryScreen, screen[0]);
            if (category != null && categoryScreen.getPreferenceCount() > 0) {
                // The category screen supplies the path; avoid repeating its title underneath.
                category.setTitle(null);
                searchScreen.addPreference(categoryScreen);
            }
        }
        return searchScreen;
    }

    /** Shared search navigates through this host's fragment-based category screens. */
    public void openSearchResult(String categoryKey, @Nullable Preference preference) {
        FragmentManager manager = getFragmentManager();
        if (!isAdded() || manager == null || manager.isStateSaved()) return;
        for (String[] screen : SCREENS) {
            if (!screen[0].equals(categoryKey)) continue;
            PreferenceGroup category = (PreferenceGroup) getPreferenceScreenForSearch().findPreference(categoryKey);
            int position = preference == null ? -1 : findIdentityPosition(category, preference, new int[]{0});
            manager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            openCategory(categoryKey, str(screen[1]), preference != null ? preference.getKey() : null, position);
            return;
        }
    }

    private static int findIdentityPosition(PreferenceGroup group, Preference target, int[] position) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference preference = group.getPreference(i);
            int current = position[0]++;
            if (preference == target) return current;
            if (preference instanceof PreferenceGroup children) {
                int found = findIdentityPosition(children, target, position);
                if (found >= 0) return found;
            }
        }
        return -1;
    }

    private static int findPosition(PreferenceGroup group, String key, int[] position) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference preference = group.getPreference(i);
            int currentPosition = position[0]++;
            if (key.equals(preference.getKey())) return currentPosition;
            if (preference instanceof PreferenceGroup children) {
                int found = findPosition(children, key, position);
                if (found >= 0) return found;
            }
        }
        return -1;
    }

    /**
     * Returns to the main screen.
     */
    public static void navigateUp(FragmentManager fragmentManager) {
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStack();
        }
    }

    /**
     * The id of the view group this fragment was placed in.
     */
    private int getContainerId() {
        final int id = getId();
        if (id != View.NO_ID) return id;

        // Fallback for a fragment restored without a container id.
        if (getView() != null) {
            final int parentId = getView().getParent() instanceof View ? ((View) getView().getParent()).getId() : View.NO_ID;
            if (parentId != View.NO_ID) return parentId;
        }
        throw new IllegalStateException("The preference fragment has no container");
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackgroundColor(RedditSettingsStyle.isModernStyle()
                ? RedditSettingsStyle.getBackgroundColor()
                : ThemeUtils.getAppBackgroundColor());

        if (getCategoryKey() == null) RedditActivityHook.initializeSearch(this);

        ListView listView = view.findViewById(android.R.id.list);
        if (listView != null) {
            if (RedditSettingsStyle.isModernStyle()) {
                styleListener = RedditSettingsStyle.styleList(listView);
            }
            Bundle arguments = getArguments();
            if (arguments != null && arguments.containsKey(EXTRA_TARGET_POSITION)) {
                String targetKey = arguments.getString(EXTRA_TARGET_KEY);
                PreferenceScreen screen = getPreferenceScreen();
                int keyedPosition = targetKey != null && screen != null
                        ? findPosition(screen, targetKey, new int[]{0}) : -1;
                int targetPosition = keyedPosition >= 0 ? keyedPosition
                        : arguments.getInt(EXTRA_TARGET_POSITION, -1);
                if (targetPosition >= 0) {
                    // PreferenceFragment binds its adapter after onViewCreated.
                    listView.post(() -> listView.post(() -> {
                        if (isAdded() && getView() == view && listView.getAdapter() != null) {
                            int count = listView.getAdapter().getCount();
                            if (count > 0) {
                                int selected = Math.min(targetPosition, count - 1);
                                listView.setSelection(selected);
                                listView.postDelayed(() -> {
                                    if (isAdded() && getView() == view) {
                                        RedditActivityHook.highlightSearchResult(listView, selected);
                                    }
                                }, 150);
                            }
                        }
                    }));
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // The toolbar of the settings dialog shows the level of this screen,
        // and returns to the label of the main screen when navigating up.
        updateToolbarTitle();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) updateToolbarTitle();
    }

    private void updateToolbarTitle() {
        String category = getCategoryKey();
        Bundle arguments = getArguments();
        CharSequence title = category != null && arguments != null
                ? arguments.getCharSequence(EXTRA_CATEGORY_TITLE)
                : RedditActivityHook.getSettingLabel();
        RedditActivityHook.setSettingsToolbarTitle(title);
    }

    @Override
    public void onDestroyView() {
        View rootView = getView();
        if (styleListener != null && rootView != null) {
            rootView.getViewTreeObserver().removeOnPreDrawListener(styleListener);
        }
        styleListener = null;
        if (getCategoryKey() == null) RedditActivityHook.destroySearch(this);
        searchScreen = null;
        super.onDestroyView();
    }
}
