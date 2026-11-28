/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/2029
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Runtime port of "Settings menu filter"
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/music/layout/hide/settingsmenu/HideSettingsMenuFilterPatch.kt).
 *
 * The upstream patch injects a hide-matching helper into PreferenceGroup and a filter hook
 * into Preference.setTitle, then walks the preference screen of the settings headers fragment
 * at the end of its onCreatePreferences. At runtime the walk is implemented with reflection
 * against the host classes instead, and the parsing/discovery/log helpers of the
 * compiled-in extension are called directly.
 */

package io.github.nexalloy.morphe.music.layout.hide.settingsmenu

import app.morphe.extension.music.patches.SettingsMenuFilterPatch
import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.patches.BaseSettingsMenuFilter
import app.morphe.extension.shared.patches.SettingsMenuFilterPickerPreference
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.nexalloy.patch
import java.lang.reflect.Field
import java.lang.reflect.Method

val hideSettingsMenuFilterPatch = patch(
    name = "Settings menu filter",
    description = "Adds an option to hide items on the standard YouTube Music settings screen " +
        "by their visible name.",
) {
    PreferenceScreen.GENERAL.addPreferences(
        NonInteractivePreference(
            key = "morphe_music_settings_menu_filter",
            titleKey = "morphe_settings_menu_filter_screen_title",
            summaryKey = "morphe_settings_menu_filter_screen_summary",
            tag = SettingsMenuFilterPickerPreference::class.java,
            selectable = true
        )
    )

    val hostPreferenceClass = classLoader.loadClass("androidx.preference.Preference")
    val hostPreferenceGroupClass = classLoader.loadClass("androidx.preference.PreferenceGroup")

    // All CharSequence fields of Preference. Field reads are required because R8 inlines
    // setTitle into the constructor for XML-inflated titles, so a preference that was never
    // through setTitle still has its title stored in a raw field.
    val preferenceTextFields: List<Field> = hostPreferenceClass.declaredFields
        .filter { it.type == CharSequence::class.java }
        .onEach { it.isAccessible = true }

    // The backing list field of PreferenceGroup.
    val groupChildrenField = hostPreferenceGroupClass.declaredFields
        .first { it.type == List::class.java }
        .apply { isAccessible = true }

    val hostPreferenceVisibleField: Field = ::preferenceVisibleField.field.apply { isAccessible = true }
    val hostSetVisibleMethod: Method = ::preferenceSetVisibleMethod.method

    /**
     * Walks the preference tree, captures every visited title for the picker,
     * and hides the preferences whose text fields match a needle.
     * Mirrors the hide-matching helper that upstream injects into PreferenceGroup.
     */
    fun hideMatching(group: Any, needles: Array<String>, parentTitle: CharSequence?) {
        val children = groupChildrenField.get(group) as? List<*> ?: return
        if (children.isEmpty()) return

        val hiding = needles.isNotEmpty()
        var invisibleChildren = 0

        for (child in children) {
            val preference = child ?: continue

            BaseSettingsMenuFilter.markWalked(preference)

            // Pass 1: pick the title for capture + recursion parent context.
            var title: CharSequence? = null
            for (field in preferenceTextFields) {
                val value = field.get(preference) as? CharSequence
                if (value != null) {
                    title = value
                    break
                }
            }
            if (title != null) {
                BaseSettingsMenuFilter.capture(parentTitle, title)
            }

            // Pass 2: hide only when needles are present.
            if (hiding) {
                for (field in preferenceTextFields) {
                    val value = field.get(preference) as? CharSequence ?: continue
                    if (!BaseSettingsMenuFilter.equalsAny(value, needles)) continue

                    // Log + hide only if this preference is currently visible, so nested
                    // groups still get processed for capture and their own hide pass.
                    if (hostPreferenceVisibleField.getBoolean(preference)) {
                        BaseSettingsMenuFilter.logHidden(value)
                        hostSetVisibleMethod.invoke(preference, false)
                    }
                    break
                }
            }

            // Nested groups are processed for capture and their own hide pass regardless.
            if (hostPreferenceGroupClass.isInstance(preference)) {
                hideMatching(preference, needles, title)
            }

            // Counting + self-hide are meaningful only when actually hiding.
            if (hiding && !hostPreferenceVisibleField.getBoolean(preference)) {
                invisibleChildren++
            }
        }

        // All direct children invisible? Hide self so an empty category header disappears.
        if (hiding && invisibleChildren == children.size) {
            hostSetVisibleMethod.invoke(group, false)
        }
    }

    /**
     * Reads the preference screen of the settings headers fragment, whose
     * getPreferenceScreen() is inherited from the host's androidx.preference copy.
     */
    fun preferenceScreenOf(fragment: Any): Any? {
        var clazz: Class<*>? = fragment.javaClass
        while (clazz != null) {
            val method = runCatching { clazz.getDeclaredMethod("getPreferenceScreen") }.getOrNull()
            if (method != null) {
                method.isAccessible = true
                return method.invoke(fragment)
            }
            clazz = clazz.superclass
        }
        return null
    }

    // Catches async title assignments that skip the tree walk.
    PreferenceSetTitleFingerprint.hookMethod {
        before {
            val preference = it.thisObject
            val title = it.args[0] as? CharSequence

            BaseSettingsMenuFilter.scopedCapture(preference, title)

            val needles = SettingsMenuFilterPatch.getNeedles()
            if (needles != null && title != null &&
                BaseSettingsMenuFilter.equalsAny(title, needles) &&
                hostPreferenceVisibleField.getBoolean(preference)
            ) {
                BaseSettingsMenuFilter.logHidden(title)
                hostSetVisibleMethod.invoke(preference, false)
            }
        }
    }

    // The upstream patch walks the screen at the end of onCreatePreferences of the
    // settings headers fragment, after the screen of the fragment is set.
    SettingsHeadersOnCreatePreferencesFingerprint.hookMethod {
        after {
            if (it.hasThrowable()) return@after

            val needles = SettingsMenuFilterPatch.getNeedles() ?: return@after
            val screen = preferenceScreenOf(it.thisObject ?: return@after) ?: return@after

            BaseSettingsMenuFilter.beginCapture()
            try {
                hideMatching(screen, needles, null)
            } catch (exception: Exception) {
                Logger.printException({ "Settings menu filter walk failure" }, exception)
            }
            BaseSettingsMenuFilter.endCapture()
        }
    }
}
