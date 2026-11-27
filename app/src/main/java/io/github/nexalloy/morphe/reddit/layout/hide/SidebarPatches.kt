package io.github.nexalloy.morphe.reddit.layout.hide

import app.morphe.extension.reddit.patches.HideSidebarComponentsPatch
import app.morphe.extension.reddit.patches.SettingsInSidebarPatch
import app.morphe.extension.reddit.settings.RedditActivityHook
import app.morphe.extension.reddit.settings.Settings
import app.morphe.extension.shared.ResourceUtils
import io.github.nexalloy.PatchExecutor
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.reddit.misc.settings.SettingsHook
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch

private fun PatchExecutor.hookDrawerBoundList(transform: (List<*>) -> List<*>) {
    val bind = CommunityDrawerBindListFingerprint.method
    val listField = ::drawerBoundList.field.apply { isAccessible = true }
    val presenterField = bind.declaringClass.declaredFields.single { it.type == listField.declaringClass }
        .apply { isAccessible = true }
    bind.hookMethod {
        before {
            val presenter = presenterField.get(it.thisObject) ?: return@before
            val original = listField.get(presenter) as? List<*> ?: return@before
            val updated = transform(original)
            if (updated !== original) listField.set(presenter, updated)
        }
    }
}

val RedditHideSidebarComponents = patch(
    name = "Hide sidebar components",
    description = "Hides individual items and groups in the community drawer."
) {
    dependsOn(SettingsHook)
    val builder = CommunityDrawerBuilderFingerprint.method
    val headerClass = HeaderItemUiModelToStringFingerprint.declaredClass
    val headerField = headerClass.declaredFields.single {
        it.type.name == "com.reddit.screens.drawer.community.HeaderItem"
    }.apply { isAccessible = true }
    val ctaTitle = ::ctaTitleResource.field.apply { isAccessible = true }
    val genericTitle = ::genericTitleResource.field.apply { isAccessible = true }
    val dividerClass = DividerItemUiModelToStringFingerprint.declaredClass

    hookDrawerBoundList { rows ->
        var previousWasDivider = true
        val filtered = rows.filter { item ->
            val divider = dividerClass.isInstance(item)
            val keep = !divider || !previousWasDivider
            previousWasDivider = divider
            keep
        }
        if (filtered.size != rows.size) filtered else rows
    }
    builder.hookMethod {
        before {
            val candidates = it.args[1] as? Collection<*> ?: return@before
            if (candidates.isEmpty()) return@before
            val header = it.args[2]
            if (headerClass.isInstance(header)) {
                val section = headerField.get(header) as? Enum<*>
                val setting = when (section?.name) {
                    "ABOUT" -> Settings.HIDE_ABOUT_SHELF
                    "GAMES_ON_REDDIT" -> Settings.HIDE_GAMES_ON_REDDIT_SHELF
                    "RECENTLY_VISITED" -> Settings.HIDE_RECENTLY_VISITED_SHELF
                    "REDDIT_PRO" -> Settings.HIDE_REDDIT_PRO_SHELF
                    "RESOURCES" -> Settings.HIDE_RESOURCES_SHELF
                    else -> null
                }
                if (setting?.get() == true) it.args[1] = emptyList<Any>()
            } else if (header == null && candidates.size == 1) {
                val item = candidates.first()
                val titleField = when {
                    ctaTitle.declaringClass.isInstance(item) -> ctaTitle
                    genericTitle.declaringClass.isInstance(item) -> genericTitle
                    else -> return@before
                }
                val titleId = titleField.getInt(item)
                val hidden = titleId != 0 && (
                    Settings.HIDE_POPULAR_SHELF.get() && titleId == ResourceUtils.getStringIdentifier("popular_feed_label") ||
                    Settings.HIDE_LATEST_SHELF.get() && titleId == ResourceUtils.getStringIdentifier("latest_feed_label") ||
                    Settings.HIDE_DISCOVER_COMMUNITIES_SHELF.get() && titleId == ResourceUtils.getStringIdentifier("communities_cta_title") ||
                    Settings.HIDE_START_A_COMMUNITY_SHELF.get() && titleId == ResourceUtils.getStringIdentifier("label_start_a_community")
                )
                if (hidden) it.args[1] = emptyList<Any>()
            }
        }
    }
    setExtensionIsPatchIncluded(HideSidebarComponentsPatch::class.java)
}

val RedditSettingsInSidebar = patch(
    name = "Settings in sidebar",
    description = "Adds Morphe settings to the top of the community drawer."
) {
    dependsOn(SettingsHook)
    val rowConstructor = CtaItemUiModelToStringFingerprint.declaredClass.getDeclaredConstructor(
        Boolean::class.javaPrimitiveType,
        Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType,
        Long::class.javaPrimitiveType,
    ).apply { isAccessible = true }
    val itemClick = ::drawerItemClick.method
    val screenField = ::drawerScreen.field.apply { isAccessible = true }
    val close = ::drawerClose.method.apply { isAccessible = true }

    val settingsRow by lazy {
        val title = ResourceUtils.getStringIdentifier("morphe_settings_title")
        val icon = ResourceUtils.getDrawableIdentifier("morphe_settings_icon_reddit")
        if (title == 0 || icon == 0) null
        else rowConstructor.newInstance(false, title, icon, Long.MIN_VALUE)
    }
    hookDrawerBoundList { rows ->
        val row = if (Settings.SETTINGS_IN_SIDEBAR.get()) settingsRow else null
        if (row == null || rows.any { it === row }) rows else listOf(row) + rows
    }
    itemClick.hookMethod {
        before {
            if (it.args[0] == null || it.args[0] !== settingsRow) return@before
            close.invoke(screenField.get(it.thisObject))
            RedditActivityHook.openSettings()
            it.result = null
        }
    }
    setExtensionIsPatchIncluded(SettingsInSidebarPatch::class.java)
}
