# Codex 任务清单（2026-11-27 第三轮交接）

仓库：`/Users/carlos/Documents/Code/NexAlloy`（非 git 仓库）。LSPosed 运行时模块（applicationId `io.github.zailinn.nexalloy`），把 morphe-patches 上游补丁移植成运行时 hook。

## 本轮交付状态（2026-11-27）

- **任务 A 已完成**：debug Kotlin/Java 编译和 190 项单元测试全部通过。指纹报告完整：YouTube 906 matched / 70 skipped，Music 252 / 61，Reddit 327 / 2，零 failed。
- **任务 B 已取消**：用户明确表示可以不重构；CopyText 保留当前可用实现，未修改其 hook/扩展/指纹。CopyText 对照测试原开关 true 已恢复。
- **任务 C 已完成**：一级顺序改为 Layout、Filters、Ads、Post buttons、Navigation bar、Sidebar、Miscellaneous。未找到现有分类 summary 键，所以没有添加 summary 或新资源键。新增 `RedditSettingsSearchDialog.java`，两个设置入口的顶栏均有搜索按钮；以真实分类 probe 建索引，150ms 防抖，保留 IME 组合输入，标题/摘要/分类不区分大小写匹配，展示所属分类及摘要，空结果显示“无结果”；点击结果进入分类并按 key/先序索引定位。返回一级时更新顶栏标题。没有恢复滑动动画。
- **任务 D 已完成**：版本 2.0.129，`:app:assembleRelease` 启用 R8 构建成功；签名校验通过；使用与设备原安装一致的本机开发签名，APK 已覆盖安装。三个宿主均已 force-stop。产物 `app/build/outputs/apk/release/app-release.apk`，构建日志 `/tmp/nexalloy-129-validation.log`。
- **R8 已检查**：新增搜索类、RedditActivityHook、RedditPreferenceFragment 的类名与反射成员保留；现有 release 缓存策略与 !DEBUG 未改变，129 版本会使旧指纹缓存失效一次。
- **任务 E 按用户最新要求结束**：用户表示“不用验证了，可以用”，停止继续操作设备。此前已确认 Reddit 设置能打开且未闪退、菜单顺序与搜索入口正常，搜索 COPY 可匹配标题和摘要；未宣称已完成所有点击定位、浅色主题、复制/双击、YouTube 设置/Library 真机回归。代码中的第三轮修复已随129构建交付。
- `BasePreference` 的 null_title/null_summary 修复、配置 context 的模块资源 loader 修复、CustomDialogListPreference 防御和 Reddit 语言行移除均保留并通过本轮编译。

## CopyText 开关对照实测（2026-11-27，用户要求先比较）

- 设备当前 2.0.128 release；CopyText 原值 true，测完已恢复 true。未修改实现。
- 按开→关→关→开四轮，每轮冷启动后等待、预热，再两次相同六滑动手势；共 8 次、3046 帧。
- CopyText 开：1564 帧，合计掉帧 8.44%，单次掉帧率中位数 8.23%，单次 P50 的中位数 11ms。
- CopyText 关：1482 帧，合计掉帧 13.43%，单次掉帧率中位数 10.77%，单次 P50 的中位数 12ms；有一次 28.29% 的波动，其他三次 7.30%–11.32%。
- 结论：本次没有测出关闭 CopyText 的稳定收益；不能据此声称开启更快，也不支持为首页性能做整套重写。建议保留功能，热路径重构需另有证据或明确决定。
- 原始 gfxinfo 与结果：`app/build/reports/copytext-comparison/`。测试期间用户提到短暂警告，截图未捕获，读取的 logcat/模块日志暂未找到对应 hook 异常，已询问文字，未将其判为正常。

## 模块关闭基线（本轮）

- 确认两个新 Reddit 进程均无 NexAlloy 初始化日志；同样六滑动手势，四次共 1761 帧。
- 掉帧分别 7.32%、7.57%、5.32%、6.41%；合计 6.64%，单次 P50 9–10ms。
- 开模块且 CopyText 开的上一组为 8.44%，相差约 1.8 个百分点；内容/系统负载变化限制了因果判断。
- 原始数据：`app/build/reports/copytext-comparison/module-off-*`。

## 续跑记录（codex 第二轮，保留备查）

- 已修编译错误：CaptionWindowMeasure 的 XposedHelpers import、ambientmode 版本标记 import、settingsmenu 的 apply 接收者。
- 已升至 128，release 已装。指纹测试：YouTube 906 matched / 70 skipped；Music 252 / 61；Reddit 327 / 2。190 项测试全部通过（含 Crossfade）。
- 修 SetSubtitleTrack / Ambient config 的 StrictQuery、Ambient 默认开启字段索引、PiP 字段间距；Caption cookies 改 hook CronetUrlRequestContext；PiP 改 hook overlay ABI；音乐 pill 方法列表兼容构造器。
- Reddit 修父类 onCreate、补顶栏图标 tint、二级页背景；首页修复 Link 跨 ClassLoader 强转（RuntimeLink）、空规则跳过、搜索建议过滤关闭时不 hook 全局 equals，掉帧 53%→5%。
- 横向动画有残影，已移除整页和分类页动画（**不要重新加动画**）。

## 主 agent 第三轮修复（已在树里、未构建装机，不要重做）

用户报了三个 bug，全部已定位修复，修改详情：

### Bug 1：YT 打开 Morphe 设置报 toast `R.string.null_summary is null`
根因：`key=null` 的 preference 默认 summaryKey 是 `"${key}_summary"` → 字面量 `null_summary` → 序列化 `@string/null_summary` → 运行时找不到。
修复：`app/src/main/java/io/github/nexalloy/morphe/shared/misc/settings/preference/BasePreference.kt` 的 `titleKey`/`summaryKey` 默认值改为 `if (key == null) null else "${key}_..."`。

### Bug 2：Reddit 帖子复制文本没了
根因：宿主 `/data/data/com.reddit.frontpage/shared_prefs/morphe_prefs.xml` 里 `morphe_copy_text` 丢失 → 回退默认 FALSE → 安装期门控（`CopyTextPatch.kt` 第 29 行 `if (!Settings.COPY_TEXT.get()) return@patch`）不装 hook。
**设备侧已治愈**（无需代码改动）：prefs 已写回 `morphe_copy_text=true` 并 force-stop，当前已装的 128 版重开 Reddit 即恢复。
待办是**重写**（任务 B），不是修 bug。

### Bug 3：Reddit Morphe 设置改语言成中文 → 打开设置即闪退循环
根因链：`Utils.setContext`（`morphe-patches-library/extension-library/.../Utils.java`）在 MORPHE_LANGUAGE≠DEFAULT 时 `createConfigurationContext` 造出**没有模块资源 loader 的新 Resources**，且 `useActivityContextIfAvailable=false` 强制所有查找走这个坏 context → `CustomDialogListPreference.<clinit>` 的 `getIdentifierOrThrow("morphe_settings_custom_checkmark")` 抛 NotFoundException → `ExceptionInInitializerError` → 类被 JVM 永久毒化 → 之后每次打开设置必崩。用户语言设置已持久化 → 每次冷启动都崩。
修复（4 层，全在树里）：
1. **删语言设置**：`morphe-patches/extensions/reddit/.../categories/MiscellaneousPreferenceCategory.java` 移除 `SortedListPreference(MORPHE_LANGUAGE)` 行及 import（用户要求：Reddit 只英语）。
2. **根治**：`Utils.java` 的 `setContext` 在创建配置 context 后调 `addModuleResources(context)`——用 `BuildConfig.APPLICATION_ID` `createPackageContext(IGNORE_SECURITY)` 拿模块 APK 路径，R+ 用 `ResourcesLoader/ResourcesProvider.addLoaders`，<R 反射 `addAssetPath`；loader 静态缓存。
3. **防御**：`morphe-patches/extensions/shared/library/.../CustomDialogListPreference.java` 静态 drawable 解析改 `getDrawableIdentifierOrZero`（try/catch 返回 0），使用点 `if (DRAWABLE_CHECKMARK != 0)` 守卫——类不再被毒化。
4. **设备治愈**：已 sed 删除宿主 prefs 的 `morphe_language` 条目并 force-stop。
修复后 `:app:compileDebugKotlin + compileDebugJavaWithJavac` 全绿（当时快照）。

### 设备侧 adb 治愈命令（如需重跑）
```
adb shell su -c 'am force-stop com.reddit.frontpage'
# 清语言（如又出现）：sed -i "/morphe_language/d" /data/data/com.reddit.frontpage/shared_prefs/morphe_prefs.xml
# 补复制开关（如又丢失）：sed 在该文件 <map> 内加 <boolean name="morphe_copy_text" value="true" />
```

## 任务 A：合并后全量验证（先做）

主 agent 的修复与 codex 第二轮改动在同一棵树上，需要合并验证：
```
./gradlew :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
./gradlew :app:testDebugUnitTest --console=plain
```
- 指纹报告 `app/build/reports/fingerprints/testDebugUnitTest.json`，基线（codex 第二轮）：YouTube 906/70、Music 252/61、Reddit 327/2，0 failed
- 修复原则：**不许改指纹查询条件和锚点**，只修机械问题。jadx ground truth：YT 21.45.82 → `/tmp/yt-src/sources`；YTM 9.46.51 → `/tmp/ytm-src/sources`；Reddit 2026.39.1 → `/tmp/reddit-src/sources`；Threads → `/tmp/threads-src/sources`

## 任务 B：CopyText 重写（已取消）

用户最新指示：可以不重构。保留当前已生效实现，不执行下方历史重构要求。

### 历史要求（不再执行）

现状：`app/src/main/java/io/github/nexalloy/morphe/reddit/layout/copytext/CopyTextPatch.kt` 可用（设置开则功能正常），但 `ComposeDispatchTouchEventFingerprint` 挂在 Compose dispatchTouchEvent 上，**每个触摸事件过一次 hook bridge**，是首页掉帧嫌疑。扩展侧 `morphe-patches/extensions/reddit/.../patches/CopyTextPatch.java`（只可加方法，别删现有）。

参考实现（重点研读，路径含特殊字符 ’）：
- `/Users/carlos/Documents/Code/Redditn‘t/com.wizpizz.reddidnt/app/src/main/java/com/reddit/adblocker/hooks/TextSelectionInstaller.kt`
- `/Users/carlos/Documents/Code/Redditn‘t/com.wizpizz.reddidnt/app/src/main/java/com/reddit/adblocker/hooks/features/TextSelection.kt`

要求：
1. 零 per-touch hook：SelectionContainer/文本 composable 包装走安装期一次性 hook；注释 tap/long-press 折叠拦截走**手势 lambda 构造器 hook**（Redditn't 的做法：构造期把 collapse 回调参数换成 no-op，双击点赞不变）。
2. 保留安装期门控（`Settings.COPY_TEXT` 关 = 零 hook）。
3. 功能等价：帖子标题/正文/评论长按可选中文本；选择时不折叠评论；不影响双击点赞；关闭设置完全恢复原生。
4. 审计现有全部 hook：凡是每次触摸/每帧都回调的，消灭或移到安装期；hook 回调里禁止反射/字符串格式化等重活（缓存到闭包）。
5. 指纹纪律：dexkit 基础查询收紧到个位数候选（字符串/参数/访问标志），逐个在 `/tmp/reddit-src/sources`（2026.39.1）验证锚点；无法唯一锚定的 `@SkipTest + matchAll().first()` 并注明；消歧 `@StrictQuery`。
6. 模块 DSL 参考：`io.github.nexalloy.morphe.FingerprintCompat.kt`（findClassDirect/findMethodDirect 等）。

## 任务 C：Reddit 设置布局优化 + 搜索（用户明确要求）

用户原话："设置项太杂乱了，找不到哪在哪"。改这些文件（独占）：
- `morphe-patches/extensions/reddit/.../settings/RedditActivityHook.java`
- `morphe-patches/extensions/reddit/.../settings/preference/RedditPreferenceFragment.java`
- 可在同目录新建搜索相关文件。**不要动 categories/、reddit/misc/settings/SettingsPatch.kt、RedditHook.kt、不要重新加滑页动画**（残影问题已特意移除）。

1. **布局优化**：按使用频率重排一级菜单 7 行（现状顺序 ads/filters/navigationBar/sidebar/postButtons/layout/miscellaneous，建议 Layout、Filters、Ads、Post buttons、Navigation bar、Sidebar、Miscellaneous，可按各分类实际条目数微调并说明理由）。一级行加 summary：先 grep `morphe-patches/patches/src/main/resources/addresources/values/reddit/strings.xml` 有没有 `morphe_screen_*_summary` 键，没有就跳过，**禁止发明新键**。
2. **设置搜索**：
   - 入口：MorpheSettingsDialog toolbar 右侧搜索图标（drawable `morphe_settings_search_icon(_bold)`，`ResourceUtils.getDrawable` 可取，tint 用现有 appForegroundColor）
   - 搜索界面（Dialog 上叠全屏 View 层或新 DialogFragment，自选）：输入框 + 实时过滤（case-insensitive；TextWatcher + Handler.postDelayed 150ms 防抖；中文输入法拼音组合期间不要丢查询）
   - 数据源 = 7 个分类的全部设置项（复用 `RedditPreferenceFragment.addScreenRow` 已验证的 probe 模式收集 title/summary/category；注意 probe 的 `getPreferenceManager().createPreferenceScreen` 用法别改）
   - 结果行：设置标题 + 所属分类 subtitle（有 summary 也显示）；空结果显示"无结果"
   - 点结果 → 关搜索 → `openCategory` 打开对应二级屏并**滚动到该项**（fragment arguments 传目标 key；onViewCreated 里算扁平索引 = 沿 PreferenceScreen 先序遍历序号（PreferenceCategory 头占 1 项），`ListView.setSelection(索引)`，不要求平滑）
   - 配色沿用 RedditSettingsStyle（深浅主题都可用）；空指针路径全防御

## 任务 D：构建 RELEASE + 装机

**用户手机装的是 release 包**：
1. `app/build.gradle.kts` versionCode 128 → 129（必须升；release 持久指纹缓存键含 VERSION_CODE）
2. `./gradlew :app:assembleRelease --console=plain`（签名配置自查，产出 `app/build/outputs/apk/release/`）
3. `adb install -r` release APK
4. force-stop 三个宿主：`com.google.android.youtube`、`com.google.android.apps.youtube.music`、`com.reddit.frontpage`（`adb shell su -c 'am force-stop <pkg>'`）

## 任务 E：真机验证清单

| 验证项 | 预期 |
|---|---|
| YT 打开 Morphe 设置 | **不再弹 `null_summary` toast**（bug 1 修复验证） |
| Reddit 打开 Morphe 设置 | 不闪退（bug 3 修复验证；语言行已从 Miscellaneous 消失） |
| Reddit 帖子长按复制 | 恢复（当前 128 版已通过 prefs 恢复；重写版功能等价 + 首页滑动不掉帧） |
| Reddit 掉帧 | 任务 B 重写后首页滑动实测（对比 gfxinfo，参考此前 53%→5% 基线） |
| Reddit 设置搜索 | 搜索图标出现、输入过滤、点击跳转定位 |
| YT 设置页 | Morphe 分组标题与 Account 对齐/同字号（前轮修复回归确认） |
| Library 页 | YouTube Library/You 页正常（GetPremiumView 已删，确认没复发） |

## 红线

- 禁止改指纹查询条件/锚点；语义不确定看上游 `morphe-patches/patches/.../{youtube,music,reddit,shared}/` 和扩展 `morphe-patches/extensions/`（已编译进模块，import `app.morphe.extension.*`）
- 禁止重新加 Reddit 设置滑页动画（残影已特意移除）
- `PatchExecutor.kt:206` 的 `!DEBUG` 是用户主动改的（debug 每次启动全量初始化、release 走持久缓存），不要"修"
- 设置字符串键必须已存在于 addresources（grep 确认），禁止发明新键

## 已知架构限制（不是 bug，别修）

- StableCaptionBlocks 稳定块/双语渲染优雅降级（运行时无法给宿主类加接口）；SB 音乐时间条片段 z-order 在最上层；`music/video/format/videoFormatPatch` 故意未注册（internal 基础设施）；playerflyoutmenu 保留 <21.44 旧版分支；`VideoStreamingDataToStringFingerprint` 同名重复（旧有、定义一致、无引用，良性）；CaptionsInPip 依赖 LSPosed 接口 hook 传播（失败备选 hook `ouq` 实现）
- HANDOFF.md 音乐任务 C/D/E 已过时（已交付）；任务 G backlog（PlaybackSpeed、Lyrics、Miniplayer 两件套、SpoofAppVersion、Scrobbling）仍未做；任务 H 阻塞不动（PrioritizeVideoQuality/RememberVideoQuality 被 build.gradle exclude）
