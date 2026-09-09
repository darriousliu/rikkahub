# CMP 回退 07 — iOS GUI 验证

日期：2026-09-10

## 环境与产物

- 模拟器：`iPhone17Pro`，UDID `469B364C-382C-4068-A012-3CD026296BAE`；未操作 `03C090DA…`。
- Bundle ID：`me.rerere.rikkahub.ios`。使用父任务提供的 `.app` 安装到保留的同一 profile；未重建、未读取钥匙串、未发起真实模型请求。
- 验证 agent：`01a08770-b042-77f0-b970-e49c4e502f1c`；模型 `gpt-5.6-terra`，推理强度 `high`。门控文件 `/tmp/cmp-rollback-07-tools/model-ios.ok` 已确认后才安装、启动和操作 GUI。
- 实际 iOS 产物：`RikkaHub.debug.dylib`，SHA-256 `2d43f3dc086e7d2daea8e2f8e4afb024e5aa18a991def48279c33a6564d71526`；与 `/tmp/cmp-rollback-07-tools/builds.json` 的同一路径条目一致。

## 验证结果

1. 正常入口启动后，实际可见页面为原正式聊天界面：标题“新聊天”、默认助手、聊天抽屉和消息输入框均可见；主题正常，没有 Status/Capabilities 演示底栏或侧栏、空白页、DI/Koin 错误。截图见 [gui-ios-final-cold-start.jpg](gui-ios-final-cold-start.jpg)。
2. 从实际聊天抽屉的柱状图入口进入“统计”。语义树和实际可见截图均显示原统计页、聊天热力图以及总对话数 0、总消息数 0、输入 Token 0、输出 Token 0、应用启动次数 0，见 [gui-ios-statistics.jpg](gui-ios-statistics.jpg)。返回后回到原聊天 entry。
3. 从抽屉设置入口进入“偏好设置”再进入无副作用的“界面偏好设置”；全程未点选任何开关。逐层返回“偏好设置”→“设置”→原聊天，层级正确。
4. 该聊天为空，先聚焦输入框，再输入短 ASCII 标记 `CMP07-ios`，未点击发送。再次从抽屉进入统计后，统计仍为总对话数/总消息数 `0/0`；返回原聊天 entry 后，运行时语义树的 `chat_input` 值仍为 `CMP07-ios`，并在实际可见截图中确认，见 [gui-ios-draft-return.jpg](gui-ios-draft-return.jpg)。随后通过当前 iOS 键盘的删除键删除 9 个字符，最新语义树恢复输入框占位文本；未发送、未长按发送。
5. 应用完整停止成功后，以同一 profile 冷启动，两次新进程 PID 分别为 `84873` 和最终留证的 `85636`；新鲜运行时树显示正式聊天入口和空输入框，而非前一帧缓存。最终截图为 [gui-ios-final-cold-start.jpg](gui-ios-final-cold-start.jpg)。最后已停止测试实例。

## 证据范围与清理

- 可见截图已逐张实际查看，均不含账号或凭据。它们证明正式聊天、统计可见卡片和草稿返回；运行时语义树补充证明导航节点、统计数值和 `chat_input` 精确值。语义树不被当作离屏 UI 的可见性证明。
- 仅使用指定 iOS 模拟器；未操作用户设置，未发送消息，因此没有创建会话、消息或模型请求。统计页 `0/0` 是未发送草稿未增加持久化数据的运行时证据。
- 已删除本人未保留的中间截图；保留上述三张关键截图与本文。没有创建 XML、数据库 fixture 或专用 `/tmp` 文件。XcodeBuildMCP 没有删除 named profile 的 API，已清空 `cmp-rollback-07-ios` 的全部默认值并切回全局默认 profile；测试实例已停止。

## 本 agent 修改文件

- `docs/references/evidence/cmp-rollback-07-2026-09-10/gui-ios-validation.md`
- `docs/references/evidence/cmp-rollback-07-2026-09-10/gui-ios-statistics.jpg`
- `docs/references/evidence/cmp-rollback-07-2026-09-10/gui-ios-draft-return.jpg`
- `docs/references/evidence/cmp-rollback-07-2026-09-10/gui-ios-final-cold-start.jpg`
