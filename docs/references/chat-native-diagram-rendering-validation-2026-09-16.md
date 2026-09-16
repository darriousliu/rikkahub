# 聊天原生 Mermaid/SVG 渲染验证记录

日期：2026-09-16

本轮使用临时、固定的离线聊天 Markdown：两个 Mermaid（flowchart、sequenceDiagram）、一个 SVG、fenced HTML、无效 Mermaid `bad diagram` 与未闭合 SVG `<svg>`。未发送模型请求，也未读取、打印或清空既有会话、凭据或设置。

| 平台 | 实际操作与结果 | 证据 |
| --- | --- | --- |
| Android 17 / `emulator-5554` arm64 | 通过。安装最新 debug APK 与 androidTest APK 后，直接 `am instrument` 运行 `NativeDiagramChatTest`，结果 `OK (1 test)`，耗时 5.327 s。最终 Gradle targeted `connectedDebugAndroidTest` 也已刷新报告为 `tests=1, failures=0`。测试经真实 `MarkdownBlock` 消息路径断言 5 个 native hosts 中 3 个 `Ready`、2 个 `Error`，6 个源码切换，预览打开与 `Close`，以及深浅主题。它递归取得真实 Android `WebView`，用 JS 点击 fenced HTML 的 `fixture-action` 并核对页面状态变化。 | `app/build/outputs/androidTest-results/connected/debug/TEST-Pixel_10_Pro_XL(AVD) - 17.xml`（最终 Gradle 报告） |
| macOS Desktop arm64 | 通过。临时 fixture 以隔离 profile 和 PID `87521` 启动；AX 精确定位窗口 `RikkaHub native diagram fixture`，实际显示多个 Mermaid 原生图。点击首图导出后出现系统 Save 对话框；用户协助选择位置并保存 `~/Documents/mermaid_1789567990896.png`。已检查产物为 41,752 bytes、641×2048 PNG，包含完整 Start → Valid? → Finish 流程、yes/no 连线和 `rikka-ai.com` 水印。 | 用户保存的 `~/Documents/mermaid_1789567990896.png`；该用户文件保留不删除。 |
| iOS iPhone 17 Pro Max / `03C090DA-107B-4F9F-BCCD-8D5265D32820` | 通过原生图表核心渲染和控件。以 `SIMCTL_CHILD_RIKKAHUB_NATIVE_DIAGRAM_FIXTURE=1` 启动已重链 Debug app；截图显示两个 Mermaid 与 SVG 原生图。XcodeBuildMCP runtime snapshot 确认源码切换后出现完整 `graph TD ...`，预览打开后出现精确 `Close`。继续滚动后，语义快照确认 Mermaid 错误为 `No Mermaid diagram type detected`，SVG 错误为未闭合根节点解析错误，且页面仍可操作。错误截图还实际显示内联 HTML 的按钮 `HTML fixture remains inline` 和结果 `HTML fixture clicked`。 | [初始渲染](evidence/chat-native-diagrams-2026-09-16/ios-initial.png)，[错误状态](evidence/chat-native-diagrams-2026-09-16/ios-errors.png) |
| Windows | Blocked：本轮没有 Windows 主机或 VM。 | 未覆盖，不能由其他平台替代。 |

## 代码与正式构建

聊天使用 `merman v0.8.0-alpha.6`（`d529f858ea3d337a1bdc8fe12e44e1403ededf2e`）和
`coil-resvg 1.1.2`。Rust 保留上游默认 feature，包括 math。配置经 C ABI 传递，原始源码及 frontmatter 不改写。

| 检查 | 结果 |
| --- | --- |
| Rust C ABI / Python 构建脚本 | 各 5 项测试通过；`cargo fmt --check` 通过 |
| Mermaid KMP 模块 | JVM、iOS 27 模拟器、Android 17 的 16 KB arm64 模拟器各 5 项测试通过 |
| 聊天 JVM 回归 | `NativeDiagramImageTest` 3 项、`WebViewPreviewTest` 2 项、`ChatImageExportTest` 5 项通过；覆盖真实原生解码、像素与缓存、中文/公式/主题、错误、Windows HTML 分支和聊天截图等待 |
| Android 正式源码构建 | `:app:assembleDebug` 通过；arm64-v8a 与 x86_64 APK 均含 `librikkahub_mermaid.so`、`libresvg_core.so`，不含已删除的 `mermaid.min.js` |
| 桌面发布混淆 | `:desktopApp:proguardReleaseJars` 通过；随后只使用混淆后的 JAR 调用原生库：Mermaid 输出 9,768 字符 SVG，含中文且公式已排版；resvg 输出 100×50 像素，实测 RGBA 为 `e6141eff` |
| iOS 正式源码构建 | `:composeApp:linkDebugFrameworkIosSimulatorArm64` 与 Xcode Debug app 构建通过，并重新安装到验证模拟器；产物不含临时 fixture 或旧 Mermaid JS |
| Windows/Linux 构建 | Windows x64 DLL、Linux x64 SO 已交叉编译；未在相应系统运行 |
| CI 配置 | 补充 Rust 1.95.0、Android NDK 29.0.14206865 与 Windows Zig/cargo-zigbuild 环境，YAML 语法检查通过；未触发远程 CI |

最终联合 Gradle 构建耗时 3m 59s。构建时使用 `-Pksp.incremental=false --no-configuration-cache`，
避免既有 KSP 缓存干扰。删除脚本后清除了本机生成目录中的旧资源副本，并检查最终包内容。
发布原生调用的复核代码保存在 [NativeDiagramReleaseProbe.java](evidence/chat-native-diagrams-2026-09-16/NativeDiagramReleaseProbe.java)。

## 未覆盖项

- iOS：已视觉确认 fenced HTML 按钮和 `clicked` 结果，但本轮未单独记录 Xcode UI 自动化对该 DOM 的点击步骤。Android 已完成真实 WebView 的 HTML 按钮交互验证。
- Android/iOS：没有自动化原生文件选择器保存。macOS 已实际保存并验证导出 PNG。
- iOS 曾点击 `Fixture dark`，但保留截图时预览 overlay 已打开，未能明确复核主题颜色变化；不将 iOS 深色主题标记为通过。`ImagePager` 的双指缩放手势未执行；预览打开与关闭已验证。

## Mermaid 视口与预览入口补充回归

后续布局修复恢复 Mermaid 的固定 `200.dp` 视口、按宽度绘制和块内垂直滚动；图面点击不再打开预览，代码块标题和右下角按钮均保留预览入口。

- Android 17（API 37）Pixel_10_Pro_XL：针对 `NativeDiagramChatTest` 运行
  `:app:connectedDebugAndroidTest`，最终 XML 为 `tests=1, failures=0`。仪器测试经真实聊天
  `MarkdownBlock` 路径确认首个 Mermaid viewport 高为 200dp，`VerticalScrollAxisRange.maxValue > 0`；
  对图面真实 touch click 和 `swipeUp` 后都没有 dialog，且滚动值增长；标题右侧与右下角预览按钮各自打开并关闭 dialog。
- 该次测试启用证据开关，在 target app 的
  `cache/native-diagram-layout-evidence/` 生成 `mermaid-fit-width.png` 与
  `mermaid-scrolled.png`。Gradle 结束后验证设备已从 ADB 列表断开，无法拉取这两张缓存图；未将其
  视为已保留截图。本轮未做截图视觉复核，专用设备缓存也暂无法清理。
- 代码回归：`ChatImageExportTest` 为 5 tests / 0 failures，
  `compileKotlinIosSimulatorArm64` 通过（25 s）。本补充轮没有重新启动 macOS 或 iOS fixture，
  因而不把上一轮 GUI 结果作为本轮布局行为的跨平台验证。

## 清理

验证完成后已终止仅 PID `87521` 的桌面 fixture 和 iOS simulator fixture，恢复 desktop/iOS 临时入口，并删除 `NativeDiagramFixturePage`。未保留含桌面背景的截图；仅保留 iOS fixture 画面。Windows 保持 Blocked。
