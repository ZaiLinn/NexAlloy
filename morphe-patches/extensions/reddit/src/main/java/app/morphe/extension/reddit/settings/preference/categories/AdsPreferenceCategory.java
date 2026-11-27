/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.reddit.settings.preference.categories;

import static app.morphe.extension.shared.StringRef.str;

import android.content.Context;
import android.preference.PreferenceScreen;

import app.morphe.extension.reddit.patches.HideAdsPatch;
import app.morphe.extension.reddit.patches.HidePremiumUpsellsPatch;
import app.morphe.extension.reddit.settings.Settings;
import app.morphe.extension.reddit.settings.preference.BooleanSettingPreference;
import app.morphe.extension.shared.Utils;

@SuppressWarnings("deprecation")
public class AdsPreferenceCategory extends ConditionalPreferenceCategory {
    public AdsPreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle(str("morphe_screen_ads_title"));
    }

    @Override
    public boolean getSettingsStatus() {
        return HideAdsPatch.isPatchIncluded() || HidePremiumUpsellsPatch.isPatchIncluded();
    }

    @Override
    public void addPreferences(Context context) {
        addPreference(new BooleanSettingPreference(
                context,
                Settings.HIDE_COMMENT_ADS
        ));
        addPreference(new BooleanSettingPreference(
                context,
                Settings.HIDE_POST_ADS
        ));
        // The upsell hooks only exist in the ported version, so the toggle is
        // hidden on app versions whose patch set does not include them.
        if (HidePremiumUpsellsPatch.isPatchIncluded()) {
            addPreference(new BooleanSettingPreference(
                    context,
                    Settings.HIDE_UPSELLS
            ));
        }

        Utils.setPreferenceTitlesToMultiLine(this);
    }
}
