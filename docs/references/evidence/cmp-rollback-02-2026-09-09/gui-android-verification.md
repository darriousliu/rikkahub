# Android GUI 验证：FolderRepository 回退（CMP rollback 02）

日期：2026-09-09（Asia/Shanghai）。执行范围仅为 `emulator-5554`（Pixel 10 Pro XL API 37，arm64）；全程通过 ADB/uiautomator/screencap 操作，未使用 Mac 全局鼠标或键盘，也没有发起模型请求或读取凭据。

## 产物与隔离

- 已使用父 agent 提供的 `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`。
- 实测 SHA-256：`25f08ea44d9bb27f132f61717aceab38e738bd3b6f637b5e03c13ffa8248c371`，与 `verification.md` 中本轮构建记录一致。
- 调试包安装前不存在，故应用数据区为本轮专用。为避免模型请求，暂停应用后仅向 Room 数据库写入两条未归类的专用会话种子：`CMP-Folder02-Android-Target-Session` 与 `CMP-Folder02-Android-Control-Session`。所有文件夹创建、重命名、会话移动和文件夹删除均由 GUI 完成。
- 两条种子仅含标题和元数据，`messageNodes` 为空。GUI 验证会话归属和列表持久化；消息正文/分支保留由真实 Room JVM 测试覆盖。

## 实际 GUI 步骤与观察

1. 冷启动成功并可打开会话抽屉；两条专用会话显示在未归类列表中。见 `gui-android-seeded-drawer.png`。
2. 从抽屉的 **New** 创建 `CMP-Folder02-Android-Target` 和 `CMP-Folder02-Android-Control`，两者均显示在文件夹栏。见 `gui-android-folders-created.png`。
3. 长按目标文件夹，选择 **Rename**。通过设备的实际 `Ctrl+A` 组合键清空文本框，再输入 `CMP-Folder02-Android-Target-Renamed`；uiautomator 在保存前确认输入框内容完全一致。保存后 GUI 显示重命名结果。
4. 长按对照会话，选择 **Move to Folder**，在底部表单选择对照文件夹；它从未归类列表消失。随后对目标会话执行相同 GUI 操作，选择已重命名的目标文件夹；进入该文件夹后可见目标会话。见 `gui-android-target-folder-view.png`。
5. 长按目标文件夹并选择 **Delete**。确认对话框明确显示“会话不会被删除，只会从文件夹移除”；实际点击 **Delete**。随后目标文件夹不再出现，目标会话回到未归类列表。见 `gui-android-delete-confirm.png` 和 `gui-android-delete-result.png`。
6. 进入保留的对照文件夹，仍可见对照会话，证明无关文件夹/会话未受影响。见 `gui-android-control-folder-view.png`。
7. 强制停止后冷启动并重新进入抽屉：目标会话仍显示为未归类，对照文件夹仍显示；本次启动的 logcat 未匹配 `FATAL EXCEPTION`、`NoDefinitionFound`、Koin 异常或 FolderRepository 异常。见 `gui-android-restart-drawer.png`。

## 结果

通过。Android 模拟器 GUI 覆盖了 Android `RepositoryModule` 实际依赖解析以及 FolderRepository 的创建、重命名、移动、删除、删除后会话保留/清空归属、无关数据保留和冷启动持久化行为。

## 清理

验证后应用已停止。通过已停止的隔离应用数据库删除了严格匹配 `CMP-Folder02-Android-*` 的两条专用会话和一条对照文件夹；回读结果为会话 `0`、文件夹 `0`，`PRAGMA integrity_check` 为 `ok`。设备端 uiautomator XML 与本地临时数据库/中间截图已清理，仅保留本摘要和关键 PNG 证据。
