# Haze 2.0 GUI 回归记录

日期：2026-09-14（Asia/Shanghai）。验证 agent：gpt-5.6-terra。

## 范围与前置条件

- 目标改动：聊天输入区由 Haze `2.0.0-alpha03` 的 `hazeEffect` 迁移至 Haze `2.0.0-beta03` 的 `hazeBlur`，应保留既有模糊开关、`HazeMaterials.thin` 和 source 设置。
- 仅接受父任务在本轮修复后明确提供的新构建；不把已有 APK 或 Desktop 包作为本改动的通过依据。
- 最小 smoke：聊天输入框模糊开关、聊天列表滚动背景、输入焦点/键盘与返回聊天页；预期无崩溃、无黑块、输入仍可用且开关切换正常。不发模型请求，不发送消息，不改用户设置；如需切换设置，结束时恢复。

## 可用性预检

| 平台 | 结果 | 证据/限制 |
| --- | --- | --- |
| Android | 已执行 | `emulator-5554`（sdk_gphone16k_arm64）在线；已安装并验证本轮 APK。 |
| iOS | 已执行（范围内） | iPhone 17 Pro Max（`03C090DA-107B-4F9F-BCCD-8D5265D32820`）已安装、启动并完成模糊开关/返回聊天回归。 |
| Desktop | 用户人工验证通过 | 用户实际确认：开启开关后输入框背景模糊正常。agent 已核对本轮包和 JAR hash；macOS Stage Manager/并行 Emulator 使专属窗口捕获失真，因此没有 agent 侧窗口截图。 |

## 实际验证

### Android（Pass：范围内）

- 产物：`app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`，构建时间为 2026-09-14 03:05:38 +0800，SHA-256 为 `a7cbd474cd3e4519a556ae75bc560001a39747adb49d7fef51150691980595b4`，与父任务提供值一致。使用 `adb install -r` 安装到 `emulator-5554`，未清空或卸载 profile。
- 正常启动 `me.rerere.rikkahub.debug/.RouteActivity` 后，实际显示正式 `New Chat` 聊天页与输入框；无崩溃或黑块。见 `android-chat-initial.png`。
- 实际走到 **Settings → Preferences → General**。`Enable Blur Effect` 初始 UI 树为 `checked=false`；点击后变为 `checked=true`，见 `android-blur-enabled-setting.png`。
- 从 General 经系统返回、关闭聊天抽屉，实际回到聊天页。启用状态下输入栏仍完整绘制，没有黑块或空白覆盖，见 `android-blur-enabled-chat.png`。
- 聚焦输入框，键盘/输入 UI 实际出现并通过 ADB 输入未发送标记 `HazeGUI`；UI 树确认 EditText `text="HazeGUI"` 且 `focused=true`，发送控件变为可用。见 `android-blur-input-focus.png`。随后逐字符清除，未发送模型请求或创建会话。
- 再次进入 General，点击将 `Enable Blur Effect` 恢复为 `checked=false`；UI 树已核对恢复状态。

### 未覆盖

- 空聊天显示 `No conversations`，没有既有消息列表或安全的测试会话可滚动，因此未把聊天列表滚动时的背景采样标记为通过。

### iOS（Pass：范围内）

- 产物：父提供的 `RikkaHub.app` 中可执行文件 SHA-256 为 `8924f0ca2dc535ba7a9a67eb5b653eb9948dd7490b26e76bbc259ce0f41049f9`，`RikkaHub.debug.dylib` 为 `f98d5e5457453886350c61733881e6bedd882dc0829bd6b7eeecd8c3c3469cb4`，均与构建记录一致。
- 使用 XcodeBuildMCP 在 iPhone 17 Pro Max（`03C090DA-107B-4F9F-BCCD-8D5265D32820`）成功安装和启动；实际导航 **Messages → Settings → Preferences → General**，开启“启用模糊效果”，逐层返回并关闭抽屉到聊天页。可见输入栏正常绘制，未见黑块或崩溃，见 `ios-blur-enabled-chat.jpg`。
- `type_text` 对 `chat_input` 返回成功，随后使用 11 次 HID Backspace 清除测试文本；但保留截图不显示文本或键盘，故不把 iOS 输入焦点/文本可见性作为独立通过证据。
- 返回 General 点击同一开关恢复关闭，并成功停止 iOS 测试进程；没有发送消息或模型请求。

### Desktop（用户人工验证通过）

- 用户实际验证：开启模糊开关后输入框背景模糊正常。
- 本 agent 已核对本轮 Desktop 可执行文件和内嵌 JAR hash 并以隔离 profile 启动；专属窗口截图受 Stage Manager/并行 Emulator 干扰，未保留为证据。

### 用户补充的 Android / iOS 人工验证

- 用户在同轮明确确认已手动验证 Android 和 iOS，blur 均没有问题。
- 三端模糊效果均获得用户人工验证确认；这项反馈独立于上述 agent 的操作和截图覆盖范围。

## 未覆盖与清理

已清除 Android 的未发送 `HazeGUI` 文本和 iOS 的测试文本，恢复两个平台的背景模糊开关为初始关闭；没有发送消息、调用模型、创建会话或修改其他设置。已停止本 agent 启动的 Android、iOS 和隔离 Desktop 进程；Android `pidof` 与测试 Desktop 进程检查均为空，隔离 Desktop profile 已删除。保留本目录的 Android 截图、一张范围受限的 iOS 聊天输入栏截图与本文作为证据，其他中间文件不作为提交证据。
