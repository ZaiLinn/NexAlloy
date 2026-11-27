package io.github.nexalloy.morphe.youtube.layout.feedcards

import app.morphe.extension.youtube.patches.FeedCardLayoutPatch
import app.morphe.extension.youtube.patches.FeedCardLayoutPatch.FeedCardLayout
import app.morphe.extension.youtube.settings.Settings
import io.github.nexalloy.morphe.shared.misc.proto.hookElement
import io.github.nexalloy.morphe.shared.misc.settings.preference.ListPreference
import io.github.nexalloy.morphe.youtube.misc.proto.elementProtoParserHookPatch
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch

internal val feedCardLayoutPatch = patch(
    name = "Feed card layout",
    description = "Adds an option to show YouTube's classic feed layout.",
) {
    dependsOn(elementProtoParserHookPatch)

    PreferenceScreen.FEED.addPreferences(
        ListPreference(
            key = "morphe_feed_card_layout",
            entriesKey = "nexalloy_feed_card_layout_entries",
            entryValuesKey = "nexalloy_feed_card_layout_entry_values",
        )
    )
    hookElement { bytes ->
        if (bytes != null && Settings.FEED_CARD_LAYOUT.get() == FeedCardLayout.CLASSIC) {
            FeedCardLayoutPatch.setFeedCardLayout(bytes)
        } else {
            bytes
        }
    }
}
