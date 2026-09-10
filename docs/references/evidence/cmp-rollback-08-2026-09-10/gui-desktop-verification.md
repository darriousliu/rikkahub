# CMP rollback 08 — desktop GUI verification

- 日期：2026-09-10（Asia/Shanghai）
- 平台：macOS desktop，正式 bundle `desktopApp/build/compose/binaries/main/app/RikkaHub.app`
- agent：`01a08793-17c4-7bf1-b788-0e4ace6c1d61`，`gpt-5.6-terra` / `high`
- 证据轮次标记：`/tmp/cmp-rollback-08-tools/model-desktop-evidence.ok`；其中 desktop contexts 均为上述模型和 effort。
- 构建产物：`Contents/app/composeApp-jvm-bbfd681646b6cccb6274383a7821977.jar`
- SHA-256：`511e9817ccf2210dcb7361694b827c426a7643df8340b844502ec2370d14e16c`，补充轮次前后均与 `/tmp/cmp-rollback-08-tools/builds.json` 一致。

## 默认值勘误

先前报告将输入框显示的 placeholder 误作存储默认值，并把 `https://example.com/dav`、`https://s3.amazonaws.com` 和 `my-bucket` 写入独立 profile；这不是恢复真实默认值。现已按源码复核并更正：

- `WebDavConfig.url` 的默认值是空字符串，`path` 的默认值是 `rikkahub_backups`：`composeApp/src/commonMain/kotlin/me/rerere/rikkahub/data/datastore/SettingsModels.kt`。
- `S3Config.endpoint` 和 `bucket` 的默认值均为空字符串，`region` 为 `auto`，`pathStyle` 默认 `true`：`composeApp/src/commonMain/kotlin/me/rerere/rikkahub/data/sync/s3/S3Config.kt`。
- 上述三个 URL/bucket 文本只是 `WebDavTab.kt` / `S3Tab.kt` 的 `OutlinedTextField` placeholder。

首次轮次使用的是全新独立 profile，未包含用户数据。其被删除后无法再在该 profile 内更正字段；删除本身完成了清理。先前报告中“已恢复为默认值”的说法已撤回。首次轮次退出时的缓冲日志显示示例端点 `PROPFIND 405`、无 access key 的 S3 列表 `400`，以及一行空路径 `mkcol success:`；无法从缓冲日志可靠归因该行的时点，因此这些日志不作为通过证据，也不在补充轮次中复现。

## 补充 GUI 证据轮次

补充轮次使用全新 profile `/private/tmp/rikkahub-cmp-rollback-08-desktop-evidence-home`，以原始 bundle 的前台 TTY 启动，唯一的 `JAVA_TOOL_OPTIONS` 为该 profile 的 `-Duser.home`。首次进程 PID 为 `11184`，同 profile 冷启动 PID 为 `16058`。

通过 GUI 填入固定只读测试端，未改变“聊天记录”和“文件”两个备份项：

- WebDAV：`http://127.0.0.1:18768/dav/`、用户名 `cmp08`、密码 `test-only`、路径 `cmp08-desktop`；“测试连接”显示成功。
- S3：endpoint `http://127.0.0.1:18768`、access key `cmp08`、secret `test-only`、bucket `cmp08-desktop`、region `auto`、path-style 保持启用；“测试连接”显示成功。

两个协议都通过顶层“恢复”入口打开只读文件列表；未点任一列表项的“恢复”或“删除”，也未点“立即备份”。代码阅读确认该顶层入口调用列表加载，列表中的“恢复”按钮才会执行恢复。

两个列表均恰好显示以下顺序：

1. `backup_cmp08_new.zip` — 2026-09-10 09:00:00，2.00 KB
2. `backup_cmp08_old.zip` — 2026-09-09 09:00:00，1.00 KB

停止 PID `11184` 后使用同一 profile 冷启动 PID `16058`。冷启动后，WebDAV 的测试 URL、路径与文件计数 `2` 仍在；重新打开列表仍显示同样两项和顺序。

## 可审阅截图

以下均为实际 RikkaHub 窗口帧，已用 `view_image` 逐张复核。截图仅含公开测试值，密码保持掩码；未保留包含桌面其他窗口的初始过宽裁切帧。

- `gui-desktop-webdav-list.png`：WebDAV 列表，两个文件、日期、大小和新到旧顺序。
- `gui-desktop-s3-list.png`：S3 列表，两个文件、日期、大小和新到旧顺序。
- `gui-desktop-cold-start-webdav-list.png`：相同 profile 冷启动后的 WebDAV 列表和配置背景，确认持久化后仍为两个预期文件。

## 清理与范围

- 补充轮次结束后直接退出 PID `16058` 并删除 evidence profile；无需尝试将测试字段写回真实默认值。已确认 `/private/tmp/rikkahub-cmp-rollback-08-desktop-evidence-home` 不存在。
- 首次轮次的 `/private/tmp/rikkahub-cmp-rollback-08-desktop-home` 也已删除。两者均为新建独立 profile，未读取钥匙串或既有用户配置。
- 未停止固定测试服务或模拟器；未修改生产代码、测试、Gradle、Xcode 或父文档。补充轮次未执行实际备份、列表项恢复、删除、PUT、DELETE 或 MKCOL；首次轮次的空路径 `mkcol success:` 日志已如上保留为未能归因的边界。
