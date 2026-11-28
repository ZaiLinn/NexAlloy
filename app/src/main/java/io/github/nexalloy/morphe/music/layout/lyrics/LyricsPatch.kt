package io.github.nexalloy.morphe.music.layout.lyrics

import android.content.Context
import android.content.pm.ProviderInfo
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.view.View
import android.view.ViewGroup
import app.morphe.extension.music.patches.components.LyricsPanelFilter
import app.morphe.extension.music.patches.lyrics.LyricsButtonPatch
import app.morphe.extension.music.patches.lyrics.LyricsPatch as LyricsLifecycle
import app.morphe.extension.music.patches.lyrics.session.LockScreenLyrics
import app.morphe.extension.music.patches.lyrics.session.MiniplayerLyrics
import app.morphe.extension.music.patches.lyrics.ui.DeviceLyricsTab
import app.morphe.extension.music.patches.lyrics.ui.LyricsPanelInstaller
import app.morphe.extension.music.patches.lyrics.ui.LyricsShareImageProvider
import app.morphe.extension.music.settings.preference.LyricsAiPromptPreference
import app.morphe.extension.music.settings.preference.LyricsClearCachePreference
import app.morphe.extension.music.settings.preference.LyricsCustomFontPreference
import app.morphe.extension.music.settings.preference.LyricsEncapsulationPreference
import app.morphe.extension.music.settings.preference.LyricsLineLimitsPreference
import app.morphe.extension.music.settings.preference.LyricsOrderedListPreference
import app.morphe.extension.music.settings.preference.LyricsTargetLanguagePreference
import app.morphe.extension.music.settings.preference.LyricsTrackFormatPreference
import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.settings.preference.SeekBarPreference
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.findClassDirect
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.music.interaction.crossfade.MusicPauseVideoFingerprint
import io.github.nexalloy.morphe.music.interaction.crossfade.MusicPlayVideoFingerprint
import io.github.nexalloy.morphe.music.misc.litho.filter.lithoFilterPatch
import io.github.nexalloy.morphe.music.misc.mediasession.addSetMetadataHook
import io.github.nexalloy.morphe.music.misc.mediasession.addSetMetadataWithSessionHook
import io.github.nexalloy.morphe.music.misc.mediasession.addSetPlaybackStateHook
import io.github.nexalloy.morphe.music.misc.mediasession.mediaSessionHookPatch
import io.github.nexalloy.morphe.music.misc.proto.elementProtoParserHookPatch
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.music.video.information.musicVideoInformationPatch
import io.github.nexalloy.morphe.shared.misc.litho.filter.addLithoFilter
import io.github.nexalloy.morphe.shared.misc.proto.hookElement
import io.github.nexalloy.morphe.shared.misc.settings.preference.InputType
import io.github.nexalloy.morphe.shared.misc.settings.preference.ListPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.PreferenceCategory
import io.github.nexalloy.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.TextPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.screenInfoPreferenceCategory
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.WeakHashMap

/**
 * The Lyrics screen of the music settings. The key, icon and sorting match the upstream
 * music settings patch, so the screen and its preferences keep their translations.
 */
private val lyricsScreen = PreferenceScreen.Screen(
    key = "morphe_settings_music_screen_04_lyrics",
    summaryKey = null,
    icon = "@drawable/morphe_settings_screen_14_lyrics",
    iconBold = "@drawable/morphe_settings_screen_14_lyrics_bold",
    layout = "@layout/morphe_preference_with_icon",
    sorting = PreferenceScreenPreference.Sorting.UNSORTED,
)

/**
 * Proxies of the host objects that the extension's access interfaces are patched onto
 * upstream, so the extension code can be used unchanged.
 */
private val proxyTargets = WeakHashMap<Any, Any>()

private fun unwrapProxy(maybeProxy: Any?): Any? {
    if (maybeProxy == null) return null
    return synchronized(proxyTargets) { proxyTargets[maybeProxy] } ?: maybeProxy
}

private fun newAccessProxy(wrapped: Any, iface: Class<*>, invoke: (Method, Array<Any?>?) -> Any?): Any =
    Proxy.newProxyInstance(DeviceLyricsTab::class.java.classLoader, arrayOf(iface)) { _, method, args ->
        when (method.name) {
            "hashCode" -> wrapped.hashCode()
            "equals" -> wrapped == unwrapProxy(args?.firstOrNull())
            "toString" -> "NexAlloy proxy of ${wrapped.javaClass.name}"
            else -> invoke(method, args)
        }
    }.also { proxy ->
        synchronized(proxyTargets) { proxyTargets[proxy] = wrapped }
    }

/**
 * Runtime port of the music lyrics patch. The litho filter, the button hook and the
 * player callbacks wire the extension lyrics code into the app; the access interfaces
 * the patcher adds to the host classes are replaced by proxies around the same members,
 * and the extension stub methods are hooked instead of having their bodies replaced.
 */
val lyricsPatch = patch(
    name = "Third-party lyrics",
    description = "Adds an option to show synced lyrics with experience enhancement from 20+ providers in the lyrics panel.",
) {
    dependsOn(
        musicVideoInformationPatch,
        mediaSessionHookPatch,
        lithoFilterPatch,
        elementProtoParserHookPatch,
    )

    lyricsScreen.addPreferences(
        SwitchPreference("morphe_music_lyrics_enabled", summary = true),
        PreferenceCategory(
            key = "morphe_music_lyrics_section_service",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = setOf(
                NonInteractivePreference(
                    key = "morphe_music_lyrics_source",
                    titleKey = null,
                    summaryKey = null,
                    tag = LyricsOrderedListPreference::class.java,
                    selectable = false,
                    dependency = "morphe_music_lyrics_enabled"
                ),
                NonInteractivePreference(
                    key = "morphe_music_lyrics_source_advice",
                    titleKey = null,
                    summaryKey = "morphe_music_lyrics_source_advice_summary",
                    dependency = "morphe_music_lyrics_enabled"
                ),
                NonInteractivePreference(
                    key = "morphe_music_lyrics_translation_provider",
                    summaryKey = null,
                    tag = LyricsOrderedListPreference::class.java,
                    selectable = false,
                    dependency = "morphe_music_lyrics_enabled"
                ),
                NonInteractivePreference(
                    key = "morphe_music_lyrics_translation_language",
                    titleKey = null,
                    summaryKey = null,
                    tag = LyricsTargetLanguagePreference::class.java,
                    selectable = true,
                    dependency = "morphe_music_lyrics_enabled"
                ),
                NonInteractivePreference(
                    key = "morphe_music_lyrics_romanization_provider",
                    summaryKey = null,
                    tag = LyricsOrderedListPreference::class.java,
                    selectable = false,
                    dependency = "morphe_music_lyrics_enabled"
                ),
                NonInteractivePreference(
                    key = "morphe_music_lyrics_romanize_language",
                    titleKey = null,
                    summaryKey = null,
                    tag = LyricsTargetLanguagePreference::class.java,
                    selectable = true,
                    dependency = "morphe_music_lyrics_enabled"
                ),
                SwitchPreference("morphe_music_lyrics_allow_transliteration", summary = true),
                NonInteractivePreference(
                    key = "morphe_music_lyrics_clear_cache",
                    summaryKey = "morphe_music_lyrics_cache_loading_summary",
                    tag = LyricsClearCachePreference::class.java,
                    selectable = true,
                    dependency = "morphe_music_lyrics_enabled"
                )
            )
        ),
        PreferenceCategory(
            key = "morphe_music_lyrics_section_font_animation",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = setOf(
                NonInteractivePreference(
                    key = "morphe_music_lyrics_text_size",
                    summaryKey = "morphe_music_lyrics_text_size_summary",
                    tag = SeekBarPreference::class.java,
                    selectable = true,
                    dependency = "morphe_music_lyrics_enabled"
                ),
                NonInteractivePreference(
                    key = "morphe_music_lyrics_custom_font",
                    titleKey = "morphe_music_lyrics_custom_font_title",
                    summaryKey = "morphe_music_lyrics_custom_font_summary_default",
                    tag = LyricsCustomFontPreference::class.java,
                    selectable = true,
                    dependency = "morphe_music_lyrics_enabled"
                ),
                SwitchPreference("morphe_music_lyrics_word_sync", summary = true),
                SwitchPreference("morphe_music_lyrics_word_smooth_sweep", summary = true),
                SwitchPreference("morphe_music_lyrics_spring_scroll", summary = true),
                SwitchPreference("morphe_music_lyrics_relax_scroll", summary = true),
                SwitchPreference("morphe_music_lyrics_gap_dots", summary = true),
                SwitchPreference("morphe_music_lyrics_focus_spotlight", summary = true),
                SwitchPreference("morphe_music_lyrics_blur_inactive", summary = true),
                SwitchPreference("morphe_music_lyrics_line_scale", summary = true),
                SwitchPreference("morphe_music_lyrics_split_background", summary = true)
            )
        ),
        PreferenceCategory(
            key = "morphe_music_lyrics_section_text_formatting",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference("morphe_music_lyrics_force_capitalize", summary = true),
                SwitchPreference("morphe_music_lyrics_strip_trailing_punctuation", summary = true),
                SwitchPreference("morphe_music_lyrics_han_form", summary = true),
                SwitchPreference("morphe_music_lyrics_swap_trans_roma", summary = true),
                ListPreference("morphe_music_lyrics_alignment_mode")
            )
        ),
        PreferenceCategory(
            key = "morphe_music_lyrics_section_visibility",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference("morphe_music_lyrics_show_section", summary = true),
                SwitchPreference(
                    key = "morphe_music_lyrics_use_ai_section",
                    summary = true,
                    tag = LyricsAiPromptPreference::class.java
                ),
                SwitchPreference("morphe_music_lyrics_hide_played", summary = true),
                SwitchPreference("morphe_music_lyrics_hide_unplayed", summary = true),
                SwitchPreference("morphe_music_lyrics_hide_info", summary = true)
            )
        ),
        PreferenceCategory(
            key = "morphe_music_lyrics_section_panel",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference("morphe_music_lyrics_tap_to_seek", summary = true),
                SwitchPreference("morphe_music_lyrics_long_press_share_mode", summary = true),
                SwitchPreference("morphe_music_lyrics_keep_screen_on", summary = true),
                SwitchPreference("morphe_music_lyrics_dynamic_background", summary = true),
                SwitchPreference("morphe_music_lyrics_dynamic_background_animated", summary = true),
                SwitchPreference("morphe_music_lyrics_show_copy_button", summary = true),
                SwitchPreference("morphe_music_lyrics_show_refresh_button", summary = true),
                SwitchPreference("morphe_music_lyrics_show_translate_button", summary = true),
                SwitchPreference("morphe_music_lyrics_show_romanize_button", summary = true),
                SwitchPreference("morphe_music_lyrics_show_current_button", summary = true),
                SwitchPreference("morphe_music_lyrics_show_fullscreen_button", summary = true)
            )
        ),
        PreferenceCategory(
            key = "morphe_music_lyrics_section_sync",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = setOf(
                NonInteractivePreference(
                    key = "morphe_music_lyrics_offset_ms",
                    summaryKey = "morphe_music_lyrics_offset_ms_summary",
                    tag = SeekBarPreference::class.java,
                    selectable = true,
                    dependency = "morphe_music_lyrics_enabled"
                ),
                SwitchPreference("morphe_music_lyrics_sb_matching", summary = true),
                SwitchPreference("morphe_music_lyrics_miniplayer", summary = true),
                SwitchPreference("morphe_music_lyrics_mediasession", summary = true),
                NonInteractivePreference(
                    key = "morphe_music_lyrics_mini_track_format",
                    summaryKey = "morphe_music_lyrics_mini_track_format_menu_summary",
                    tag = LyricsTrackFormatPreference::class.java,
                    selectable = true,
                    dependency = "morphe_music_lyrics_enabled"
                ),
                TextPreference(
                    key = "morphe_music_lyrics_mini_encapsulation",
                    tag = LyricsEncapsulationPreference::class.java
                ),
                NonInteractivePreference(
                    // Keyed by one of the limits it edits, so it is available when they are.
                    key = "morphe_music_lyrics_mini_char_limit",
                    titleKey = "morphe_music_lyrics_mini_line_limits_title",
                    summaryKey = "morphe_music_lyrics_mini_line_limits_summary",
                    tag = LyricsLineLimitsPreference::class.java,
                    selectable = true
                ),
                TextPreference(
                    key = "morphe_music_lyrics_mini_instrumental",
                    inputType = InputType.TEXT_MULTI_LINE
                ),
                NonInteractivePreference(
                    key = "morphe_music_lyrics_mini_next_offset_ms",
                    summaryKey = "morphe_music_lyrics_mini_next_offset_ms_summary",
                    tag = SeekBarPreference::class.java,
                    selectable = true,
                    dependency = "morphe_music_lyrics_enabled"
                )
            )
        ),
        PreferenceCategory(
            key = "morphe_settings_music_lyrics_metadata",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = setOf(
                TextPreference(
                    key = "morphe_music_lyrics_custom_regex",
                    inputType = InputType.TEXT_MULTI_LINE
                ),
                TextPreference(
                    key = "morphe_music_lyrics_text_filter",
                    inputType = InputType.TEXT_MULTI_LINE
                ),
                TextPreference(
                    key = "morphe_music_lyrics_credit_line_regex",
                    inputType = InputType.TEXT_MULTI_LINE
                )
            )
        ),
        screenInfoPreferenceCategory(
            key = "morphe_music_lyrics_section_about",
            preferences = setOf(
                NonInteractivePreference(
                    key = "morphe_music_lyrics_about",
                    titleKey = null,
                    dependency = "morphe_music_lyrics_enabled"
                )
            )
        )
    )

    // The panel content is built by Elements, so there is no view to hook. The timed
    // lyrics component is the earliest signal that the opened panel is the lyrics one.
    addLithoFilter(LyricsPanelFilter())

    // The Lyrics button under the player is disabled by the server for tracks it has no
    // lyrics for, so it is enabled again to open the panel with the third-party lyrics.
    hookElement { bytes -> LyricsButtonPatch.enableLyricsButton(bytes) }

    // region Lyrics tab for device files.

    // The patcher replaces the bodies of the two extension stubs below with calls to the
    // app; the runtime module hooks them instead, with the members resolved from the host.
    val tabClass = ::lyricsTabClass.clazz
    val messageClass = tabClass.superclass
        ?: error("the tab proto class ${tabClass.name} has no message superclass")
    val tabRendererClass = ::lyricsTabRendererClass.clazz
    val builderAddTabMethod = ::lyricsTabsBuilderAddTabMethod.method
    val tabbedViewField = ::lyricsTabbedViewField.field.apply { isAccessible = true }
    val tabModelRendererField = ::lyricsTabModelRendererField.field.apply { isAccessible = true }
    val removeTabMethod = TabControllerRemoveTabFingerprint.method
    val tabRendererToBytesMethod = tabRendererClass.getMethod("toByteArray")

    val tabDefaultInstance = tabClass.declaredFields.first {
        Modifier.isStatic(it.modifiers) && it.type == tabClass
    }.apply { isAccessible = true }.get(null)

    val parseFromMethod = messageClass.declaredMethods.first {
        Modifier.isStatic(it.modifiers) &&
            it.parameterTypes.contentEquals(arrayOf(messageClass, ByteArray::class.java)) &&
            it.returnType == messageClass
    }.apply { isAccessible = true }

    DeviceLyricsTab::class.java.getDeclaredMethod("defaultTab").apply { isAccessible = true }
        .hookMethod(object : XC_MethodReplacement() {
            override fun replaceHookedMethod(param: XC_MethodHook.MethodHookParam): Any = tabDefaultInstance
        })
    DeviceLyricsTab::class.java
        .getDeclaredMethod("parseTab", Any::class.java, ByteArray::class.java)
        .apply { isAccessible = true }
        .hookMethod(object : XC_MethodReplacement() {
            override fun replaceHookedMethod(param: XC_MethodHook.MethodHookParam): Any? =
                parseFromMethod.invoke(null, param.args[0], param.args[1])
        })

    // Upstream appends the lyrics tab after each addTab call inside the response builder
    // method. The scoped hook fires after the same calls, with the builder as receiver.
    SideloadedWatchNextResponseFingerprint.hookMethod(scopedHook(builderAddTabMethod) {
        after {
            val builder = it.thisObject ?: return@after
            val builderProxy = newAccessProxy(builder, DeviceLyricsTab.TabsBuilderAccess::class.java) { _, args ->
                builderAddTabMethod.invoke(builder, args?.get(0))
            }
            DeviceLyricsTab.addTab(builderProxy)
        }
    })

    // The page of the added tab is replaced with one that only holds the panel. Upstream
    // replaces the page argument at the head of the tab controller's addTab.
    TabControllerAddTabFingerprint.hookMethod {
        before {
            val controller = it.thisObject ?: return@before
            // Xposed arguments do not include the receiver, so the model and the page of
            // the upstream p1/p3 are args[0] and args[2].
            val tabModel = it.args[0] ?: return@before
            val tabbedView = runCatching { tabbedViewField.get(controller) }.getOrNull() as? ViewGroup
                ?: return@before
            val tabProxy = newAccessProxy(tabModel, DeviceLyricsTab.TabModelAccess::class.java) { _, _ ->
                val renderer = tabModelRendererField.get(tabModel) ?: return@newAccessProxy null
                tabRendererToBytesMethod.invoke(renderer)
            }
            val controllerProxy = newAccessProxy(
                controller, DeviceLyricsTab.TabControllerAccess::class.java
            ) { _, args ->
                removeTabMethod.invoke(controller, unwrapProxy(args?.get(0)))
            }
            it.args[2] = DeviceLyricsTab.onAddTab(
                tabbedView, controllerProxy, tabProxy, it.args[2] as View?
            )
        }
    }

    // endregion

    // Some accounts get lyrics as a player tab instead of an engagement panel. A tab page can
    // be built long before it is selected, so the litho filter alone can't install the panel.
    TabbedViewTabSelectedFingerprint.hookMethod {
        before { LyricsPanelInstaller.onTabSelected(it.thisObject as ViewGroup) }
    }

    // Some accounts get the panel without a heading, and then nothing in the view tree
    // tells the lyrics panel from the other panels sharing the same container. The panel
    // the app itself put in the container is what tells them apart. Panels with a
    // heading do not need this, so a miss here leaves the rest of the patch working.
    runCatching {
        val controllerClass = EngagementPanelControllerFingerprint.declaredClass

        // Only instance fields the controller reassigns can hold the panel it shows,
        // and leaving the primitives and arrays out keeps flags from matching as a type.
        val panelFieldTypes = controllerClass.declaredFields
            .filter { field ->
                !Modifier.isStatic(field.modifiers) &&
                    !Modifier.isFinal(field.modifiers) &&
                    !field.type.isPrimitive &&
                    !field.type.isArray
            }
            .mapTo(hashSetOf()) { it.type }

        // The only method taking that type along with a flag is the one that assigns it.
        val setCurrentPanel = controllerClass.declaredMethods.single { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[1] == java.lang.Boolean.TYPE &&
                method.parameterTypes[0] in panelFieldTypes
        }
        val panelClass = setCurrentPanel.parameterTypes[0]

        // Upstream injects after the assignment of the current panel, while the argument
        // is cleared to null before the assignment when the panel is being closed, which
        // the argument state after the method returns matches.
        setCurrentPanel.hookMethod {
            after { LyricsPanelInstaller.onEngagementPanelChanged(it.args[0]) }
        }

        // The panel identifier says which panel this is in any language, and with or without
        // a heading. The app reads it with a static helper taking the panel renderer, and the
        // panel hands out its renderer through the only method of the panel type that takes
        // nothing and returns that type. The extension stub is hooked instead of rewritten.
        val identifierHelper = ::lyricsPanelIdentifierHelperMethod.method
        val rendererType = identifierHelper.parameterTypes.single()
        val rendererGetter = panelClass.declaredMethods.single { method ->
            method.parameterTypes.isEmpty() && method.returnType == rendererType
        }
        LyricsPanelInstaller::class.java
            .getDeclaredMethod("getPanelIdentifier", Any::class.java)
            .apply { isAccessible = true }
            .hookMethod(object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: XC_MethodHook.MethodHookParam): Any? {
                    val panel = param.args[0] ?: return null
                    val renderer = rendererGetter.invoke(panel) ?: return null
                    return identifierHelper.invoke(null, renderer)
                }
            })
    }.onFailure {
        Logger.printException({
            "Engagement panel controller not found, a lyrics panel shown without a heading will not be used"
        }, it)
    }

    addSetMetadataHook { metadata -> LyricsLifecycle.onSetMetadata(metadata) }
    addSetMetadataWithSessionHook { session, metadata ->
        MiniplayerLyrics.onMediaSessionSetMetadata(session!!, metadata)
    }
    addSetMetadataWithSessionHook { session, metadata ->
        LockScreenLyrics.onMediaSessionSetMetadata(session!!, metadata)
    }
    addSetPlaybackStateHook { playbackState -> LyricsLifecycle.onSetPlaybackState(playbackState) }

    MusicPauseVideoFingerprint.hookMethod {
        before { LyricsLifecycle.onPauseVideo() }
    }
    MusicPlayVideoFingerprint.hookMethod {
        before { LyricsLifecycle.onPlayVideo() }
    }

    // region Lyrics share images.

    // The patcher adds a provider to the app manifest. A runtime module cannot change the
    // manifest, so the provider is added to the list the app installs at startup instead,
    // and the host class loader resolves the module's provider class through a hook that
    // unregisters itself once the provider is installed.
    runCatching {
        val installContentProviders = XposedHelpers.findMethodExact(
            "android.app.ActivityThread", null, "installContentProviders",
            Context::class.java, List::class.java
        )
        val providerName = LyricsShareImageProvider::class.java.name
        val installingProviders = ThreadLocal.withInitial { false }

        val loadClassMethod = ClassLoader::class.java.getDeclaredMethod("loadClass", String::class.java)
        val unhook = XposedBridge.hookMethod(loadClassMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                if (param.args[0] == providerName) {
                    param.result = LyricsShareImageProvider::class.java
                }
            }
        })
        installContentProviders.hookMethod {
            before {
                val providers = it.args[1] as MutableList<ProviderInfo>
                if (providers.none { provider -> providerName == provider.name }) {
                    providers.add(ProviderInfo().apply {
                        authority = appContext.packageName + LyricsShareImageProvider.AUTHORITY_SUFFIX
                        name = providerName
                        grantUriPermissions = true
                        exported = false
                        packageName = appContext.packageName
                        applicationInfo = appContext.applicationInfo
                    })
                }
                installingProviders.set(true)
            }
            after {
                installingProviders.set(false)
                unhook.unhook()
            }
        }
    }.onFailure {
        Logger.printException({ "Lyrics share image provider not registered, sharing lyrics images will fail" }, it)
    }

    // endregion
}
