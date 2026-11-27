/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.reddit.patches;

/**
 * Standalone patch to hide Reddit search from the text selection contextual menu.
 *
 * The compiled patch changes the MIME type of the Answers text selection activity
 * in the manifest. In the Xposed runtime form the module switches the enabled state
 * of the activity component instead, since the manifest cannot be changed at runtime.
 */
@SuppressWarnings("unused")
public class HideRedditSearchPatch {

    /**
     * @return If this patch was included during patching.
     */
    @SuppressWarnings("SameReturnValue")
    public static boolean isPatchIncluded() {
        return false;  // Modified during patching.
    }
}
