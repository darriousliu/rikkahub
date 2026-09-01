# Compose Multiplatform 平台能力现状

本文档记录 Android / iOS / Desktop 三端**当前代码实际实现到什么程度**。

和相邻文档的分工：

- [Compose Multiplatform 迁移边界](compose-multiplatform-migration.md)：记录迁移的**目标边界**和依赖约束（应该怎么做）。
- 本文档：记录**现状快照**（现在做到哪了）。
- [Compose Multiplatform 人工测试矩阵](cmp-manual-test-matrix.md)：记录需要设备/权限/凭据的**人工验收**结论。

图例：✅ 完整实现 · 🟡 降级或部分可用 · ❌ 未实现

## 基线

- 快照提交：`3a6b115c7`（分支 `feature/cmp-migrate`）
- 更新日期：2026-09-01
- 结论来源：直接读源码（`expect`/`actual` 配对、平台 source set 内容、DI 注入点、`PlatformRouteContent` 路由分发），不以能否编译代替能力判断。
- 编译验证：`:composeApp:compileKotlinIosSimulatorArm64`、`compileKotlinIosArm64`、`compileKotlinJvm`、`compileAndroidMain` 均通过。

## 代码分布

| 位置 | 文件数 | Kotlin 行数 |
|---|---:|---:|
| 共享模块 `commonMain` | 526 | **87,279** |
| `androidMain` | 47 | 4,160 |
| `iosMain` | 42 | 2,137 |
| `jvmMain` | 45 | 1,449 |
| `androidJvmMain` | 2 | 85 |
| `mobileMain`（Android + iOS） | 1 | 28 |
| `nativeMain` | 1 | 8 |
| `:app`（Android application shell） | 136 | **22,926** |
| `:document`（Android-only） | 4 | 1,053 |
| `:workspace`（Android-only） | 7 | 1,401 |
| `:desktopApp`（JVM shell） | 1 | 138 |

统计范围：8 个共享 KMP 模块（`composeApp`、`common`、`ai`、`search`、`speech`、`highlight`、`web`、`material3`）加三个平台壳，不含测试源集。

- 共享模块内 `commonMain` 占比 **91.7%**（87,279 / 95,146），平台适配层只占 8.3%。
- 全仓主源码 `commonMain` 占比 **72.3%**（87,279 / 120,664）。`:app` 从 24,774 行降到 22,926 行。

复现命令：

```bash
for m in composeApp common ai search speech highlight web material3; do for ss in commonMain androidMain jvmMain iosMain mobileMain androidJvmMain nativeMain; do d=$m/src/$ss; [ -d "$d" ] && echo "$m $ss $(find $d -name '*.kt' | wc -l) $(find $d -name '*.kt' -exec cat {} + | wc -l)"; done; done
```

## 已三端打通（核心业务）

| 能力 | Android | iOS | Desktop | 实现位置 |
|---|:--:|:--:|:--:|---|
| 聊天主流程（流式 / 分支 / MCP 工具调用） | ✅ | ✅ | ✅ | `SharedChatRuntime` |
| 助手配置 / 会话 / 历史 / 收藏 / 统计 / 消息搜索 | ✅ | ✅ | ✅ | `commonMain` 页面与 VM（部分助手配置在非 Android 端没有运行时，见下表） |
| Provider 与模型管理、搜索服务 SDK | ✅ | ✅ | ✅ | `:ai` / `:search` |
| Room 3 + bundled SQLite | ✅ | ✅ | ✅ | `AppDatabase` expect/actual |
| DataStore 设置 | ✅ | ✅ | ✅ | `SettingsStore` |
| MCP 运行时 | ✅ | ✅ | ✅ | `McpManager` |
| OAuth 授权与回调 | ✅ Custom Tabs | ✅ ASWebAuthenticationSession | ✅ loopback | `OAuthCallbackSessionFactory` |
| 备份恢复（WebDAV / S3 / ZIP 流式） | ✅ | ✅ | ✅ | `SharedWebDavBackupTransport` / `SharedS3BackupTransport` |
| Skills 存储 | ✅ | ✅ | ✅ | `FileKitSkillStore` |
| 翻译器 | ✅ | ✅ | ✅ | `SharedTranslationRuntime` |
| 图片生成 | ✅ | ✅ | ✅ | `SharedImageGenerationRuntime` |
| Markdown / LaTeX(RaTeX) / 代码高亮 | ✅ | ✅ | ✅ | `commonMain` + `:highlight` |
| 云端 TTS（OpenAI / Gemini / MiniMax 等） | ✅ | ✅ | ✅ | `speech/commonMain` |
| FileKit 文件选择与沙箱导入 | ✅ | ✅ | ✅ | `SharedChatAttachmentStore` |
| 更新检查 | ✅ | ✅ | ✅ | `UpdateChecker` |
| Message Transformer 流水线 | ✅ | ✅ | ✅ | 契约与十个实现在 `commonMain`，平台差异收敛到四个注入点 |
| 内置 AI 工具（搜索 / 会话 / 记忆 / 技能 / JS / AskUser / 时间 / 剪贴板 / TTS） | ✅ | ✅ | ✅ | `LocalTools` + `SharedChatRuntime`，平台专有工具经 `PlatformLocalTools` 注入 |

## 平台适配（三端各自实现）

| 能力 | Android | iOS | Desktop |
|---|:--:|:--:|:--:|
| 通知 | ✅ 前台服务 | ✅ UNUserNotification | ✅ SystemTray |
| 打开外部链接 | ✅ CustomTabs | ✅ `UIApplication.openURL` | ✅ `Desktop.browse` |
| 崩溃与统计 | ✅ Firebase | ✅ Firebase Apple | ✅ Sentry |
| 局域网服务发现 | ✅ JmDNS | ✅ Bonjour `NSNetService` | ✅ JmDNS |
| 系统 TTS | ✅ | ✅ | ✅ |
| 音频播放 | ✅ Media3 | ✅ AVFoundation | ✅ JavaFX |
| 二维码图片解码 | ✅ | ✅ | ✅ |
| 二维码渲染 | ✅ | ✅ CoreImage | ✅ |
| 角色卡元数据读取 | ✅ | ✅ | ✅ |
| 相机权限 | ✅ 运行时请求 | ✅ AVFoundation 授权 | 🟡 恒为 granted |
| 通知 / 局域网权限 | ✅ 运行时请求 | 🟡 恒为 granted（系统首次使用时弹） | 🟡 恒为 granted |
| 请求日志采集 | ✅ OkHttp 拦截器 | ✅ Ktor 插件 | ✅ Ktor 插件 |
| 日期时间与数字本地化 | ✅ `java.time` | ✅ NSDateFormatter | ✅ `java.time` |
| 剪贴板读写 | ✅ ClipboardManager | ✅ UIPasteboard | ✅ AWT Toolkit |

## 平台差异（当前缺口）

| 能力 | Android | iOS | Desktop | 说明 |
|---|:--:|:--:|:--:|---|
| 二进制文档解析 PDF/DOCX/PPTX/EPUB | ✅ | ❌ | ❌ | 解析器在 Android-only 的 `:document`，`DocumentTextExtractor` 在其余端返回 null；纯文本附件三端可读 |
| Message Transformer：Workspace 提醒 | ✅ | ❌ | ❌ | 依赖 Android-only 的 `:workspace`；非 Android 端 `workspaceId` 恒为空，transformer 直接返回 |
| 流式过程中的 visualTransform | ✅ | ❌ | ❌ | `SharedChatRuntime` 的状态即显示源，没有显示/存储分流，think 标签要等生成结束才转成推理块 |
| 日历 / 屏幕使用时间工具 | ✅ | ❌ | ❌ | 依赖 Android 专有 API，经 `PlatformLocalTools` 注入，其余端为空实现 |
| 内嵌 Web Server | ✅ | ❌ | ✅ | iOS 是 `UnavailableWebServerHost`，按迁移边界明确 unavailable |
| Workspace 沙箱 + 终端 | ✅ | ❌ | ❌ | `:workspace` 含 CMake/native，Android-only |
| ASR 语音输入 | ✅ | ❌ | ❌ | 5 个 Controller 全在 `speech/androidMain`，只有设置页共享 |
| 相机扫码 | ✅ | ✅ | ❌ | Android/iOS 共享 `mobileMain` 的 KScan；`JvmQrScanner` 返回 `null` |
| WebView 页面 | ✅ | ❌ | ✅ | Desktop 用 kdroidfilter WebView |
| Mermaid 图表 | ✅ | ❌ | ❌ | 依赖 WebView，`mermaidRenderer` 仅 Android 非空 |
| 代码块 HTML 预览 | ✅ | ❌ | ✅ | |
| 保存代码到文件 | ✅ | ❌ | ❌ | JVM/iOS 的 `saveCode` 为 `null` |
| 聊天导出为图片 | ✅ | ❌ | ❌ | `RenderExport` 默认空实现 |
| 系统分享入口（分享到 App） | ✅ | ❌ | ❌ | `Screen.ShareHandler` 仅 Android 路由 |
| 文件管理页 / Debug 页 | ✅ | ❌ | ❌ | `Screen.SettingFiles` / `Screen.Debug` 仅 Android 路由 |
| 应用内更新下载安装 | ✅ | ❌ | ❌ | 更新*检查*已共享 |
| 图片裁剪 | ✅ uCrop | 🟡 跳过裁剪返回原图 | 🟡 跳过裁剪返回原图 | 有意降级 |
| GIF 动画 | ✅ coil-gif | 🟡 首帧 | 🟡 首帧 | 有意降级 |
| TTS 悬浮窗 | ✅ FloatingX | ❌ 空 actual | ❌ 空 actual | |
| 分享文本 | ✅ Intent | ✅ 系统分享面板 | 🟡 仅复制到剪贴板 | |
| 分享文件 | ✅ | ✅ FileKit | ❌ `sharePlatformFile` 空实现 | |
| 附件粘贴/拖入、音量键翻页、屏幕常亮 | ✅ | ❌ | ❌ | `ChatInputPlatformContent` / `ChatPagePlatformContent` 平台内容 |

## 已知不一致

| 项 | 现象 | 处理建议 |
|---|---|---|
| `PlatformCapabilities.kt` 的 `QR_RENDER` | iOS 被标为 `UNAVAILABLE`，但 `IosQrCodeRenderer` 已是完整 CoreImage 实现 | 声明矩阵落后于代码。当前 `hasCapability` 只被 `WORKSPACE` 用于收起入口，不影响功能，但应同步 |
| `SkillStore` 的 name 参数语义 | `listSkills()` 返回 frontmatter 显示名，而 `readSkillFile` 等方法把参数当目录名解析。`SkillSummary.directoryName` 已补齐，`SkillsTools` 与 `SkillDetailVM` 已改用它，但 `SkillsVM` 的保存路径仍以显示名建目录 | 建议把 `SkillStore` 的参数明确改名为 `directoryName`，让语义写在签名里 |

## 维护方式

改动任何 `expect`/`actual`、平台 source set 内容、`PlatformRouteContent` 路由或 DI 注入点时，同步更新本文档对应行，并更新顶部的"基线"提交与日期。

判定口径：

- ✅ 该平台有真实实现，能完成能力本身的语义。
- 🟡 有 actual 但语义被降级（跳过、回退、恒为真），调用方不会失败但结果不完整。
- ❌ 没有实现：空 actual、返回 `null`、路由落到 `SharedUnavailableRouteContent`，或模块根本不在该平台依赖图里。
