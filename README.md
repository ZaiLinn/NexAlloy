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

| App | Package |
| --- | --- |
| YouTube | `com.google.android.youtube` |
| YouTube Music | `com.google.android.apps.youtube.music` |
| Reddit | `com.reddit.frontpage` |
| Google Photos | `com.google.android.apps.photos` |
| Instagram | `com.instagram.android` |
| Threads | `com.instagram.barcelona` |

## Patches

### YouTube
- Remove ads
- SponsorBlock
- Return YouTube Dislike
- DeArrow (alternative titles and thumbnails)
- Remove background playback restrictions
- AI channel filtering and reporting (AiSList)
- Add to queue
- Swipe controls
- Seekbar
- Remove share links tracking query parameter
- Open system share sheet
- Hide and change navigation buttons
- Remember video quality changes
- Show video quality button
- Show advanced video quality menu
- Copy video url video player button
- Copy video titles and comments
- Open external downloader app
- Custom playback speed
- Remember playback speed
- Playback speed dialog button
- Hide layout components
- Hide feed flyout menu items
- Hide video action buttons
- Disable Shorts resuming on startup
- Disable video codecs
- Disable auto captions
- Bypass image region restrictions
- Change start page
- Channel search
- Channel whitelist
- Disable scrolling speed limit
- Playback in feeds
- Disable auto feed refresh
- Restore original titles
- Open Shorts in regular player

### YouTube Music
- Remove music video ads
- Remove background playback restrictions
- Hide upgrade button
- Hide 'Get Music Premium' label
- Enable exclusive audio playback
- Download songs, albums, and playlists, with external downloader and export options
- Lyrics enhancements (Apple Music-style lyrics, offset adjustment)
- Audio ducking
- Crossfade
- Remember repeat and shuffle state
- Disable swipe to change track in the player and miniplayer
- Disable skipping to the next track when disliking a song
- Pause playback when muted
- Hide notifications
- Layout tweaks (miniplayer, navigation buttons, orientation)

### Reddit
- Hide ads
- Sanitize sharing links
- Start as guest

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
