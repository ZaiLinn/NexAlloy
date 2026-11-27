/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.reddit.patches;

import app.morphe.extension.reddit.settings.Settings;

@SuppressWarnings("unused")
public class HidePremiumUpsellsPatch {

    /**
     * @return If this patch was included during patching.
     */
    @SuppressWarnings("SameReturnValue")
    public static boolean isPatchIncluded() {
        return false;  // Modified during patching.
    }

    /**
     * Injection point. Gates the runtime upsell hooks (settings rows, drawer items and
     * the profile feed GraphQL flag). Read at hook time, never cached.
     */
    public static boolean hideUpsells() {
        return Settings.HIDE_UPSELLS.get();
    }

    /**
     * Injection point. Gates the silent refresh of the drawer lists (Favorites,
     * Your Communities): their loading spinner footer is left out. Read at hook
     * time, never cached.
     */
    public static boolean silentDrawerRefresh() {
        return Settings.SILENT_DRAWER_REFRESH.get();
    }
}
