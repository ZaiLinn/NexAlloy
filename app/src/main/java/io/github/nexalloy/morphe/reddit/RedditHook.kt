package io.github.nexalloy.morphe.reddit

import io.github.nexalloy.ExtensionResourceHook
import io.github.nexalloy.morphe.reddit.ad.HideAds
import io.github.nexalloy.morphe.reddit.font.CustomFont
import io.github.nexalloy.morphe.reddit.font.ForceSystemFont
import io.github.nexalloy.morphe.reddit.interaction.avatars.HighQualityAvatars
import io.github.nexalloy.morphe.reddit.interaction.comments.RememberCollapsedComments
import io.github.nexalloy.morphe.reddit.interaction.dialogs.RemoveSubredditDialog
import io.github.nexalloy.morphe.reddit.interaction.media.HideMediaViewerComponents
import io.github.nexalloy.morphe.reddit.interaction.media.KeepMediaViewerInSubreddit
import io.github.nexalloy.morphe.reddit.interaction.media.MediaViewerBlackBackground
import io.github.nexalloy.morphe.reddit.interaction.media.MediaViewerFade
import io.github.nexalloy.morphe.reddit.interaction.media.SwipeUpForComments
import io.github.nexalloy.morphe.reddit.interaction.profiles.SearchHiddenProfiles
import io.github.nexalloy.morphe.reddit.interaction.redgifs.RedgifsAudio
import io.github.nexalloy.morphe.reddit.interaction.reply.FullScreenReply
import io.github.nexalloy.morphe.reddit.interaction.screenshots.DisableScreenshotPopup
import io.github.nexalloy.morphe.reddit.layout.copytext.RedditCopyText
import io.github.nexalloy.morphe.reddit.layout.feed.autoscroll.feedAutoScroll
import io.github.nexalloy.morphe.reddit.layout.feed.disableBottomBarAutoHide
import io.github.nexalloy.morphe.reddit.layout.feed.disableHomeFeedAutoRefresh
import io.github.nexalloy.morphe.reddit.layout.feed.disablePostDetailSwipe
import io.github.nexalloy.morphe.reddit.layout.feed.disableRefreshOnBack
import io.github.nexalloy.morphe.reddit.layout.feed.flairFilters
import io.github.nexalloy.morphe.reddit.layout.feed.keepFeedPosition
import io.github.nexalloy.morphe.reddit.layout.feed.keywordFilters
import io.github.nexalloy.morphe.reddit.layout.feed.media.feedMediaMaxHeight
import io.github.nexalloy.morphe.reddit.layout.feed.media.fullWidthFeedMedia
import io.github.nexalloy.morphe.reddit.layout.hide.RedditAutoModeratorComments
import io.github.nexalloy.morphe.reddit.layout.hide.RedditColorizeCommentIndentLines
import io.github.nexalloy.morphe.reddit.layout.hide.RedditHideAnswersPreview
import io.github.nexalloy.morphe.reddit.layout.hide.RedditHideAskButton
import io.github.nexalloy.morphe.reddit.layout.hide.RedditHideCommentFlairsAndBadges
import io.github.nexalloy.morphe.reddit.layout.hide.RedditHideCommunitiesShelf
import io.github.nexalloy.morphe.reddit.layout.hide.RedditHideCommunityHighlights
import io.github.nexalloy.morphe.reddit.layout.hide.RedditHideCommunityMenuBadge
import io.github.nexalloy.morphe.reddit.layout.hide.RedditHideInteractivePosts
import io.github.nexalloy.morphe.reddit.layout.hide.RedditHideNavigationButtons
import io.github.nexalloy.morphe.reddit.layout.hide.RedditHidePeopleAlsoSearchFor
import io.github.nexalloy.morphe.reddit.layout.hide.RedditHidePostButtons
import io.github.nexalloy.morphe.reddit.layout.search.RedditHideRedditSearch
import io.github.nexalloy.morphe.reddit.layout.hide.RedditHideRelatedPostsSection
import io.github.nexalloy.morphe.reddit.layout.hide.RedditHideSidebarComponents
import io.github.nexalloy.morphe.reddit.layout.hide.RedditHideTrendingShelves
import io.github.nexalloy.morphe.reddit.layout.hide.RedditSettingsInSidebar
import io.github.nexalloy.morphe.reddit.layout.hide.RedditShowFlairsInHomeFeed
import io.github.nexalloy.morphe.reddit.layout.post.RedditCustomizePostLayout
import io.github.nexalloy.morphe.reddit.layout.sidepanel.RedditPostSidePanel
import io.github.nexalloy.morphe.reddit.layout.upsell.RedditHidePremiumUpsells
import io.github.nexalloy.morphe.reddit.misc.debugging.EnableRedditDebugging
import io.github.nexalloy.morphe.reddit.misc.downloadlocation.RedditDownloadLocation
import io.github.nexalloy.morphe.reddit.misc.guest.startAsGuestPatch
import io.github.nexalloy.morphe.reddit.misc.appicon.RedditAppIcon
import io.github.nexalloy.morphe.reddit.misc.openlink.OpenLinksDirectly
import io.github.nexalloy.morphe.reddit.misc.openlink.OpenLinksExternally
import io.github.nexalloy.morphe.reddit.misc.privacy.SanitizeSharingLinks
import io.github.nexalloy.morphe.reddit.misc.settings.SettingsHook
import io.github.nexalloy.morphe.reddit.misc.sharecontent.RedditSharePostContent
import io.github.nexalloy.morphe.reddit.misc.sharesheet.OpenRedditSystemShareSheet

internal const val REDDIT_PACKAGE = "com.reddit.frontpage"
private const val REDDIT_PORT_VERSION = "2026.39.1"

private val existingRedditPatches = arrayOf(
    // Adds the module assets to the host resources, for the extension settings UI.
    ExtensionResourceHook,
    SettingsHook,
    HideAds,
    SanitizeSharingLinks,
    startAsGuestPatch,
)

private val redditVersionPatches = arrayOf(
    disableHomeFeedAutoRefresh,
    disableRefreshOnBack,
    keepFeedPosition,
    disablePostDetailSwipe,
    disableBottomBarAutoHide,
    keywordFilters,
    flairFilters,
    feedAutoScroll,
    feedMediaMaxHeight,
    fullWidthFeedMedia,
    RedditHideNavigationButtons,
    RedditHideSidebarComponents,
    RedditHidePostButtons,
    RedditHideCommentFlairsAndBadges,
    RedditHideCommunityHighlights,
    RedditHideCommunityMenuBadge,
    RedditHideCommunitiesShelf,
    RedditHideTrendingShelves,
    RedditHideInteractivePosts,
    RedditShowFlairsInHomeFeed,
    RedditHideRelatedPostsSection,
    RedditHideAnswersPreview,
    RedditHideAskButton,
    RedditHidePeopleAlsoSearchFor,
    RedditHideRedditSearch,
    RedditAutoModeratorComments,
    RedditColorizeCommentIndentLines,
    RedditSettingsInSidebar,
    RememberCollapsedComments,
    FullScreenReply,
    SearchHiddenProfiles,
    SwipeUpForComments,
    KeepMediaViewerInSubreddit,
    HideMediaViewerComponents,
    MediaViewerBlackBackground,
    MediaViewerFade,
    RedgifsAudio,
    HighQualityAvatars,
    RemoveSubredditDialog,
    DisableScreenshotPopup,
    RedditDownloadLocation,
    RedditSharePostContent,
    RedditCopyText,
    RedditCustomizePostLayout,
    OpenLinksDirectly,
    OpenLinksExternally,
    OpenRedditSystemShareSheet,
    RedditAppIcon,
    CustomFont,
    ForceSystemFont,
    RedditPostSidePanel,
    EnableRedditDebugging,
    RedditHidePremiumUpsells,
)

val RedditPatches = existingRedditPatches + redditVersionPatches

internal fun redditPatchesForVersion(versionName: String?) =
    if (versionName == REDDIT_PORT_VERSION) RedditPatches else existingRedditPatches
