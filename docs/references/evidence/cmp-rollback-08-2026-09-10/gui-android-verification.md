# CMP rollback 08 — Android GUI verification

日期：2026-09-10（Asia/Shanghai）

设备：`emulator-5554`，Pixel_10_Pro_XL，1344×2992 headless framebuffer

包：`me.rerere.rikkahub.debug`

APK SHA-256：`84138444aca63564171802656daddf0e7ffe7ca933f11ee0777a326b6ea63819`

## 执行上下文

- 放行标记：`/tmp/cmp-rollback-08-tools/model-android-final.ok`
- GUI agent：`01a087a7-7fb2-7243-b1c1-e642f54eaaff`
- 模型：`gpt-5.6-terra`，`high`
- 父轮已通过的代码测试：282 项；三端构建已通过。本轮未修改生产代码、测试、Gradle、Xcode 设置，也未构建、提交、清数据、卸载、读钥匙串或触发 Backup Now / 列表项 Restore / Delete。

## GUI 结果

四个标签（WebDAV Backup、S3 Backup、Local、Reminder）均通过 Android GUI 打开。

WebDAV 使用 GUI 逐字段输入以下 fixture：

- URL：`http://10.0.2.2:18768/dav/`
- username：`cmp08`
- password：`test-only`
- path：`cmp08-android`

S3 使用 GUI 逐字段输入：

- endpoint：`http://10.0.2.2:18768`
- access key：`cmp08`
- secret：`test-only`
- bucket：`cmp08-android`
- region：`auto`；Path Style 保持 `true`；Chat Records 与 Files 两项保持勾选。

两协议均实际点击 Test Connection，且仅点击页面底层的顶层 Restore 来打开只读文件列表。授权后，fixture 服务记录 WebDAV `PROPFIND /dav/cmp08-android`（207，depth 0/1）和 S3 请求；两份真实列表均恰好显示：

1. `backup_cmp08_new.zip` — 2.00 KB（2048 bytes），Sep 10, 2026 9:00 AM。
2. `backup_cmp08_old.zip` — 1.00 KB（1024 bytes），Sep 9, 2026 9:00 AM。

顺序为新文件在前。`ignore.txt` 和目录均未出现在列表。

## 持久性与恢复

- 在离开并重进 Data Backup 后，两协议的测试字段仍由 UI 树显示为已保存。
- 使用同一 profile 完整 `force-stop` 后冷启动，应用正常进入主界面；重新进入 Data Backup 时，WebDAV 与 S3 测试字段仍存在。冷启动前权限缺口使列表请求超时；同一保存配置在系统 GUI 临时授权后成功加载两协议列表。
- 最终通过 GUI 恢复真实初始值，并离开、重进页面复核：WebDAV URL / username / password 为空，path 为 `rikkahub_backups`；S3 endpoint / access key / secret / bucket 为空，region 为 `auto`，Path Style 为 `true`，备份选项保留。

## 本轮权限阻塞与恢复

初始系统 App info GUI 显示 `No permissions granted`。在权限未授予时，真实应用 PID `5663` 的 Ktor/OkHttp 日志记录：

```text
ConnectTimeoutException: Connect timeout has expired
url=http://10.0.2.2:18768/dav/cmp08-android
Caused by: SocketTimeoutException: failed to connect to /10.0.2.2:18768
```

S3 的顶层 Restore 也显示过连接超时。它们是有效失败尝试，不能作为通过证据。父轮定位到 targetSdk 37 上 `ACCESS_LOCAL_NETWORK` 未授予：普通 shell `nc` 可通，但同应用 UID 的网络访问与 GUI 请求同样超时。

仅为本轮只读 fixture 验证，通过 Android 系统 GUI 的 App info → Permissions → Nearby devices 选择了 Allow；系统 UI 确认 Allow 单选被选中后，以上两协议请求与列表均成功。此操作没有修改 Manifest 或应用代码，也不构成本项的代码修复。验证完成后，仍通过同一系统 GUI 选择 Don’t allow；系统 UI 显示 `deny_radio_button checked=true`，随后只读 `dumpsys package` 确认：

```text
android.permission.ACCESS_LOCAL_NETWORK: granted=false
android.permission.NEARBY_WIFI_DEVICES: granted=false
```

## 图像证据

以下图片均在本轮由 `view_image` 实际查看：

- `gui-android-webdav-list-success.png`：成功 WebDAV 两项真实列表，SHA-256 `a9313670ad2ecebc3090615b67a16a2efffe82f4320da98e4e7a7d9632b63e73`。
- `gui-android-s3-list-success.png`：成功 S3 两项真实列表，SHA-256 `3e0b5e8245b121450b1922d465b480c3b4d58d86ebc57763b165684775658368`。
- `gui-android-cold-start.png`：同 profile force-stop 后的冷启动主界面，SHA-256 `c2c284569eabaffb57c5db8490a36e4491cdf7c249f5beb2f796ca44e6633bfc`。
- `gui-android-local-network-initial.png`：系统 GUI 的初始 `No permissions granted` 状态，SHA-256 `228d2768e04142ee12221fed131bf0d9e2fa2c70dcbc4b22be5e1e90b82e8fc5`。

旧的 `/tmp/cmp08-android-retry-backup.png` 实际为表单，不是列表，未作为通过证据。早期 Loading / timeout 图同样未作为通过证据。

## 未覆盖边界

- 未点击任一列表项 Restore / Delete，因本项只验证列表入口与内容。
- 未触发 Backup Now、写入、删除、真实账户或真实备份恢复。
- 临时授权后未再进行第二次独立 cold start；已在授权前确认同 profile 冷启动后的两协议配置持久性，并在该保存配置上完成两份列表验证。
