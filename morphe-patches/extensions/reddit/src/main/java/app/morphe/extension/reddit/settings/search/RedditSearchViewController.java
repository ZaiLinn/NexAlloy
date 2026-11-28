package app.morphe.extension.reddit.settings.search;

import android.app.Activity;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.SearchView;
import android.widget.Toolbar;

import java.util.List;

import app.morphe.extension.reddit.settings.preference.RedditPreferenceFragment;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.settings.preference.SettingPreference;
import app.morphe.extension.shared.settings.search.BaseSearchViewController;

/** Uses the same search, history and result controls as YouTube and Music. */
@SuppressWarnings("deprecation")
public final class RedditSearchViewController extends BaseSearchViewController {
    private CharSequence titleBeforeSearch;

    public RedditSearchViewController(Activity activity, Toolbar toolbar, RedditPreferenceFragment fragment,
                                      View root, Window window, Runnable navigateUp) {
        super(activity, toolbar, new FragmentAdapter(fragment), root, window, navigateUp);
        initializeSearchData();
    }

    public static FrameLayout wrapToolbar(Activity activity, Toolbar toolbar) {
        FrameLayout parent = new FrameLayout(activity);
        parent.setLayoutParams(toolbar.getLayoutParams());
        parent.addView(toolbar, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout container = (FrameLayout) android.view.LayoutInflater.from(activity).inflate(
                ResourceUtils.getLayoutIdentifier("morphe_settings_search_view"), parent, false);
        // The Reddit toolbar has elevation; its background must not cover the search field.
        container.setElevation(toolbar.getElevation() + toolbar.getTranslationZ() + 1);
        parent.addView(container, new FrameLayout.LayoutParams(-1, -1));
        return parent;
    }

    @Override
    protected void openSearch() {
        titleBeforeSearch = toolbar.getTitle();
        updatePreferences(fragment.getPreferenceScreenForSearch());
        super.openSearch();
    }

    @Override
    public void closeSearch() {
        super.closeSearch();
        toolbar.setTitle(titleBeforeSearch);
    }

    @Override
    protected void refreshSearchResults() {
        updatePreferences(fragment.getPreferenceScreenForSearch());
        super.refreshSearchResults();
    }

    public void highlightResult(android.widget.ListView list, int position) {
        searchResultsAdapter.highlightPreferenceAtPosition(list, position);
    }

    private static void updatePreferences(PreferenceGroup group) {
        if (group == null) return;
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference preference = group.getPreference(i);
            if (preference instanceof SettingPreference setting) setting.updateFromSetting();
            if (preference instanceof PreferenceGroup children) updatePreferences(children);
        }
    }

    private record FragmentAdapter(RedditPreferenceFragment fragment) implements BasePreferenceFragment {
        @Override public PreferenceScreen getPreferenceScreenForSearch() {
            return fragment.getPreferenceScreenForSearch();
        }
        @Override public View getView() { return fragment.getView(); }
        @Override public Activity getActivity() { return fragment.getActivity(); }
        @Override public Runnable getSearchResultNavigation(Preference preference, List<String> keys) {
            if (keys.isEmpty()) return null;
            return () -> fragment.openSearchResult(keys.get(0), preference);
        }
    }
}
