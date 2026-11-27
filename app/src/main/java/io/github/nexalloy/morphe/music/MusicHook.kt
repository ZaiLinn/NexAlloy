package io.github.nexalloy.morphe.music

import io.github.nexalloy.ExtensionResourceHook
import io.github.nexalloy.morphe.music.ad.hideAdsPatch
import io.github.nexalloy.morphe.music.interaction.dislikeredirection.disableDislikeRedirectionPatch
import io.github.nexalloy.morphe.music.interaction.downloads.Downloads
import io.github.nexalloy.morphe.music.interaction.loop.loopVideoPatch
import io.github.nexalloy.morphe.music.interaction.pausewhenmuted.pauseWhenMutedPatch
import io.github.nexalloy.morphe.music.interaction.remember.repeatstate.rememberRepeatStatePatch
import io.github.nexalloy.morphe.music.interaction.remember.shufflestate.rememberShuffleStatePatch
import io.github.nexalloy.morphe.music.interaction.trackswipe.disableSwipeToChangeTrackPatch
import io.github.nexalloy.morphe.music.misc.audio.EnableExclusiveAudioPlayback
import io.github.nexalloy.morphe.music.layout.buttons.navigation.navigationBarPatch
import io.github.nexalloy.morphe.music.misc.backgroundplayback.BackgroundPlayback
import io.github.nexalloy.morphe.music.misc.debugging.EnableDebugging
import io.github.nexalloy.morphe.music.misc.privacy.SanitizeSharingLinks
import io.github.nexalloy.morphe.music.misc.settings.SettingsHook
import io.github.nexalloy.morphe.music.video.information.musicVideoInformationPatch
import io.github.nexalloy.morphe.music.video.playerresponse.PlayerResponseMethodHook
import io.github.nexalloy.morphe.music.misc.mediasession.mediaSessionHookPatch
import io.github.nexalloy.morphe.shared.misc.CheckRecycleBitmapMediaSession
import io.github.nexalloy.morphe.music.video.skipsilence.SkipSilence
import io.github.nexalloy.morphe.music.video.drc.DisableDRCAudio
import io.github.nexalloy.morphe.music.video.audio.ForceOriginalAudio
import io.github.nexalloy.morphe.music.layout.startpage.changeStartPagePatch
import io.github.nexalloy.morphe.music.misc.quic.DisableQUICProtocol
import io.github.nexalloy.morphe.music.misc.sharesheet.openSystemShareSheetPatch
import io.github.nexalloy.morphe.music.layout.captions.AutoCaptions
import io.github.nexalloy.morphe.music.interaction.crossfade.Crossfade
import io.github.nexalloy.morphe.music.misc.album.playAlbumSongsPatch
import io.github.nexalloy.morphe.music.misc.audiovideoswitch.enableAudioVideoSwitchPatch
import io.github.nexalloy.morphe.music.layout.returnyoutubedislike.returnYouTubeDislikePatch
import io.github.nexalloy.morphe.music.layout.sponsorblock.musicSponsorBlockPatch
import io.github.nexalloy.morphe.music.interaction.likefilter.queueLikeFilterPatch

val YTMusicPatches = arrayOf(
    ExtensionResourceHook,
    BackgroundPlayback,
    navigationBarPatch,
    hideAdsPatch,
    EnableExclusiveAudioPlayback,
    CheckRecycleBitmapMediaSession,
    EnableDebugging,
    SanitizeSharingLinks,
    Downloads,
    loopVideoPatch,
    rememberRepeatStatePatch,
    rememberShuffleStatePatch,
    disableSwipeToChangeTrackPatch,
    disableDislikeRedirectionPatch,
    pauseWhenMutedPatch,
    // Infrastructure for the Wave 2 patches (SponsorBlock, ReturnYouTubeDislike, …).
    musicVideoInformationPatch,
    PlayerResponseMethodHook,
    mediaSessionHookPatch,
    // Wave 2 feature patches. All must add their preferences before SettingsHook closes the screens.
    SkipSilence,
    DisableDRCAudio,
    ForceOriginalAudio,
    changeStartPagePatch,
    DisableQUICProtocol,
    openSystemShareSheetPatch,
    AutoCaptions,
    Crossfade,
    playAlbumSongsPatch,
    enableAudioVideoSwitchPatch,
    returnYouTubeDislikePatch,
    musicSponsorBlockPatch,
    queueLikeFilterPatch,
    // All patches must add their preferences before SettingsHook closes the screens.
    SettingsHook
)
