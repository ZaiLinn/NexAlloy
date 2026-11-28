<div align="center">
  <h1>NexAlloy</h1>
  <img alt="License" src="https://img.shields.io/badge/License-GPL--3.0-blue.svg">
  <a href="https://github.com/LSPosed/LSPosed"><img alt="LSPosed" src="https://img.shields.io/badge/Requires-LSPosed-8700ff.svg"></a>
  <a href="https://github.com/ZaiLinn/NexAlloy"><img alt="GitHub Stars" src="https://img.shields.io/github/stars/ZaiLinn/NexAlloy"></a>
  <br>
</div>

**ZaiLinn's personal fork of [NexAlloy](https://github.com/NexAlloy/NexAlloy) — an LSPosed runtime module, powered by Morphe, ReVanced, and beyond.**

> [!NOTE]
> **Source-only fork.** This repository publishes **source code only** — no prebuilt APKs or releases are provided here. Build it yourself (see [Building](#building)).

>[!IMPORTANT]  
> - This is **NOT an official Morphe or ReVanced project**, do not ask their developers for help.  
> - **Root access** with an **LSPosed-compatible framework** is strictly **required** to use this module!  
> - This is an Xposed-style **runtime hook module**, not a patcher/build-time patch framework.  

## Supported apps

| App | Package | Tested version |
| --- | --- | --- |
| YouTube | `com.google.android.youtube` | 21.45.82 |
| YouTube Music | `com.google.android.apps.youtube.music` | 9.46.51 |
| Reddit | `com.reddit.frontpage` | 2026.39.1 |
| Google Photos | `com.google.android.apps.photos` | — |
| Instagram | `com.instagram.android` | — |
| Threads | `com.instagram.barcelona` | — |

> [!TIP]
> Patches and fingerprint tests are verified against the versions above. Other versions generally work, but Reddit's full feature set is only enabled on `2026.39.1` (older/newer Reddit versions fall back to core patches).

## Patches

### YouTube

**Ads & privacy**
- Remove ads (with AI channel filtering and reporting, AiSList)
- SponsorBlock
- Return YouTube Dislike
- DeArrow (alternative titles and thumbnails)
- Remove background playback restrictions
- Sanitize sharing links (remove tracking query parameters)
- Bypass link redirects
- Open links externally
- Open system share sheet
- Disable QUIC protocol
- Spoof device dimensions

**Playback & video**
- Remember video quality changes; show video quality button; show advanced video quality menu; hide Premium video quality label
- Custom playback speed; remember playback speed; playback speed dialog button; replay slower on double-tap
- Force original audio track
- Disable video codecs
- Disable DRC audio
- Skip silence
- Playback buffer settings
- Remember live stream position
- Autoplay control
- Disable playlist autoplay / reverse playlist autoplay

**Player & interaction**
- Add to queue
- Swipe controls
- Seekbar with custom thumb
- Copy video url player button
- Copy video titles and comments
- Open external downloader app
- Download post images, translate posts
- Reload video button
- Disable haptic feedback
- Disable chapter skip on double-tap
- Disable "Continue watching" prompt
- Remove viewer discretion dialog
- Skip live Shorts
- Fullscreen video scale
- Custom player overlay opacity
- Glass-style menus, snackbar tweaks
- App refresh rate

**Captions**
- Disable auto captions
- Preferred caption language
- Auto-translate captions, translated caption pens
- Captions in PiP
- Copy transcript / show transcript
- Stable caption blocks, captions after seek

**Layout & hiding**
- Hide layout components
- Hide video action buttons
- Hide and change navigation buttons
- Change header
- Wide search bar
- Hide Shorts components; disable Shorts resuming on startup; Shorts autoplay control; open Shorts in regular player
- Hide update screen; disable layout updates
- Hide status bar
- Hide timestamp, end screen cards, end screen suggested video, info cards, autoplay preview, rolling number animation
- Hide player popup panels, player overlay buttons, player flyout menu items, related video overlay, ambient mode
- Hide settings menu items, sign-in to TV popup
- Hide related videos
- Live chat translate

**Feed & navigation**
- Feed card layout
- Playback in feeds
- Disable auto feed refresh
- Disable scrolling speed limit
- Restore original titles
- Change start page
- Channel search
- Channel whitelist
- Bypass image region restrictions

**Fixes**
- Back-to-exit gesture, like button, media session stop, delayed ping crash, PiP chat bar, playback speed while playing

### YouTube Music

**Ads & privacy**
- Remove music video ads
- Remove background playback restrictions
- Hide upgrade button, 'Get Music Premium' label
- Sanitize sharing links
- Open system share sheet
- Disable QUIC protocol

**Playback & audio**
- Enable exclusive audio playback
- Skip silence
- Disable DRC audio
- Force original audio
- Crossfade between tracks
- Disable audio ducking
- Pause playback when muted
- Loop playback
- Remember repeat and shuffle state
- Disable swipe to change track in the player and miniplayer
- Disable skipping to the next track when disliking a song
- Queue like filter
- Spoof video streams
- Change start page
- Disable auto captions

**Downloads & features**
- Download songs, albums, and playlists, with external downloader and export options
- Play album songs
- Audio/video switch

**Integrations**
- SponsorBlock
- Return YouTube Dislike

**Lyrics**
- Lyrics enhancements (Apple Music-style lyrics, offset adjustment)

**Layout & hiding**
- Apple Music-style artwork
- Miniplayer: custom color, forced miniplayer, swipe to dismiss, previous/next buttons
- Hide layout components
- Hide music action buttons
- Hide flyout menu components
- Hide settings menu items
- Hide notifications
- Hide and change navigation buttons
- Force portrait orientation

### Reddit

**Core (all supported versions)**
- Hide ads
- Sanitize sharing links
- Start as guest

**Feed** (full feature set)
- Disable home feed auto refresh
- Disable refresh on back
- Keep feed position
- Disable post detail swipe
- Disable bottom bar auto hide
- Keyword filters, flair filters
- Feed auto scroll
- Feed media max height, full-width feed media
- Show flairs in home feed

**Hiding** (full feature set)
- Hide navigation buttons, sidebar components, post buttons
- Hide comment flairs and badges, AutoModerator comments
- Colorize comment indent lines
- Hide community highlights, community menu badge, communities shelf, trending shelves
- Hide interactive posts, related posts section
- Hide answers preview, ask button, "People also search for", Reddit search
- Settings in sidebar
- Hide premium upsells

**Interaction** (full feature set)
- Remember collapsed comments
- Full-screen reply
- Search hidden profiles
- Swipe up for comments
- Media viewer: keep in subreddit, hide components, black background, fade
- Redgifs audio
- High-quality avatars
- Remove subreddit dialog
- Disable screenshot popup

**Misc** (full feature set)
- Download location
- Share post content, copy text
- Customize post layout
- Open links directly / externally, system share sheet
- App icon, custom font, force system font
- Post side panel
- Debugging

### Google Photos
- Spoof Pixel XL (unlock device-specific features)
- Enable DCIM folders backup control

### Instagram
- Hide ads

### Threads
- Hide ads
- Simplified Chinese content filter

## Building

Requirements: Android SDK, JDK 17+.

```bash
./gradlew assembleRelease
```

- Output: `app/build/outputs/apk/release/app-release.apk`
- Release signing is configured via a `signing.properties` file at the repo root (see `app/build.gradle.kts` for the expected keys). Without it, configure your own signing config.
- The LSPosed module ID is `io.github.zailinn.nexalloy`; enable it for the apps listed above and reboot.

### Fingerprint tests

The module locates hooks with [DexKit](https://luckypray.org/DexKit/en/) fingerprints, which are verified against real APKs placed in `app/binaries/` (not included in this repository):

```bash
./gradlew :app:testDebugUnitTest
```

## Development

See [DEVELOPMENT.md](DEVELOPMENT.md) for the project structure and patch conventions.

## ⭐ Credits

[NexAlloy](https://github.com/NexAlloy/NexAlloy): the upstream project this fork continues (itself evolved from ReVancedXposed).  
[DexKit](https://luckypray.org/DexKit/en/): a high-performance dex runtime parsing library.  
[Morphe](https://morphe.software): Transform Your Android Apps  
[ReVanced](https://revanced.app): Continuing the legacy of Vanced at [revanced.app](https://revanced.app)

## License

GPL-3.0 — see [LICENSE](LICENSE).
