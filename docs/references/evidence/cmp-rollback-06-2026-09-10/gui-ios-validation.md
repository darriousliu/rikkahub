# CMP 回退 06 — iOS GUI 验证

日期：2026-09-10

## 环境与产物

- 模拟器：`iPhone17Pro`，UDID `469B364C-382C-4068-A012-3CD026296BAE`；未操作 `03C090DA…`。
- Bundle ID：`me.rerere.rikkahub.ios`。保留既有 data container，只安装并启动父任务提供的原始 `.app`。
- 验证 agent：`01a08758-4b59-7df2-921b-27a61a25ce4e`；模型 `gpt-5.6-terra`，推理强度 `high`（`/tmp/cmp-rollback-06-tools/model-ios.ok` 已核对）。
- 实际产物：`RikkaHub.debug.dylib`，SHA-256 `12de83d310cd23dc390a22b97de3d9b4db8a2aa2c309215fd0c24e6199fda479`，与 `builds.json` 的 iOS 条目一致。
- 未运行 Gradle/Xcode 构建，未读取钥匙串，未发起真实模型请求。

## 基线、种子和 GUI 结果

1. 安装并正常启动后，从 Messages 抽屉进入“统计”。页面完成加载且可见聊天热力图、总对话数 0、总消息数 0、输入 Token 0、输出 Token 0、应用启动次数 0；没有永久 loading 或 DI/Koin 异常。基线截图见 [gui-ios-baseline.jpg](gui-ios-baseline.jpg)。
2. 应用完全停止后，使用父任务提供且已 roundtrip 验证的离线 helper，在同一容器 DB 执行 `seed DB --manifest <专用 /tmp 清单> --platform ios`。helper 自身断言通过：新增 3 个专用会话、5 个节点、6 条消息（含未选分支），增量为对话 +3、消息 +6、prompt +124、completion +74、cached +28；两个日期的用户消息数为 2026-09-08 的 2 条和 2026-09-07 的 1 条。原有行指纹保持不变。
3. 同 profile 正常启动并再次从抽屉进入“统计”。实际运行时 UI 可访问树显示：总对话数 **3**、总消息数 **6**、输入 Token **124**、输出 Token **74**、缓存节省 Token **28**、应用启动次数 **0**。截图实际可见这些卡片；热力图存在两格不同深浅的活动格，见 [gui-ios-seeded-stats.jpg](gui-ios-seeded-stats.jpg)。该截图不含账户信息。
4. 返回聊天页后重新进入统计，运行时 UI 树显示的六项值保持 `3 / 6 / 124 / 74 / 28 / 0`。随后完整停止并冷启动，再进入统计，数值仍相同，见 [gui-ios-cold-start-stats.jpg](gui-ios-cold-start-stats.jpg)。iOS 的 `launch_count` 未增加，停止后按 helper 只读取该字段的 preferences 结果为 `{"launch_count": 0}`，与页面一致。
5. 再次停止应用后，使用同一专用 manifest 执行 helper cleanup。输出为 `cleanup: PASS`、`unrelated_rows: unchanged`；统计回到基线 `0 / 0 / 0 / 0 / 0`，helper 的 `integrity_check` 与 `foreign_key_check` 均通过。最后重新启动并实际进入统计页确认全 0，且 cached token 为 0 时缓存卡片按原逻辑消失；随后停止了测试实例。

## 清理与覆盖边界

- 已删除本任务的 `/tmp` manifest、临时截图源和临时 XcodeBuildMCP profile；保留的只有本目录三张无账户信息的关键截图和本文档。共享 helper、`builds.json` 与模型标记未删除，留待父任务统一清理。
- 本次覆盖 iOS 实际 Koin/StatsVM 数据接线、统计页加载、卡片数据、热力图可见活动格及深浅、离开再入、冷启动持久化和精确 fixture 清理。单个热力图格没有可访问名称，日期对应关系由固定 fixture 构造；GUI 证据只据实际可见的两格不同深浅和统计卡片，不以 SQL 查询代替 GUI 证明。
