package io.github.nexalloy.morphe.reddit.interaction.profiles

import app.morphe.extension.reddit.patches.SearchHiddenProfilesPatch
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook

internal val SearchHiddenProfiles = patch(
    name = "Search hidden profiles",
    description = "Searches public posts by author from profiles that hide their posts or comments.",
) {
    val username = ::profileUsernameField.field.apply { isAccessible = true }
    val filtered = ::profileFilteredField.field.apply { isAccessible = true }
    val model = ::profileModelField.field.apply { isAccessible = true }
    val openSearch = ::openProfileSearchCall.method
    ProfileSearchClickFingerprint.method.hookMethod(scopedHook(openSearch) {
        before {
            if (innerDepth != 0) return@before
            val profile = model.get(outerParam.thisObject) ?: return@before
            val opened = SearchHiddenProfilesPatch.openProfileSearch(object : SearchHiddenProfilesPatch.ProfileModel {
                override fun patch_getUsername() = username.get(profile) as? String
                override fun patch_isProfileFiltered() = filtered.getBoolean(profile)
            })
            if (opened) it.result = null
        }
    })
    setExtensionIsPatchIncluded(SearchHiddenProfilesPatch::class.java)
}
