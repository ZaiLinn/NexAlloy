# NexAlloy 未完成任务交接清单（给 Codex）

> 生成时间：2026-11-27。本清单只列**尚未完成**的工作。已完成且验证绿的部分不在本表（见文末"状态基线"）。

## 本轮更新（2026-11-27，优先于下文历史清单）

- YT Music 已将 Downloads、Remember repeat state、Remember shuffle state、Disable swipe to change track、Disable dislike redirection、Pause when muted 注册在 SettingsHook 之前，设置入口已在真机 9.46.51 验证可打开，用户反馈可用。新功能的每个行为尚未逐项做完整端到端测试。
- 修正 Music VideoInformation 的时长更新顺序：在通知换歌消费者前更新新歌曲时长。
- 最新全量指纹报告：Music 180 matched / 60 skipped / 0 failed；YouTube 754 / 59 / 0；Reddit 327 / 2 / 0。下文任务 B 的失败记录已过时；video ID/length 的抽象接口仅用于反射调用，使用 AllowAbstract，不是 Hook 抽象方法。
- 用户明确排除：Android Auto、Sonos Cast、Jam queue sharing、Spoof app version、A-B loop。已有 loop 文件保留，但不注册、不展示设置。不要按下文旧 backlog 把这些接入。
- 用户要求给 YT Music 的 Morphe 设置及子页面增加进出动画，范围不含原生主界面。
- YouTube 启动闪退已修：RemoveViewerDiscretionDialog 不再 Hook java.lang.Boolean.booleanValue，而是 Hook 宿主的 Boolean 生产方法。专项回归测试 ViewerDiscretionHookTest 已通过。
- You 页异常：用户确认关闭 NexAlloy 的 Channel whitelist 补丁、强停重开后恢复。根因尚未落实到具体代码；用户已将优先级转到 YT Music，暂不继续修改 YouTube 页面。
- 用户已自行恢复排查时临时关闭的 Hide general ads 和 Disable navigation buttons without labels，后续不要改动。
- Debug 包现在每次冷启动都清除持久化指纹缓存、重新初始化；release 仍复用有效缓存。

---

## 项目关键背景（Codex 必读）

- 仓库：`/Users/carlos/Documents/Code/NexAlloy`。LSPosed 运行时模块（Xposed 式运行时 hook，**不是** patcher/build-time 补丁框架）。
- 上游补丁语义参考：`morphe-patches/patches/src/main/kotlin/app/morphe/patches/{music,youtube,reddit,shared}/`（patcher DSL）。
- 扩展代码直接编译进模块：`morphe-patches/extensions/{music,shared,shared-youtube,youtube}/`，import 路径 `app.morphe.extension.*`。
- jadx 反编译 ground truth（已就绪）：
  - YT Music 9.46.51 → `/tmp/ytm-src/sources`（210M）
  - YouTube 21.45.82 → `/tmp/yt-src/sources`
  - Threads 450.0.0.51.78 → `/tmp/threads-src/sources` + `/tmp/threads-c3`
  - Reddit dex 副本 → `/tmp/reddit-apk-check/`
- 真实 APK 在 `app/binaries/`（reddit/youtube/music 三个 .apk）。**注意：.apkm/.apks 不能放进 binaries，会破坏指纹测试**。
- 编译验证：`./gradlew :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain`
- 指纹单测：`./gradlew :app:testDebugUnitTest --console=plain`，报告 `app/build/reports/fingerprints/testDebugUnitTest.json`（结构 `apks[].groups[].results[]{fingerprint,status,matches}`）。
- **缓存规则（重要）**：release 的 dexkit 指纹缓存键 = 宿主 lastUpdateTime + COMMIT_HASH + **VERSION_CODE**（`PatchExecutor.kt`）。debug 按用户要求每次冷启动重新初始化。每次安装新包仍递增 versionCode（见 `app/build.gradle.kts`）。
- **指纹查询纪律（血泪教训）**：任何指纹的 dexkit 基础查询必须用参数类型/访问标志/字符串收紧到个位数候选。Java 侧指令过滤会对**每个候选**拉取并解码指令（flatbuffers），宽查询在运行时会把宿主堆吃光导致进程死亡（2026-11-27 的 YouTube Library 崩溃事故，凶手就是 4307 候选的 DRC 指纹）。
- fail-closed 纪律：hook 定位失败静默跳过保持原行为，try/catch 包裹，绝不 crash 宿主。
- 多匹配设计（`matchAll().first()`）的指纹标 `@SkipTest`；消歧用 `@StrictQuery`；版本门控用 `@RequireAppVersion`；参考 `morphe/youtube/interaction/downloads/Fingerprints.kt` 的惯例。

---

## 任务 A：YouTube Library 崩溃——真机验证（最高优先级）

**背景**：YouTube 21.45.82 上 Library（You tab）页内容全空/崩溃。已定位凶手 = `morphe/youtube/video/drc/Fingerprints.kt` 的 `PlaybackAgnosticUserPreferencesFingerprint` 基础查询太宽（4307 候选），运行时 dexkit 逐候选拉指令把 512MB 堆吃光 → 主线程 GC 死亡螺旋 → HookBridge 爆栈崩溃（tombstone 签名：`invokeExactOriginalMethod` + "stack pointer close to top of stack"）。

**已完成**：指纹已收紧为原生查询（`accessFlags = [PUBLIC, FINAL]`、`returnType = "V"`、`parameters = ["Lapqe;"]`，锚定 `Async/DefaultMediaFetchControllerWrapper.g(apqe)`），静态测试已绿（youtube drc 组 matched 2 / skipped 1）。jadx 验证：`DefaultMediaFetchControllerWrapper.g` 首指令链 `apax.c()Z`（isDrcEnabled）→ `apax.b()`（String）→ `aquz.dq(...)`（工厂）与指纹三个过滤器吻合。

**待办**：
1. 构建（`./gradlew :app:assembleDebug`，versionCode 应为 115，装前确认 `dumpsys package io.github.zailinn.nexalloy | grep versionName` 确实是 2.0.115）。
2. adb 安装 → `am force-stop com.google.android.youtube` → 重新打开 → 点底部 "You" tab。
3. 验证：Library 页正常渲染内容区块（History/Playlists 等），进程 60 秒不死。
4. **若 Library 仍空但不崩**：说明崩溃修好了但 2.0.113 时代的"空页"另有原因。届时走 build 侧二分：改 `morphe/youtube/YoutubeHook.kt` 的 `YouTubePatches` 数组分半，构建+装机+看 Library。备选嫌疑（按优先序）：`changeStartPagePatch`、`NavigationBar`、`FeatureOverride`（insertLiteralOverride 改全局 feature flag）、`HideAds`（默认开）、`channelWhitelistPatch`。
5. 调试技巧：`adb shell uiautomator dump` 读页面结构；`kill -3 <pid>` 后看 `/data/anr/trace_*`；tombstone 在 `/data/tombstones/`；设备 OPPO PJD110 有 root（`adb shell "su -c '...'"`），无 sqlite3。

## 任务 B：修 2 个失败的音乐指纹组（在途半成品收尾）

最后一批测试（2026-11-27 08:2x）中 music 侧 2 组 4 个 failed：

1. **`morphe/music/video/information/`（music-infra2 半成品）**：2 个失败，其中已知 `MusicVideoLengthMethod` 命中了**抽象方法** `Lwkk;->d()J`——运行时无法 hook 抽象方法，需解析到唯一具体实现（修法参考 yt-fp-fix 对 YouTube infocards 的同型修复：`morphe/youtube/layout/hide/infocards/Fingerprints.kt` 用 `findClassDirect` 解析接口唯一实现）。该组其余指纹已 matched。
2. **`morphe/music/interaction/dislikeredirection/`（music-quickwins2 半成品）**：2 个失败，查询过宽。按"指纹查询纪律"用参数类型/字符串收紧。上游 `patches/music/interaction/dislikeredirection/`；版本门控 `is_9_35_or_greater` 已备好。

两组的 Patch 文件都已落盘，修完指纹+测试绿即收尾。同目录其余交付（repeatstate/shufflestate/trackswipe/loop/mediasession/playerresponse/downloads）已测试绿。

## 任务 C：Crossfade 移植（零交付，重活）

- 上游：`morphe-patches/patches/src/main/kotlin/app/morphe/patches/music/interaction/crossfade/`（CrossfadePatch.kt 1288 行 + Fingerprints.kt 138 行）。
- 模块目标目录：`morphe/music/interaction/crossfade/`。
- 扩展侧 `app.morphe.extension.music.patches.CrossfadePatch.java` 已存在且在编译范围，先通读再设计 hook。
- 核心难点：上游用 patcher 接口注入（PlayerCoordinatorAccess/ExoPlayer 接口）——运行时不可行，必须翻译成 hook 语义（before/after 改参改返回值、调扩展静态方法）。
- 上游有 versionCheckPatch 依赖，旗标已备齐（`morphe/music/misc/playservice/VersionCheckPatch.kt`，is_9_19…is_9_45）。
- 33 条 crossfade 字符串已在 addresources 生成，偏好接线需求列在报告里即可（SettingsPatch 统一接线，见任务 F）。
- 指纹全部对 `/tmp/ytm-src/sources`（9.46.51）重新锚点验证。

## 任务 D：Play album songs + Enable audio/video switch（半成品/零交付）

1. **Play album songs**：`morphe/music/misc/album/Fingerprints.kt` 已落盘（avswitch2 被打断时只写了指纹）。缺 Patch 文件与指纹测试验证。上游 `patches/music/misc/album/PlayAlbumSongsPatch.kt`；依赖 interaction/playlisttracktap 的指纹（CommandResolver/WatchEndpointCommandBuilder）；扩展侧 `PlaylistTrackTapPatch.java` 和 `patches/album/` 已存在。
2. **Enable audio/video switch**：零交付。上游 `patches/music/misc/audiovideoswitch/`（EnableAudioVideoSwitchPatch.kt + QueueSwitchAbi.kt + QueueSwitchInstaller.kt）。依赖 `morphe/music/video/information/` 和 `morphe/music/video/playerresponse/`（任务 B 收尾后可用）。QueueSwitchInstaller 是 patcher 端代码生成器，运行时不可行——读 QueueSwitchAbi 搞清 ABI 语义后用 hook 等价实现。

## 任务 E：whitepicks 8 项（零交付）

全部在 YouTube 侧有同构已验证移植可抄（`morphe/youtube/` 下），抄模式、改音乐版指纹（必须对 9.46.51 重新锚点，不能照抄 YouTube 锚点）：

| 功能 | 抄 | 目标目录 | 上游 |
|---|---|---|---|
| SkipSilence | `youtube/video/skipsilence/` | `music/video/skipsilence/` | `music/video/audio/SkipSilencePatch.kt` |
| DisableDRCAudio | `youtube/video/drc/` | `music/video/drc/` | `music/video/audio/DisableDRCAudioPatch.kt` |
| ForceOriginalAudio | `morphe/shared/misc/audio/tracks/`（休眠红利已存在，确认音乐版可用即可） | `music/video/audio/` | `music/video/audio/ForceOriginalAudioPatch.kt` |
| VideoFormat | `youtube/video/format/` | `music/video/format/` | `music/video/format/VideoFormatPatch.kt` |
| DisableQUIC | `youtube/shared/quic/` 工厂模式 | `music/misc/quic/` | `music/misc/quic/DisableQUICProtocolPatch.kt` |
| OpenSystemShareSheet | `youtube/layout/sharesheet/` | `music/misc/sharesheet/` | `music/misc/sharesheet/OpenSystemShareSheetPatch.kt` |
| ChangeStartPage | `youtube/layout/startpage/` | `music/layout/startpage/` | `music/layout/startpage/ChangeStartPagePatch.kt` |
| DisableAutoCaptions | `youtube/layout/captions/` | `music/layout/captions/` | `music/layout/captions/DisableAutoCaptionsPatch.kt` |

对应扩展 Java 类全部已存在（`app.morphe.extension.music.patches/` 下 60 个文件），先读再设计 hook。

## 任务 F：全局接线（所有音乐 patch 交付后统一做）

1. **MusicHook.kt 注册**（`morphe/music/MusicHook.kt` 的 `YTMusicPatches` 数组）：把已交付 patch 全部注册。已知约束：
   - `Downloads` 必须在 `SettingsHook` **之前**（PreferenceScreen.close() 在 SettingsHook 内执行）。
   - 参考 YouTube 侧顺序约束：偏好注入类（SettingsHook）和 insertLiteralOverride（FeatureOverride）放最后。
2. **偏好接线**（`morphe/music/misc/settings/SettingsPatch.kt`，四屏 ADS/GENERAL/PLAYER/MISC，键 01/03/05/06，02/04/07+ 空位为休眠容量）：
   - Downloads 5 个偏好（music-downloads 报告）：`morphe_download_mode`（List，默认 IN_APP，PLAYER 屏 `morphe_external_downloader_screen` 子屏）、`morphe_download_force_4k`、`morphe_export_download_folder`（ExportFolderPreference）、`morphe_external_downloader_menu`、`morphe_external_downloader_name`（ExternalDownloaderPreference）。
   - 其余各 patch 的偏好需求以各 agent/任务报告为准。
3. 全部接线完成后：全量编译 + 全量指纹单测（Reddit 26 组绿 + YouTube 组绿/预期 skip + Music 组新增全绿）。

## 任务 G：Wave 2/3 未开工音乐补丁（分类账 backlog，按需）

SponsorBlock、ReturnYouTubeDislike、QueueLikeFilter、PlaybackSpeed、Scrobbling、Miniplayer 两件套（ChangeMiniplayerColor / EnableForcedMiniplayer + SwipeToDismiss + NextPreviousButtons）、SpoofAppVersion、Lyrics、SoundProof（重，资源注入需运行时重写）、Android Auto、Sonos。依赖 infra（任务 B 收尾）解锁。

## 任务 H：已知阻塞（🔴 构建依赖）

PrioritizeVideoQuality + RememberVideoQuality（YouTube 侧）：扩展类被 `app/build.gradle.kts` 的 JavaCompile exclude 排除（缺 protobuf/innertube 依赖）。除非把依赖链搬进模块构建，否则不实现。

## 任务 I：Reddit 掉帧——设备回归验证（静态审计已完成，无设备）

2026-11-27 完成全量静态审计（结论存 `~/.vibe` scratchpad reddit-jank-audit.md）：模块 58 个补丁的热路径回调全部合规（设置内存缓存、Logger 门控、无宽查询指纹、无每帧重活），**未发现吸烟枪**。最可能原因 = 冷启动初始化风暴（debug 构建每次冷启动主线程跑 300+ dexkit 指纹查询，2.0.115 前无缓存，之后每版本号一次）。

设备回归后的验证协议：
1. 装新构建（必升 versionCode）→ 第一次启动 Reddit 预期 10-25s 初始化卡顿（正常）→ force-stop
2. 第二次启动应流畅，logcat 验证 "Using cached keys: true"
3. 若第二次启动仍持续掉帧：`dumpsys gfxinfo com.reddit.frontpage` + 掉帧中 `kill -3` 抓 ANR trace + 从 launcher 正常打开模块 UI 用 Reddit 补丁开关二分（**注意：adb am start 拉起模块 UI 会 Binder is null 写不进配置，必须正常入口打开**）
4. 若 init 风暴不可接受再议后台化改造

---

## 状态基线（截至交接时）

- **Reddit**：全部交付，26+ 组测试绿。
- **YouTube**：A 层 32 补丁全部交付注册；yt-fp-fix 修复 12 个失败指纹后 YouTube 组 754 matched / 59 skipped / 0 failed。
- **YT Music**：已交付并测试绿 = HideAds、NavigationBar、BackgroundPlayback、EnableExclusiveAudioPlayback、EnableDebugging、SanitizeSharingLinks、Downloads（38 指纹全绿）、mediasession、playerresponse、repeatstate、shufflestate、trackswipe、loop、shared 组。半成品 = video/information（2 失败指纹）、dislikeredirection（2 失败指纹）、misc/album（仅指纹）。
- **Threads**：缕筛（简中帖/评论过滤）+ HideAds 已交付。
- **实机**：YouTube 21.45.82 Library 页问题已定位到 DRC 指纹（已修，待装机验证，见任务 A）。所有功能均未做系统实机验证（静态验证边界）。
- 设备：OPPO PJD110（root），YouTube 21.45.82，模块 2.0.115 待构建。
