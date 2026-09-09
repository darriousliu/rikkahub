# StatsRepository 回退：Desktop GUI 验证

日期：2026-09-10（Asia/Shanghai）。结果：**Pass**。

本次验证覆盖 CMP 第 06 项中 `StatsRepository`、`StatsQueries`、`RoomStatsQueries` 移除后，Desktop 端改为直接创建 `StatsVM(get(), get(), get())` 的实际 Koin 和统计数据接线。没有运行 Gradle/Xcode 构建、没有改生产或测试代码、没有读取钥匙串，也没有发起模型请求。

## 执行环境与来源

- Agent ID：`01a08758-4beb-78e0-a378-ae02fcd5b64f`；父 agent 创建的 Desktop 模型许可记录为 `gpt-5.6-terra / high`。
- 原始包：`desktopApp/build/compose/binaries/main/app/RikkaHub.app`；未修改 bundle、`Info.plist` 或签名。
- 已核对的本次产物：`Contents/app/composeApp-jvm-8fb8b37f79e5f076c0c9fc5eb77485ea.jar`，SHA-256 为 `efaac0c35cf62209c8f3efe53da996b96ef2698820cb5cecb36c7f4da648dbc6`，与 `/tmp/cmp-rollback-06-tools/builds.json` 一致；清理后再次核对仍一致。
- 每次以前台 TTY 运行原包 `Contents/MacOS/RikkaHub`，仅设置 `JAVA_TOOL_OPTIONS=-Duser.home=/private/tmp/rikkahub-cmp-rollback-06-desktop-home`。启动 PID/TTY 依次为 `73516/ttys006`（基线）、`74344/ttys006`（植入）、`75116/ttys006`（冷启动）、`75973/ttys006`（清理后确认）。每个 PID 均正常退出；没有终止其他 RikkaHub 实例。
- 用 `lsof` 确认 PID 73516 打开的数据库正是专用 profile 下 `.rikkahub/database/rikka_hub.db`。GUI 由 CUA 操作；截图前均以精确 PID 查询 `CGWindowList` 的 layer-0 `RikkaHub` 窗口号后执行 `screencapture -l`，并已实际查看，截图没有账户信息。

## 过程与结果

| 步骤 | 预期 | 实际结果 |
| --- | --- | --- |
| 基线 | 正常进入“统计”，无永久 loading 或 DI 异常；记录原有数据 | Pass。Koin 启动 73 个定义；实际页面显示热力图、总对话/消息、输入/输出和启动次数卡片，均为 0。基线快照为会话 0、消息 0、prompt 0、completion 0、cached 0，`launch_count=0`。 |
| 离线植入 | 应保留其他行，新增 3 专用会话、5 节点、6 消息；增量为会话 +3、消息 +6、prompt +124、completion +74、cached +28；最近两日用户消息为 2、1 | Pass。停止 PID 73516 后，`stats_fixture.py seed --platform desktop` 生成专用 manifest；IDs 为 `2bcfb326-ac3b-5606-9879-dbc3fdbccfc9`、`b86efc9a-89a2-5574-be67-b50c44b0d845`、`64499478-4b82-5e4a-933e-d18f0c62efdb`。helper 对增量与无关行指纹均已断言。 |
| 植入后 GUI | 统计真实显示总对话 3、总消息 6、输入 124、输出 74、缓存 28；热力图有两格且深浅不同 | Pass。CUA 可访问树显示所有值和“缓存节省 Token”；实际截图显示 3、6、124、74 以及热力图右侧两格不同深浅。 |
| 页面往返 | 返回其他页面再进入，数据不变 | Pass。返回聊天再进入统计后，可访问树再次为 3、6、124、74、28 和启动次数 0。 |
| 冷启动 | 完全退出后启动同一 profile，数据和热力图保持；Desktop 启动次数与 preferences 一致 | Pass。PID 74344 正常退出后，PID 75116 冷启动；实际 GUI 与可访问树仍显示 3、6、124、74、28、两格热力图。preferences 仅读取 `launch_count`，值为 0，与 GUI 一致；Desktop 旧逻辑不递增该值。 |
| 严格清理 | 仅删除 helper 专用数据，无关行不变，统计回到基线，完整性/外键通过 | Pass。停止 PID 75116 后，`stats_fixture.py cleanup` 返回 `cleanup: PASS`、`unrelated_rows: unchanged`，快照回到 0/0/0/0/0；helper 已通过 `integrity_check` 和 `foreign_key_check`。之后 PID 75973 的真实统计页也显示全零，且不再有“缓存节省 Token”卡片。 |

## 关键截图

- [植入数据后的统计页面](gui-desktop-seeded-statistics.png)：真实页面的 3、6、124、74 与两格不同深浅的热力图。
- [冷启动后的统计页面](gui-desktop-cold-start.png)：完整退出、冷启动后仍为相同统计值与热力图。

## 清理与覆盖边界

最后一个测试 PID 已正常退出。已删除专用 profile `/private/tmp/rikkahub-cmp-rollback-06-desktop-home`、专用 manifest 和本轮临时 CoreGraphics 窗口工具；未删除共享 helper、`builds.json` 或模型标记。仅保留本报告和两张关键截图。

本验证覆盖本次 Desktop 原始产物的启动、Koin/StatsVM 数据接线、统计卡片、缓存卡片条件显示、热力图、页面重入、冷启动持久化和严格清理。它不覆盖 Android/iOS GUI、真实模型生成、附件、并发/取消/异常路径，亦不替代父 agent 已完成的单元测试与多平台编译证据。
