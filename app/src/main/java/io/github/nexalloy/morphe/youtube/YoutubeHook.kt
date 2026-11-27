package io.github.nexalloy.morphe.youtube

import android.app.Activity
import app.morphe.extension.shared.Utils
import io.github.libxposed.api.XposedInterface
import io.github.nexalloy.ExtensionResourceHook
import io.github.nexalloy.addModuleAssets
import io.github.nexalloy.atLast
import io.github.nexalloy.hookExtensionClassLoading
import io.github.nexalloy.hookMethod
import io.github.nexalloy.injectHostClassLoaderToSelf
import io.github.nexalloy.morphe.shared.misc.CheckRecycleBitmapMediaSession
import io.github.nexalloy.morphe.shared.misc.debugging.experimentalBooleanFeatureFlagFingerprint
import io.github.nexalloy.morphe.shared.misc.debugging.experimentalIndexedBooleanFeatureFlagFingerprint
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.morphe.youtube.misc.playservice.is_21_42_or_greater
import io.github.nexalloy.morphe.youtube.ad.HideAds
import io.github.nexalloy.morphe.youtube.interaction.channelsearch.channelSearchPatch
import io.github.nexalloy.morphe.youtube.interaction.copytext.copyTextPatch
import io.github.nexalloy.morphe.youtube.interaction.copyvideolink.CopyVideoLinkButtonPatch
import io.github.nexalloy.morphe.youtube.interaction.downloads.Downloads
import io.github.nexalloy.morphe.youtube.interaction.postimages.postImageDownloadPatch
import io.github.nexalloy.morphe.youtube.interaction.posttranslate.postTranslatePatch
import io.github.nexalloy.morphe.youtube.interaction.seekbar.seekbarPatch
import io.github.nexalloy.morphe.youtube.interaction.swipecontrols.SwipeControls
import io.github.nexalloy.morphe.youtube.layout.branding.header.changeHeaderPatch
import io.github.nexalloy.morphe.youtube.layout.buttons.action.HideVideoActionButtons
import io.github.nexalloy.morphe.youtube.layout.buttons.navigation.NavigationBar
import io.github.nexalloy.morphe.youtube.layout.captions.AutoCaptions
import io.github.nexalloy.morphe.youtube.layout.dearrow.deArrowPatch
import io.github.nexalloy.morphe.youtube.layout.feedcards.feedCardLayoutPatch
import io.github.nexalloy.morphe.youtube.layout.feedrefresh.disableAutoFeedRefreshPatch
import io.github.nexalloy.morphe.youtube.layout.flyout.addToQueuePatch
import io.github.nexalloy.morphe.youtube.layout.hide.general.HideLayoutComponents
import io.github.nexalloy.morphe.youtube.layout.hide.shorts.HideShortsComponents
import io.github.nexalloy.morphe.youtube.layout.originaltitles.restoreOriginalTitlesPatch
import io.github.nexalloy.morphe.youtube.layout.playbackinfeeds.playbackInFeedsPatch
import io.github.nexalloy.morphe.youtube.layout.returnyoutubedislike.returnYouTubeDislikePatch
import io.github.nexalloy.morphe.youtube.layout.scrolling.disableScrollSpeedLimitPatch
import io.github.nexalloy.morphe.youtube.layout.sharesheet.openSystemShareSheetPatch
import io.github.nexalloy.morphe.youtube.layout.shortsnoresume.DisableShortsResumingOnStartup
import io.github.nexalloy.morphe.youtube.layout.shortsplayer.openShortsInRegularPlayerPatch
import io.github.nexalloy.morphe.youtube.layout.shortsautoplay.shortsAutoplayPatch
import io.github.nexalloy.morphe.youtube.layout.sponsorblock.SponsorBlock
import io.github.nexalloy.morphe.youtube.layout.startpage.changeStartPagePatch
import io.github.nexalloy.morphe.youtube.layout.thumbnails.BypassImageRegionRestrictionsPatch
import io.github.nexalloy.morphe.youtube.misc.backgroundplayback.BackgroundPlayback
import io.github.nexalloy.morphe.youtube.misc.debugging.EnableDebugging
import io.github.nexalloy.morphe.youtube.misc.privacy.SanitizeSharingLinks
import io.github.nexalloy.morphe.youtube.misc.settings.SettingsHook
import io.github.nexalloy.morphe.youtube.misc.whitelist.channelWhitelistPatch
import io.github.nexalloy.morphe.youtube.misc.fix.backtoexitgesture.fixBackToExitGesturePatch
import io.github.nexalloy.morphe.youtube.misc.fix.likebutton.fixLikeButtonPatch
import io.github.nexalloy.morphe.youtube.misc.fix.mediasessionstop.fixMediaSessionStopPatch
import io.github.nexalloy.morphe.youtube.misc.fix.delayedping.fixDelayedPingCrashPatch
import io.github.nexalloy.morphe.youtube.misc.fix.pipchatbar.fixPipChatBarPatch
import io.github.nexalloy.morphe.youtube.misc.fix.playbackspeedwhileplaying.fixPlaybackSpeedWhilePlayingPatch
import io.github.nexalloy.morphe.youtube.layout.updatescreen.hideUpdateScreenPatch
import io.github.nexalloy.morphe.youtube.misc.bypasslinkredirects.BypassLinkRedirects
import io.github.nexalloy.morphe.youtube.misc.openlinksexternally.OpenLinksExternally
import io.github.nexalloy.morphe.youtube.misc.quic.DisableQUICProtocol
import io.github.nexalloy.morphe.youtube.misc.spoof.devicedimensions.SpoofDeviceDimensions
import io.github.nexalloy.morphe.youtube.interaction.playlistautoplay.disable.DisablePlaylistAutoplay
import io.github.nexalloy.morphe.youtube.interaction.autoplaycontrol.AutoplayControl
import io.github.nexalloy.morphe.youtube.interaction.playlistautoplay.reverse.ReversePlaylistAutoplay
import io.github.nexalloy.morphe.youtube.video.quality.hidepremium.HidePremiumVideoQuality
import io.github.nexalloy.morphe.youtube.video.speed.replayslower.ReplaySlower
import io.github.nexalloy.morphe.youtube.video.drc.DisableDRCAudio
import io.github.nexalloy.morphe.youtube.video.skipsilence.SkipSilence
import io.github.nexalloy.morphe.youtube.interaction.viewerdiscretion.RemoveViewerDiscretionDialog
import io.github.nexalloy.morphe.youtube.interaction.haptic.DisableHapticFeedback
import io.github.nexalloy.morphe.youtube.interaction.chapterskip.DisableChapterSkipDoubleTap
import io.github.nexalloy.morphe.youtube.interaction.continuewatching.DisableContinueWatchingPrompt
import io.github.nexalloy.morphe.youtube.interaction.reloadbutton.ReloadVideoButtonPatch
import io.github.nexalloy.morphe.youtube.interaction.shorts.skipLiveShortsPatch
import io.github.nexalloy.morphe.youtube.video.buffer.playbackBufferPatch
import io.github.nexalloy.morphe.youtube.misc.refreshrate.appRefreshRatePatch
import io.github.nexalloy.morphe.youtube.layout.widesearchbar.wideSearchBarPatch
import io.github.nexalloy.morphe.youtube.layout.glassmenus.glassMenusPatch
import io.github.nexalloy.morphe.youtube.layout.player.fullscreen.fullscreenVideoScalePatch
import io.github.nexalloy.morphe.youtube.layout.player.overlay.customPlayerOverlayOpacityPatch
import io.github.nexalloy.morphe.youtube.layout.snackbar.snackbarPatch
import io.github.nexalloy.morphe.youtube.layout.statusbar.hideStatusBarPatch
import io.github.nexalloy.morphe.youtube.interaction.seekbar.customSeekbarThumbPatch
import io.github.nexalloy.morphe.youtube.video.rememberlivestreamposition.RememberLiveStreamPosition
import io.github.nexalloy.morphe.youtube.layout.hide.time.HideTimestamp
import io.github.nexalloy.morphe.youtube.layout.hide.endscreencards.HideEndScreenCards
import io.github.nexalloy.morphe.youtube.layout.hide.infocards.HideInfoCards
import io.github.nexalloy.morphe.youtube.layout.hide.relatedvideos.HideRelatedVideos
import io.github.nexalloy.morphe.youtube.layout.hide.autoplaypreview.HideAutoplayPreview
import io.github.nexalloy.morphe.youtube.layout.hide.rollingnumber.DisableRollingNumber
import io.github.nexalloy.morphe.youtube.layout.hide.playerpopup.DisablePlayerPopupPanels
import io.github.nexalloy.morphe.youtube.layout.hide.playeroverlaybuttons.HidePlayerOverlayButtons
import io.github.nexalloy.morphe.youtube.layout.captions.core.Captions
import io.github.nexalloy.morphe.youtube.layout.captions.core.CaptionsAfterSeek
import io.github.nexalloy.morphe.youtube.layout.captions.core.CaptionTrackCapture
import io.github.nexalloy.morphe.youtube.layout.captions.core.CaptionViewport
import io.github.nexalloy.morphe.youtube.layout.captions.core.CaptionWindowMeasure
import io.github.nexalloy.morphe.youtube.layout.captions.core.StableCaptionBlocks
import io.github.nexalloy.morphe.youtube.layout.captions.language.AutoTranslateCaptions
import io.github.nexalloy.morphe.youtube.layout.captions.language.CaptionCookies
import io.github.nexalloy.morphe.youtube.layout.captions.language.CaptionsInPip
import io.github.nexalloy.morphe.youtube.layout.captions.language.CopyTranscript
import io.github.nexalloy.morphe.youtube.layout.captions.language.PreferredCaptionLanguage
import io.github.nexalloy.morphe.youtube.layout.captions.language.Transcript
import io.github.nexalloy.morphe.youtube.layout.captions.language.TranslatedCaptionPens
import io.github.nexalloy.morphe.youtube.layout.hide.ambientmode.AmbientMode
import io.github.nexalloy.morphe.youtube.layout.hide.endscreensuggested.HideEndScreenSuggestedVideo
import io.github.nexalloy.morphe.youtube.layout.hide.playerflyoutmenu.HidePlayerFlyoutMenuComponents
import io.github.nexalloy.morphe.youtube.layout.hide.relatedvideooverlay.HideRelatedVideoOverlay
import io.github.nexalloy.morphe.youtube.layout.hide.settingsmenu.HideSettingsMenuFilter
import io.github.nexalloy.morphe.youtube.layout.hide.signintotvpopup.DisableSignInToTVPopup
import io.github.nexalloy.morphe.youtube.layout.livechat.LiveChatTranslate
import io.github.nexalloy.morphe.youtube.layout.disableupdates.DisableLayoutUpdates
import io.github.nexalloy.morphe.youtube.shared.YOUTUBE_MAIN_ACTIVITY_CLASS_TYPE
import io.github.nexalloy.morphe.youtube.video.audio.ForceOriginalAudio
import io.github.nexalloy.morphe.youtube.video.codecs.DisableVideoCodecs
import io.github.nexalloy.morphe.youtube.video.quality.VideoQuality
import io.github.nexalloy.morphe.youtube.video.speed.PlaybackSpeed
import io.github.nexalloy.patch
import org.luckypray.dexkit.wrap.DexMethod

val ExtensionHook = patch(name = "<ExtensionHook>") {
    injectHostClassLoaderToSelf(this::class.java.classLoader!!, classLoader)
    hookExtensionClassLoading(this::class.java.classLoader!!)
    DexMethod("$YOUTUBE_MAIN_ACTIVITY_CLASS_TYPE->onCreate(Landroid/os/Bundle;)V").hookMethod {
        before {
            val mainActivity = it.thisObject as Activity
            mainActivity.addModuleAssets()
            Utils.setContext(mainActivity)
        }
    }

    ExtensionResourceHook.run(this)
}

private val overrides = mutableMapOf<Long, (Boolean) -> Boolean>()

fun insertLiteralOverride(id: Long, override: Boolean = false) {
    overrides[id] = { override }
}

fun insertLiteralOverride(id: Long, override: (Boolean) -> Boolean) {
    overrides[id] = override
}

val FeatureOverride = patch {
    dependsOn(VersionCheck)

    val methods = mutableListOf(::experimentalBooleanFeatureFlagFingerprint.method)
    if (is_21_42_or_greater) {
        methods += ::experimentalIndexedBooleanFeatureFlagFingerprint.method
    }
    methods.forEach { method ->
        method.hookMethod {
            priority = XposedInterface.PRIORITY_DEFAULT - 1
            after {
                val id = it.args.atLast(2) as Long
                val orig = it.result as Boolean
                val match = overrides[id] ?: return@after
                it.result = match(orig)
            }
        }
    }
}

val YouTubePatches = arrayOf(
    ExtensionHook,
    BackgroundPlayback,
    feedCardLayoutPatch,
    playbackInFeedsPatch,
    disableAutoFeedRefreshPatch,
    restoreOriginalTitlesPatch,
    openShortsInRegularPlayerPatch,
    disableScrollSpeedLimitPatch,
    changeHeaderPatch,
    SanitizeSharingLinks,
    HideAds,
    SponsorBlock,
    copyTextPatch,
    returnYouTubeDislikePatch,
    CopyVideoLinkButtonPatch,
    Downloads,
    postImageDownloadPatch,
    postTranslatePatch,
    HideShortsComponents,
    DisableShortsResumingOnStartup,
    NavigationBar,
    SwipeControls,
    VideoQuality,
    HideLayoutComponents,
    HideVideoActionButtons,
    PlaybackSpeed,
    AutoCaptions,
    EnableDebugging,
    ForceOriginalAudio,
    DisableVideoCodecs,
    deArrowPatch,
    openSystemShareSheetPatch,
    seekbarPatch,
    addToQueuePatch,
    BypassImageRegionRestrictionsPatch,
    CheckRecycleBitmapMediaSession,
    changeStartPagePatch,
    channelSearchPatch,
    channelWhitelistPatch,
    // Fixes and interactions ported from the upstream patch bundle.
    fixBackToExitGesturePatch,
    fixLikeButtonPatch,
    fixMediaSessionStopPatch,
    fixDelayedPingCrashPatch,
    fixPipChatBarPatch,
    fixPlaybackSpeedWhilePlayingPatch,
    hideUpdateScreenPatch,
    BypassLinkRedirects,
    OpenLinksExternally,
    DisableQUICProtocol,
    SpoofDeviceDimensions,
    DisablePlaylistAutoplay,
    AutoplayControl,
    ReversePlaylistAutoplay,
    HidePremiumVideoQuality,
    ReplaySlower,
    DisableDRCAudio,
    SkipSilence,
    RemoveViewerDiscretionDialog,
    DisableHapticFeedback,
    DisableChapterSkipDoubleTap,
    DisableContinueWatchingPrompt,
    ReloadVideoButtonPatch,
    RememberLiveStreamPosition,
    HideTimestamp,
    HideEndScreenCards,
    HideInfoCards,
    HideRelatedVideos,
    HideAutoplayPreview,
    DisableRollingNumber,
    DisablePlayerPopupPanels,
    HidePlayerOverlayButtons,
    // Caption suite (order matters: features before the Captions settings aggregator).
    CaptionsAfterSeek,
    CaptionWindowMeasure,
    CaptionViewport,
    CaptionTrackCapture,
    StableCaptionBlocks,
    PreferredCaptionLanguage,
    AutoTranslateCaptions,
    TranslatedCaptionPens,
    CaptionCookies,
    CaptionsInPip,
    CopyTranscript,
    Transcript,
    Captions,
    // Layout hides and other ported patches.
    DisableSignInToTVPopup,
    HideRelatedVideoOverlay,
    HideEndScreenSuggestedVideo,
    HideSettingsMenuFilter,
    AmbientMode,
    HidePlayerFlyoutMenuComponents,
    LiveChatTranslate,
    DisableLayoutUpdates,
    // Wave 4: interaction and performance patches.
    appRefreshRatePatch,
    playbackBufferPatch,
    wideSearchBarPatch,
    shortsAutoplayPatch,
    skipLiveShortsPatch,
    // Wave 4 cosmetic patches.
    hideStatusBarPatch,
    customPlayerOverlayOpacityPatch,
    customSeekbarThumbPatch,
    glassMenusPatch,
    snackbarPatch,
    fullscreenVideoScalePatch,
    // make sure settingsHook at end to build preferences
    SettingsHook,
    FeatureOverride
)
