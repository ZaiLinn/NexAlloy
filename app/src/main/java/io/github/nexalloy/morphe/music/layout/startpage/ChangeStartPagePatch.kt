package io.github.nexalloy.morphe.music.layout.startpage

import android.app.Activity
import android.os.Bundle
import app.morphe.extension.music.patches.ChangeStartPagePatch
import app.morphe.extension.shared.settings.preference.SortedListPreference
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.music.misc.playservice.is_9_26_or_greater
import io.github.nexalloy.morphe.music.misc.playservice.versionCheckPatch
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.music.shared.MusicActivityOnCreateFingerprint
import io.github.nexalloy.morphe.shared.misc.settings.preference.ListPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import io.github.nexalloy.patch

val changeStartPagePatch = patch(
    name = "Change start page",
    description = "Adds an option to set which page the app opens in instead of the homepage.",
) {
    dependsOn(versionCheckPatch)

    PreferenceScreen.GENERAL.addPreferences(
        noTitleUnsortedPreferenceCategory(
            ListPreference(
                key = "morphe_change_start_page",
                tag = SortedListPreference::class.java
            )
        )
    )

    // Upstream transforms the browse id field read or the returned browse id of the cold
    // start method. At runtime the same is achieved by transforming the returned value.
    ColdStartUpFingerprint.hookMethod {
        after {
            it.result = ChangeStartPagePatch.overrideBrowseId(it.result as String?)
        }
    }

    // On cold start no root tab is active yet, and a library page that is not a
    // Library tab root (Subscriptions) opens outside the Library tab and shows blank.
    // Upstream transforms the active root tag where the library tab check reads it.
    // At runtime the same is achieved by adapting the stored field for the duration
    // of the check.
    if (is_9_26_or_greater) {
        val ownerFields = ::activeRootTagOwnerFields.fieldList
        val ownerField = ownerFields.first().apply { isAccessible = true }
        val tagField = ownerFields.last().apply { isAccessible = true }

        OpenInLibraryTabFingerprint.hookMethod {
            before {
                val owner = ownerField.get(it.thisObject) ?: return@before
                val original = tagField.get(owner)
                val overridden = ChangeStartPagePatch.overrideActiveRootTag(original as String?)
                if (overridden !== original) {
                    tagField.set(owner, overridden)
                    it.setObjectExtra("morphe_originalRootTag", original)
                }
            }
            after {
                val original = it.getObjectExtra("morphe_originalRootTag") ?: return@after
                it.setObjectExtra("morphe_originalRootTag", null)
                ownerField.get(it.thisObject)?.let { owner ->
                    tagField.set(owner, original)
                }
            }
        }
    }

    // There is no browse id assigned to the Search start page, so the search activity
    // is fired from the main activity onCreate instead.
    // Upstream injects the extension call at the start of onCreate.
    MusicActivityOnCreateFingerprint.hookMethod {
        before {
            ChangeStartPagePatch.overrideIntentActionOnCreate(
                it.thisObject as Activity,
                it.args[0] as Bundle?
            )
        }
    }
}
