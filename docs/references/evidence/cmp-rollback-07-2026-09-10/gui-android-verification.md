# Android GUI 验证：共享 RikkaHubApp 演示外壳回退（CMP rollback 07，重试通过）

日期：2026-09-10（Asia/Shanghai）。目标设备为 `emulator-5554`（Pixel_10_Pro_XL，1344×2992），包名
为 `me.rerere.rikkahub.debug`。本轮门控文件
`/tmp/cmp-rollback-07-tools/model-android-retry.ok` 记录的实际执行上下文是
`gpt-5.6-terra` / `high`，agent ID 为 `01a08775-b68b-7a43-801d-50c1722f2da9`。

此前记录的是设备未连接时的**未执行阻塞**；本次在已启动并确认 `sys.boot_completed=1` 的同一 AVD 上重试，以下
GUI 步骤均实际完成并通过。

## 产物、隔离与方法

- 验证 APK：`app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`。
- SHA-256：`f975d92fbf24506356ffa652cf674c97c5e3b0f1ae518b79623890a2d15a8d71`，实际
  `shasum -a 256` 与 `/tmp/cmp-rollback-07-tools/builds.json` 完全一致。
- 使用 `adb install -r` 安装，保留设备既有 profile 数据；未卸载、清空数据、写数据库或 preferences，未发送
  消息或发起模型请求。
- 仅用 ADB 的 framebuffer 截图、uiautomator 可见 UI 树和 Android 输入事件；没有操作 Mac 鼠标或键盘。菜单
  坐标由当轮 UI 树取得，未沿用旧坐标。所有保留截图都已实际查看，且不包含账号或凭据。

## 实际结果

1. 正常启动后，实际 framebuffer 显示正式 `New Chat` / `Default Assistant / Auto (RikkaHub)` 聊天入口和既有主题。
   UI 树中没有 `Status` 或 `Capabilities` 演示项，屏幕上也没有空白页、DI/Koin 异常或错误提示。
2. 从新鲜抽屉树的 `content-desc="统计数据"`，实际 bounds 为 `[522,2794][582,2854]`，进入
   **Statistics**。页面显示聊天热力图及统计卡片（会话 0、消息 0、输入 0、输出 0），无加载卡死或异常。
   系统返回由 Statistics 回到抽屉；界面可见返回按钮也回到抽屉，关闭抽屉后恢复聊天页。
3. 通过抽屉实际进入 `Assistant Settings → Default Assistant → Basic Settings`。Basic Settings 可见
   Assistant Name、Workspace、Chat Model 等设置项；没有修改或保存。连续三次系统返回依次回到助手详情、助手
   设置列表和聊天根页；额外一次根页系统返回才离开应用至 Android 桌面，随后重新启动继续验证。
4. 确认聊天输入框为空后输入未发送 ASCII 草稿 `CMP07-android`。从新鲜抽屉再次进入 Statistics，使用实际界面
   返回按钮回抽屉并关闭抽屉，聊天页的 `EditText` 和截图均仍显示该草稿，证明该 navigation entry 的 ChatVM
   输入状态被保留。草稿未发送；聚焦输入框后用 Android `Ctrl+A`/Delete 清除，UI 树确认标记消失且输入框
   `text=""`。
5. `am force-stop me.rerere.rikkahub.debug` 后以同一 profile 冷启动，`topResumedActivity` 为
   `me.rerere.rikkahub.debug/me.rerere.rikkahub.RouteActivity`。新 framebuffer 再次显示正式 `New Chat` 入口，
   不含测试草稿。抽屉可见 `No conversations`；冷启动后再进 Statistics，GUI 中 Total Conversations、Total
   Messages、Input Tokens、Output Tokens 均为 **0**，因此未发送草稿没有新增会话或消息。

首次草稿往返时曾在 Statistics 转场动画尚未结束时立即发出系统返回，结果帧仍是 Statistics；该次不作为通过依据。
等待实际转场后改用可见返回按钮完成往返，并保留了最终聊天草稿帧。该现象仅限制那一次过早输入的结论，不影响已完成的
有效导航检查。

## 保留的 GUI 证据

- `gui-android-statistics.png`：首次进入 Statistics 的正式统计页。
- `gui-android-draft-return.png`：从 Statistics 返回聊天后，未发送 `CMP07-android` 草稿仍可见。
- `gui-android-cold-chat.png`：force-stop 后的正式聊天冷启动入口，输入框为空。
- `gui-android-cold-statistics.png`：冷启动后 Statistics 的会话/消息/输入/输出均为 0。

## 清理与覆盖边界

已 force-stop 本任务启动的应用实例，删除所有设备端 `cmp07-android-retry-*` XML/PNG 和本 agent 的
`/tmp/cmp07-android-retry.*` 临时目录；仅保留上述四张关键截图与本报告。没有停止或关闭父 agent 拥有的模拟器，
没有改动生产代码、测试、Gradle、Xcode、Git 或父文档。

本次覆盖 Android 端正式 Composition 宿主的冷启动、Statistics 和设置导航、系统/界面返回层级、草稿跨导航保存及
未发送草稿的冷启动结果。未覆盖真实模型服务、已存在会话内容、配置变更、分享/深链和其他平台。

## 本 agent 修改的文件

- `docs/references/evidence/cmp-rollback-07-2026-09-10/gui-android-verification.md`
- `docs/references/evidence/cmp-rollback-07-2026-09-10/gui-android-statistics.png`
- `docs/references/evidence/cmp-rollback-07-2026-09-10/gui-android-draft-return.png`
- `docs/references/evidence/cmp-rollback-07-2026-09-10/gui-android-cold-chat.png`
- `docs/references/evidence/cmp-rollback-07-2026-09-10/gui-android-cold-statistics.png`
