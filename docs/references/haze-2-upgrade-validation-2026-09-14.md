# Haze 依赖升级与编译验证

日期：2026-09-14。

## 原因与适配

- Haze 从 `2.0.0-alpha03` 升为 `2.0.0-beta03` 后，旧的 `hazeEffect(state) { blurEffect { ... } }`
  已不能编译。聊天输入框改用 `hazeBlur(input = HazeInput.Sources(hazeState), style = inputHazeStyle)`。
  继续使用原来的共享 `HazeState`、`HazeMaterials.thin`、背景颜色和模糊开关。
- 新版 Haze Android AAR 要求 `minCompileSdk=37`、`minCompileMinorSdk=2`。
  `app` 与 `composeApp` 使用 AGP 的 `compileSdk { version = release(37) { minorApiLevel = 2 } }`；
  `minSdk=26` 和应用 `targetSdk=37` 保持原值。本地验证已安装 `platforms;android-37.2`。
- 新的 `org.jetbrains.compose.ui:ui-tooling-preview` 依赖提供统一的
  `androidx.compose.ui.tooling.preview.Preview`。更新 11 处旧包导入，保留预览函数与运行逻辑。
- 一并纳入本轮已有的 Gradle 依赖版本和 DSL 升级；现有 Xcode 签名/方案改动与 CMP 平台抽象审计文件
  不属于本次修复，留在工作区。

依据：

- [Haze 2.0 迁移指南](https://chrisbanes.github.io/haze/latest/migrating-2.0/)
- [Compose UI previews](https://kotlinlang.org/docs/multiplatform/compose-previews.html)
- [Android 编译 SDK 配置](https://developer.android.com/build)
- 本地 Gradle 缓存中的 Haze `2.0.0-beta03` 源码和 Android AAR 元数据。

## 代码验证

| 步骤 | 预期 | 实际与覆盖范围 |
| --- | --- | --- |
| 修复前 `:composeApp:compileKotlinJvm` | 复现依赖升级造成的 API 错误 | 复现 `blurEffect` 未解析、`hazeEffect` 参数不匹配以及 11 处旧 `Preview` 导入错误 |
| 修复后 `:composeApp:compileKotlinJvm` | 新 Haze/Preview API 可编译 | 通过 |
| `:composeApp:jvmTest --tests me.rerere.rikkahub.ui.components.ai.ChatInputAsrTest` | 输入、ASR 状态与发送/取消行为不回归 | 3 项通过，0 失败、0 跳过；该测试关闭模糊，因此不替代模糊视觉验证 |
| `:composeApp:compileKotlinIosSimulatorArm64` | 新 API 在 iOS 共享源码中可编译 | 通过 |
| `:desktopApp:createDistributable` | 生成可运行的桌面包 | 通过 |
| SDK 更新前 `:app:assembleDebug` | 检查 Android 依赖要求 | 复现 Haze 四个 AAR 需要编译 SDK 37.2，当前 37.0 不满足 |
| SDK 更新后 `:app:assembleDebug` | 生成新版 Android Debug APK | 通过；包含 AAR 元数据检查、Android Kotlin 编译、资源与 APK 打包 |
| XcodeBuildMCP `build_sim`，`iosApp` scheme、Debug、iPhone 17 Pro Max | 链接共享 framework 并构建可安装的 iOS Simulator 应用 | 通过，285.5 秒；包含 `:composeApp:linkDebugFrameworkIosSimulatorArm64` 与 Swift 编译/应用打包 |

本轮产物 SHA-256：

| 产物 | SHA-256 |
| --- | --- |
| Android `app-arm64-v8a-debug.apk` | `a7cbd474cd3e4519a556ae75bc560001a39747adb49d7fef51150691980595b4` |
| Desktop 包内 `composeApp-jvm-c04d605b1bc49d359a47cf9746adced.jar` | `17db21afbb983b20c1d1fb856a99def9906f194ce5daea51b82b7503f4421bd7` |
| iOS Simulator `RikkaHub.debug.dylib` | `f98d5e5457453886350c61733881e6bedd882dc0829bd6b7eeecd8c3c3469cb4` |

## GUI 验证判定

需要 GUI：本轮迁移替换实际绘制模糊的 Modifier，且升级了 Haze 渲染实现；代码编译和既有输入测试
不能证明开启模糊后的背景绘制、切换及键盘表现。依照根 `AGENTS.md`，由 `gpt-5.6-terra` 子 agent
使用本轮构建完成的产物核对受影响平台，并记录实际操作、预期、结果和未覆盖范围。无需真实模型请求。

Android 已实际执行 `关闭 → 开启模糊 → 返回聊天页 → 聚焦并输入未发送文本 → 清空文本 → 恢复关闭`。
启用后输入区正常绘制，未出现崩溃或黑块；焦点和发送按钮状态正常。现有测试数据为空会话，未覆盖消息列表
滚动时的背景采样。

Desktop 由用户人工验证通过：开启模糊开关后，输入框后方有正常的模糊效果。agent 已核对本轮包并以
隔离 profile 启动，但专属窗口捕获受到窗口管理/焦点干扰；Desktop 的视觉通过结论来自用户反馈，
不将 agent 的启动操作或其他窗口截图作为视觉证据。

iOS 已安装并启动本轮构建，实际开启模糊、返回聊天页并核对输入区正常绘制，随后恢复开关并退出应用。
文本输入工具返回成功，但截图没有独立显示输入文本或键盘，因此 iOS 文本/焦点用例不标记通过。

用户在同轮进一步手动验证 Android 和 iOS，并确认两端 blur 均正常。因此三端模糊效果均已获得用户的
人工验证确认；以上 agent 的列表滚动与 iOS 文本/焦点覆盖限制仍按各自验证方法如实保留。

操作详情、实际覆盖范围及应用截图见
[GUI 回归记录](evidence/haze-2-upgrade-2026-09-14/verification.md)。测试开关和草稿已恢复，测试进程、
隔离 Desktop profile 与临时构建日志已清理；未发送模型请求。
