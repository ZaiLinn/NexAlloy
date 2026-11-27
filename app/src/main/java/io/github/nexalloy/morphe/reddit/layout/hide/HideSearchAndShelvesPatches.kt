package io.github.nexalloy.morphe.reddit.layout.hide

import app.morphe.extension.reddit.patches.HideAnswersPreviewPatch
import app.morphe.extension.reddit.patches.HideAskButtonPatch
import app.morphe.extension.reddit.patches.HideCommunitiesShelfPatch
import app.morphe.extension.reddit.patches.HideCommunityHighlightsPatch
import app.morphe.extension.reddit.patches.HideCommunityMenuBadgePatch
import app.morphe.extension.reddit.patches.HidePeopleAlsoSearchForSectionPatch
import app.morphe.extension.reddit.patches.HideRelatedPostsSectionPatch
import app.morphe.extension.reddit.patches.HideTrendingShelvesPatch
import app.morphe.extension.reddit.settings.Settings
import io.github.nexalloy.PatchExecutor
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.reddit.misc.flag.hookRedditFeatureFlag
import io.github.nexalloy.morphe.reddit.misc.settings.SettingsHook
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook
import java.util.Locale

val RedditHideCommunitiesShelf = patch(
    name = "Hide communities shelf",
    description = "Hides the related or suggested communities shelf in subreddits."
) {
    dependsOn(SettingsHook)
    val sections = listOf(
        CommunityRecommendationSection_2026_18_Fingerprint.method,
        CommunityRecommendationsComposeMethodFingerprint.method,
    )
    for (section in sections) {
        section.hookMethod {
            before { if (HideCommunitiesShelfPatch.hideCommunitiesShelf()) it.result = null }
        }
    }
    setExtensionIsPatchIncluded(HideCommunitiesShelfPatch::class.java)
}

val RedditHideRelatedPostsSection = patch(
    name = "Hide Related Posts section",
    description = "Hides Related Posts below the comments of a post."
) {
    dependsOn(SettingsHook)
    skipWhen(RelatedPostSectionContentFingerprint, HideRelatedPostsSectionPatch::hideRelatedPostsSection)
    setExtensionIsPatchIncluded(HideRelatedPostsSectionPatch::class.java)
}

val RedditHideAnswersPreview = patch(
    name = "Hide Answers preview",
    description = "Hides the Reddit Answers preview above search results."
) {
    dependsOn(SettingsHook)
    val sections = listOf(
        SearchAnswersPreviewSectionToStringFingerprint.declaredClass,
        SearchAnswersPreviewStreamingSectionToStringFingerprint.declaredClass,
    )
    val contentMethods = sections.map { section ->
        val abstractContent = section.interfaces.single().declaredMethods
            .single { it.returnType == Void.TYPE }
        section.getDeclaredMethod(abstractContent.name, *abstractContent.parameterTypes)
    }
    for (method in contentMethods) {
        method.hookMethod {
            before { if (HideAnswersPreviewPatch.hideAnswersPreview()) it.result = null }
        }
    }
    setExtensionIsPatchIncluded(HideAnswersPreviewPatch::class.java)
}

val RedditHideAskButton = patch(
    name = "Hide Ask button",
    description = "Hides Ask in the Reddit search bar."
) {
    dependsOn(SettingsHook)
    val button = AskButtonComposableFingerprint.method
    hookRedditFeatureFlag(HideAskButtonPatch::hideAskButton)
    button.hookMethod {
        before { if (HideAskButtonPatch.shouldHideAskButton()) it.result = null }
    }
    setExtensionIsPatchIncluded(HideAskButtonPatch::class.java)
}

val RedditHideCommunityMenuBadge = patch(
    name = "Hide community menu badge",
    description = "Hides the red dot on the community menu button."
) {
    dependsOn(SettingsHook)
    val badgeContainer = CommunityMenuBadgeFingerprint.method
    val badge = ::communityMenuBadgeComposable.method
    val testTag = ::communityMenuBadgeTestTag.method
    badgeContainer.hookMethod(scopedHook(
        testTag to {
            after {
                if (!it.hasThrowable() && it.args[1] == "community_menu_badge") {
                    outerParam.setObjectExtra("redditCommunityMenuBadgeModifier", it.result)
                }
            }
        },
        badge to {
            before {
                val modifier = outerParam.getObjectExtra("redditCommunityMenuBadgeModifier") ?: return@before
                if (it.args[1] === modifier && HideCommunityMenuBadgePatch.hideCommunityMenuBadge()) {
                    it.result = null
                }
            }
        },
    ))
    setExtensionIsPatchIncluded(HideCommunityMenuBadgePatch::class.java)
}

val RedditHideCommunityHighlights = patch(
    name = "Hide community highlights",
    description = "Hides Community highlights at the top of subreddits."
) {
    dependsOn(SettingsHook)
    val highlights = CommunityHighlightsFingerprint.method
    val animatedContent = ::highlightsAnimatedContent.method
    highlights.hookMethod(scopedHook(animatedContent) {
        before {
            if (it.args[4] == "content visibility" && it.args[0] is Boolean) {
                it.args[0] = HideCommunityHighlightsPatch.hideCommunityHighlights(it.args[0] as Boolean)
            }
        }
    })
    setExtensionIsPatchIncluded(HideCommunityHighlightsPatch::class.java)
}

val RedditHidePeopleAlsoSearchFor = patch(
    name = "Hide People also search for section",
    description = "Hides the People also search for section from search results."
) {
    dependsOn(SettingsHook)
    setExtensionIsPatchIncluded(HidePeopleAlsoSearchForSectionPatch::class.java)
    if (!Settings.HIDE_PEOPLE_ALSO_SEARCH_FOR_SECTION.get()) return@patch
    val mapper = SearchRowMapperFingerprint.method
    val comparison = ::querySuggestionComparison.method
    mapper.hookMethod(scopedHook(comparison) {
        after {
            if (!it.hasThrowable() && it.result == true && it.args.any { arg -> arg == "SearchQuerySuggestion" }) {
                it.result = HidePeopleAlsoSearchForSectionPatch.isQuerySuggestion(true)
            }
        }
    })
    setExtensionIsPatchIncluded(HidePeopleAlsoSearchForSectionPatch::class.java)
}

val RedditHideTrendingShelves = patch(
    name = "Hide Trending shelves",
    description = "Hides Trending shelves from feeds and search suggestions."
) {
    dependsOn(SettingsHook)
    val languageManager = LocaleLanguageManagerInitFingerprint.method
    val contentLanguages = LocaleLanguageManagerContentLanguagesFingerprint.method.apply { isAccessible = true }
    val header = SearchSectionHeaderFingerprint.method
    val headerState = header.parameterTypes.first()
    val labelField = if (headerState == String::class.java) null
    else headerState.declaredFields.first { it.type == String::class.java }.apply { isAccessible = true }
    val suggestions = TypeaheadSuggestionItemFingerprint.method
    val trendingItems = listOf(
        TrendingItemFingerprint.method,
        TrendingFeedUnitSectionFingerprint.method,
        TrendingFeedUnitDismissedSectionFingerprint.method,
    )

    languageManager.hookMethod {
        after {
            if (!it.hasThrowable()) {
                val locales = (contentLanguages.invoke(it.thisObject) as? List<*>)?.filterIsInstance<Locale>()
                if (locales != null) HideTrendingShelvesPatch.setContentLanguages(locales)
            }
        }
    }
    header.hookMethod {
        before {
            val state = it.args[0]
            val hidden = when {
                state == null || state is String -> HideTrendingShelvesPatch.hideTrendingHeaderLegacy(state as String?)
                else -> HideTrendingShelvesPatch.hideTrendingHeader(object : HideTrendingShelvesPatch.TrendingInterface {
                    override fun patch_getTrendingLabel(): String? = labelField?.get(state) as? String
                    override fun toString(): String = state.toString()
                })
            }
            if (hidden) it.result = null
        }
    }
    suggestions.hookMethod {
        before { if (HideTrendingShelvesPatch.hideTrendingCommunitiesShelf()) it.result = null }
    }
    for (method in trendingItems) {
        method.hookMethod {
            before { if (HideTrendingShelvesPatch.hideTrendingShelf()) it.result = null }
        }
    }
    setExtensionIsPatchIncluded(HideTrendingShelvesPatch::class.java)
}

private fun PatchExecutor.skipWhen(fingerprint: Fingerprint, predicate: () -> Boolean) {
    fingerprint.hookMethod { before { if (predicate()) it.result = null } }
}
