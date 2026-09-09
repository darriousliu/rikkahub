# FolderRepository 最小回退：Desktop GUI 验证

日期：2026-09-09；有效 GUI 操作约 23:40–23:56（Asia/Shanghai）。

功能结果：**Pass，限定为两条空会话**。GUI 完成创建、精确重命名、分别归类、删除目标、
目标会话返回未归类、对照保留，以及正常退出后的冷启动持久化复验。

执行模型说明：恢复后的实际执行上下文为 `gpt-6-astra / max`，此前锁屏诊断为 `gpt-5.6-terra / medium`；
父 agent 已复核配置，本次补验不能标注为 Terra 执行。模型偏差记录见 [总验证记录](verification.md)。
此前锁屏期间的尝试没有形成通过证据，本报告保留其简述并替代原 `gui-desktop-blocked.md`。

## 构建来源与隔离

使用父 agent 提供的原始包：
`/Users/liuzhenhui/AndroidStudioProjects/rikkahub/desktopApp/build/compose/binaries/main/app/RikkaHub.app`。
父 agent 提供的来源为 `c64962ed` 工作树加待提交的 FolderRepository 最小回退和对应 Koin 绑定，
不是仅凭 `c64962ed` 提交即可复现的产物。本任务没有重新构建、运行单元测试或修改该包、plist、签名。

以下 SHA-256 在本轮 GUI 前后各计算一次，完全一致。路径相对于包内 `Contents/app/`：

| 产物 | SHA-256 |
| --- | --- |
| `composeApp-jvm-3a7e8dfb71575f33b1c8af6e76c17d7.jar` | `12e5c6107cd7acf7bde5ca468ff128e8529bb34d5ba31a12cdc848c3a85870d7` |
| `desktopApp-jvm-699c9a389278b6a1c47d1ef19efd79f.jar` | `7b4f5d6654cbab7dea2375324b8abd47d1a03dd8823ac7e27e6caeb9ee007ae5` |

每次均以前台 TTY 直接执行 `Contents/MacOS/RikkaHub`，设置
`JAVA_TOOL_OPTIONS=-Duser.home=/private/tmp/rikkahub-cmp-rollback-02-desktop-home`，不使用后台 `&`。
用启动 PID、`lsof` 确认其持有该 home 下 `.rikkahub/database/rikka_hub.db`，再按相同 PID 查询 AX 和 CGWindowList。

| 阶段 | PID / TTY session | 主窗口号 | 退出结果 |
| --- | --- | --- | --- |
| GUI 创建、重命名 | `49254` / `43462` | `31148` | GUI 关闭；退出码 0 |
| 种子后启动、GUI 归类与删除 | `50239` / `2135` | `31208` | GUI 关闭；退出码 0 |
| 冷启动持久化复验 | `51372` / `93126` | `31278` | GUI 关闭；退出码 0 |

三个实例均展示正常的 800×600 逻辑点主窗口。先用精确 PID 的原生 AX 打开侧栏，
再比对 CUA 状态与该 PID 的 AX 状态、专用会话标题；CUA 重连后才进行后续操作。
没有关闭其他来源的 RikkaHub 进程。

## 测试数据

两个文件夹完全由 GUI 创建，目标由 GUI 重命名：

- 目标：`CMP-Folder02-Desktop` → `CMP-Folder02-Desktop-Renamed`，
  ID `f702e3f0-1552-406b-b64c-876bd6f8a7b0`。
- 对照：`Keep`，ID `70bfb393-8c85-4407-a588-729d7f17d820`。

第一次正常退出且 PID 消失后，按用户授权仅向 `ConversationEntity` 插入两条空会话。
种子没有修改文件夹表或归属字段，两条会话初始 `folder_id=''`：

| 专用会话 | ID | create_at / update_at |
| --- | --- | --- |
| `CMP02 Desktop Target` | `02d00000-0000-4000-8000-000000000001` | `1788969000000` |
| `CMP02 Desktop Keep` | `02d00000-0000-4000-8000-000000000002` | `1788969000001` |

共用默认助手 `0950e2dc-9bd5-4801-afa3-aa887aa36b4e`；`nodes`、`suggestions`、
`mode_injection_ids`、`lorebook_ids` 均为 `[]`，`is_pinned=0`，其余可选文本为空。
`message_node` 为 0 条。没有读取凭据或发送模型请求。

## GUI 操作、预期与结果

| 步骤 | 操作和预期 | 实际观察 |
| --- | --- | --- |
| 1. 创建 | 侧栏“新建”分别输入目标名称与 `Keep`，保存；两者均可选 | Pass；AX 展示两个名称 |
| 2. 精确重命名 | 长按目标 → 重命名，全选旧值，输入完整新名称并保存 | Pass；AX 为 `CMP-Folder02-Desktop-Renamed`，重启后的选择器仍显示完整新名称与 Keep |
| 3. 加载种子 | 正常退出，离线种子，启动同一 profile | Pass；“聊天”列表同时出现 Target 与 Keep 两条专用会话 |
| 4. 归类目标 | 长按 Target → 移动到文件夹 → 完整新名称 | Pass；Target 从“聊天”消失；选中目标文件夹后仅显示 Target |
| 5. 归类对照 | 在“聊天”长按 Keep 会话 → 移动到文件夹 → Keep | Pass；“聊天”显示“没有对话记录”；选择 Keep 后仅显示对照会话 |
| 6. 删除目标 | 选中目标并确认其含 Target；长按文件夹 → 删除 → 确认 | Pass；确认框完整显示目标名称，说明对话不会删除；确认后目标标签消失，自动返回“聊天”，Target 出现 |
| 7. 对照保留 | 删除后选中 Keep | Pass；Keep 标签和唯一的 `CMP02 Desktop Keep` 保持，Target 不在 Keep 中 |
| 8. 冷启动 | 正常退出 PID 50239，启动同一 profile 的 PID 51372，分别查看“聊天”和 Keep | Pass；“聊天”仅有 Target，Keep 仅有对照会话，已删除目标没有复现 |

CUA 完成点击、输入、选择和确认。CUA 未提供鼠标按住接口，长按通过临时原生 GUI 工具完成：
只使用已经观察到的 AX/截图坐标，操作前确认前台进程等于自己的 PID。
目标名称使 Keep 位于标签栏右侧时，用相同方式发送一次水平滚动显示 Keep，核对后滚回目标。
早期右键、CUA 水平滚动和拖动没有完成所需动作，未将这些尝试记为通过；最终每项均观察到了实际结果。
文件夹创建、重命名、会话归类和删除均未由数据库写入替代。

冷启动日志显示 Koin 成功启动 75 个定义；侧栏和文件夹操作在真实桌面入口工作。
这是本场景的实际 DI/数据流验证，不外推为所有依赖或平台功能均已验证。

## 关键证据

五张截图均按上述已确认 PID 所属的精确窗口号，用 `screencapture -l` 获取；已逐张查看。

- [完整重命名结果和 Keep 选择器](gui-desktop-renamed.png)
- [删除目标确认框及保留对话提示](gui-desktop-delete-confirm.png)
- [删除后 Target 自动返回聊天](gui-desktop-target-returned.png)
- [冷启动后 Target 保持未归类](gui-desktop-restart-target.png)
- [冷启动后 Keep 及其对照会话保留](gui-desktop-restart-keep.png)

正常关闭最后一个测试实例后，使用 SQLite `-readonly` 做辅助核对，
[离线检查输出](gui-desktop-offline-check.txt) 全部通过：

- 恰好两条会话；Target 的 13 个字段与种子完全一致，`folder_id=''`。
- Keep 会话的 13 个字段匹配预期：仅归属为 GUI 创建的 Keep，其余字段与种子一致。
- 仅 Keep 文件夹保留，其 ID、assistant_id、名称、排序和创建时间 5 个字段未变；目标 ID 已不存在。
- 消息节点仍为 0；`integrity_check=ok`，无外键违规。

数据库检查只用于补充 GUI 已观察到的结果，未作为 GUI 行为的替代证据。

## 覆盖边界

覆盖本次包的 Desktop 文件夹列表、创建、重命名、空会话归类与删除后归还、对照保留、
持久化和冷启动实际 DI 接线。
空会话只能证明会话记录及这些元数据保留；未覆盖非空消息、分支、附件、模型生成、
并发/取消/异常、其他助手或 Android/iOS GUI。父 agent 报告的 187 项 JVM 测试及各目标构建
未在本任务重跑，也不由本报告代替。

## 此前锁屏诊断与清理

此前只读查询得到 `CGSSessionScreenIsLocked=1`，同时出现 CUA ScreenCaptureKit `-3811`、
本任务 PID 41956 的 `CVDisplayLink -6661`，以及不可截图的 113×117 窗口。
未确认归属的旧实例截图已丢弃，未计为产品证据；该次测试 TTY 退出码 130。
用户通知解锁后，本轮只读查询 `screenLocked=false`，随后正常窗口、AX 和截图均恢复。
没有修改 TCC 或系统权限。此前曾修改临时拷贝包的 plist，后按用户纠正丢弃；该副本未用于有效验证。
本轮使用的原始包、plist 和签名未修改。

2026-09-09 23:56:53 CST 确认本轮三个已知测试 PID 均不在进程表中，随后删除并确认不存在：

- `/private/tmp/rikkahub-cmp-rollback-02-desktop-home`：包含两条空会话、剩余 Keep、数据库和设置的整个隔离 profile。
- `/private/tmp/cmp-folder02-desktop-tools.jnJqs5`：临时原生 AX/鼠标工具、源码及 seed/verify SQL。

此前临时副本 `RikkaHub-CmpRollback02.app`、launcher 日志和临时窗口截图也确认不存在。
专用测试数据已清除；保留本报告、五张关键截图和离线检查输出。
本轮仅改本项 `gui-desktop-*` 证据文件；未修改生产、测试或 Xcode，未执行 Git 写操作，提交由父 agent 处理。
