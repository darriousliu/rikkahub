# Android GUI 验证：StatsRepository 回退（CMP rollback 06）

日期：2026-09-10（Asia/Shanghai）。执行目标仅为 `emulator-5554`（Pixel_10_Pro_XL，arm64）；全程使用 ADB、uiautomator 和 screencap，未使用 Mac 全局鼠标/键盘，未发起真实模型请求，未读取钥匙串。本记录的实际执行上下文为 `gpt-5.6-terra` / `high`，agent ID：`01a08758-4ac9-73d0-8b0a-ded4ae216eda`。

## 产物与隔离

- 验证 APK：[app-arm64-v8a-debug.apk](../../../../app/build/outputs/apk/debug/app-arm64-v8a-debug.apk)
- SHA-256：`942d57d609d4d5a62e0d40abfcf9da0af70a648abf1375e8fc6cc62263002244`，与 `/tmp/cmp-rollback-06-tools/builds.json` 的 Android 记录一致。
- 通过 `adb install -r` 保留已有 profile 数据；未清空模拟器、未卸载应用。
- 离线 fixture 在应用完全停止后，连同主库、WAL 和 SHM 复制数据库；helper 关闭最后一个 SQLite connection 后正常 checkpoint 并移除了本地 WAL/SHM。写回时使用 `adb exec-in` 的字节 stdin 与 `run-as` 应用 UID，并精确删除设备端旧 WAL/SHM，使 SQLite 自行重建；首次副本的 WAL 为 0 字节，SHM 为 32 KiB。

## 实际步骤与结果

1. 安装并启动本轮 APK，从实际菜单进入 **Statistics**。页面加载结束，没有永久 loading 或可见 DI/Koin 异常。基线可见值：会话 `0`、消息 `0`、输入 `0`、输出 `0`、启动次数 `5`，缓存卡片未显示。[基线截图](gui-android-baseline.png)
2. 完全停止应用后，按唯一 manifest 播种 3 条专用会话、5 个节点和 6 条消息（含未选分支）。helper 的基线为 `0/0/0/0`；播种结果为会话 `3`、消息 `6`、prompt `124`、completion `74`、cached `28`。用户消息日期为 `2026-09-08` 的 2 条和 `2026-09-07` 的 1 条；无关行 fingerprint 未变化。
3. 同一 profile 启动并实际打开 Statistics。可见卡片分别为 `3`、`6`、`124`、`74`、`28`，缓存卡片出现；热力图 9 月位置可见两格且深浅不同。首次 Android 冷启动的可见启动次数为 `6`。[种子页截图](gui-android-seeded-top.png)
4. 返回更新详情页后，经导航抽屉的实际“统计数据”菜单重新进入 Statistics；可见统计值、缓存卡片和热力图均保持一致。[复进截图](gui-android-reenter.png)
5. 完整停止并第二次冷启动后重新进入 Statistics，仍显示 `3/6/124/74/28` 和相同的两格热力图；启动次数显示 `7`。停止后复制 preferences，仅以 helper 输出 `{"launch_count": 7}`，与可见值一致。[冷启动截图](gui-android-cold-restart.png)
6. 再次停止应用后，helper 按 manifest 的确定 ID 清理，输出 `cleanup: PASS`、`unrelated_rows: unchanged`，统计恢复为 `0/0/0/0`。helper 同时断言 `PRAGMA integrity_check = ok` 且 `foreign_key_check` 为空。写回后再次打开 Statistics，确认 GUI 基线为会话/消息/输入/输出 `0`、热力图为空、缓存卡片消失；该最终确认冷启动使启动次数正常成为 `8`。[清理后截图](gui-android-cleanup.png)

## 清理与覆盖边界

已停止本任务启动的应用实例，删除专用的设备端截图/XML、应用私有 `files/cmp-rollback-06`、本地数据库副本、manifest 与临时 profile 文件；仅保留上述关键截图和本记录。共享 helper、构建记录和模型标记未删除。

本次真实 GUI 覆盖 Android 端 Koin 解析 `StatsVM(get(), get(), get())`、StatsVM 的 Room 统计加载、热力图日期数据、缓存卡片条件显示、页面复进和冷启动持久化。未运行 Gradle/Xcode 构建或测试，未修改生产/测试代码，也未覆盖其他平台；代码等价和跨平台构建证据由父任务另行维护。
