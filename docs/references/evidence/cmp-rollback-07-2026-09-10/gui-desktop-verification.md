# CMP 第07项回退：Desktop GUI 验证

日期：2026-09-10（Asia/Shanghai）。结果：**Pass**。

本次仅验证指定 Desktop 原始应用包在移除共享 `RikkaHubApp(productContent)` 演示 Status/Capabilities 外壳及 `SharedEntryTestTags` 后，仍直接进入正式产品聊天界面，并保持统计、设置和输入草稿的实际 GUI 行为。未运行 Gradle/Xcode 构建，没有改生产、测试、Gradle 或 Xcode 文件，没有读取钥匙串，也没有发送消息或发起模型请求。

## 执行环境与产物

- Agent ID：`01a08770-b0be-7041-aeb5-2bfa56934644`；父 agent 创建的 `/tmp/cmp-rollback-07-tools/model-desktop.ok` 和 `models.json` 记录的模型为 `gpt-5.6-terra / high`（turn `01a08770-b126-7311-961d-a4285aacc2e7`）。
- 原始包：`desktopApp/build/compose/binaries/main/app/RikkaHub.app`；直接以前台 TTY 执行 `Contents/MacOS/RikkaHub`，未改 bundle、`Info.plist` 或签名。
- 已核对本次 JAR：`Contents/app/composeApp-jvm-85116b8babcbf7e36033f9f4b86f233f.jar`；SHA-256 为 `e90eb04991139a4a8f7b54aa10d71931c4c60473edd7ec400cb8f67f76ee3a20`，与 `/tmp/cmp-rollback-07-tools/builds.json` 一致。
- 启动均仅设置 `JAVA_TOOL_OPTIONS=-Duser.home=/private/tmp/rikkahub-cmp-rollback-07-desktop-home`。基线 PID/TTY 为 `82680/ttys006`，同 profile 冷启动 PID 为 `85104`，两者都由窗口关闭正常退出且前台 TTY 会话结束。`lsof` 证实各实例打开的数据库位于该隔离 profile，且同时打开本次 Desktop 包和上述 JAR。额外的干净 profile 菜单冒烟 PID `86233` 也已正常退出。
- GUI 由 CUA 操作；每次导航均取新鲜可访问树或截图定位。对于需保存的截图，先按测试 PID 通过 `CGWindowList` 确定 layer-0 主窗口号，再用 `screencapture -l` 截取。四张截图都已实际查看，未含账户、凭据或用户实际数据。

## 验证结果

| 步骤 | 预期 | 实际 |
| --- | --- | --- |
| 首次启动 | 显示正式聊天、原主题与抽屉/菜单；没有 Status/Capabilities 演示底栏/侧栏、空白或 DI 失败 | Pass。实际可见深色正式聊天页，带“新聊天”、默认助手、菜单与输入框；Koin 输出为 `Started 73 definitions`。语义树中没有 Status/Capabilities 演示项。 |
| 统计往返 | 打开统计，读到实际值；返回聊天 | Pass。抽屉“统计数据”打开“统计”页，实际可见聊天热力图及总对话数、总消息数、输入/输出 Token 均为 0；返回后回到原聊天。未发送草稿前后统计始终为 0。 |
| 设置层级 | 打开设置和无副作用子页后，逐层返回聊天，不改设置 | Pass。实际打开“设置”，再打开“偏好设置”（主题、通知、常规、界面偏好设置），通过界面返回控件恢复到聊天。未改变颜色模式或任何设置项。 |
| 草稿保留 | 在空聊天输入 `CMP07-desktop`，不发送；统计往返后保留；清空 | Pass。语义树和可见截图均显示标记；统计往返后仍显示在同一输入框。未点击/长按发送；统计仍为总对话 0、总消息 0。使用文本选择和 Backspace 清除了该标记，最终树恢复为“输入消息与AI聊天”占位符。 |
| 完整冷启动 | 终止自己的实例后以同 profile 冷启动，正式聊天入口仍可用 | Pass。PID `82680` 完全退出后，PID `85104` 以同一 profile 路径冷启动，实际显示正式空聊天页与菜单按钮，非前一帧。随后在已清理 profile 上的独立 PID `86233` 再次冷启动，实际打开并关闭聊天抽屉，确认正式导航入口可用。 |

## 证据与可见覆盖

- [初始正式聊天页](gui-desktop-chat-initial.png)：首次启动的空聊天、原主题、菜单和输入框；没有演示外壳。
- [统计页](gui-desktop-statistics.png)：实际可见热力图与全零总对话/总消息卡片。
- [草稿统计往返后](gui-desktop-draft-returned.png)：实际可见未发送的 `CMP07-desktop` 已返回原输入框。
- [同 profile 冷启动](gui-desktop-cold-start.png)：首次完整退出后，PID `85104` 的正式空聊天界面。

可访问性语义树用于确认文本值、导航可达性和输入框内容；截图只用于上述实际可见的页面覆盖。Compose Desktop 的可访问树在嵌套导航返回时仍会保留离屏导航层，因此没有把离屏语义节点当作可见页面证据。

## 清理、限制与修改文件

所有测试 PID（`82680`、`85104`、`86233`）均已正常退出；`lsof -p` 对最终 PID 已失败。每次结束后都删除专用 profile `/private/tmp/rikkahub-cmp-rollback-07-desktop-home`；未删除模拟器或用户数据。没有创建临时 XML、数据库 fixture 或自定义工具，父 agent 的 `/tmp/cmp-rollback-07-tools` 门控和产物清单未修改。

本验证覆盖本次 Desktop 原始包的正式入口、抽屉、统计、设置/偏好设置、未发送草稿的导航保留、清理和冷启动。它不覆盖 Android/iOS GUI、真实模型生成、外部服务、附件、并发或异常路径，也不替代父 agent 的源码等价与编译核对。

本 agent 最终新增的文件：

- `docs/references/evidence/cmp-rollback-07-2026-09-10/gui-desktop-verification.md`
- `docs/references/evidence/cmp-rollback-07-2026-09-10/gui-desktop-chat-initial.png`
- `docs/references/evidence/cmp-rollback-07-2026-09-10/gui-desktop-statistics.png`
- `docs/references/evidence/cmp-rollback-07-2026-09-10/gui-desktop-draft-returned.png`
- `docs/references/evidence/cmp-rollback-07-2026-09-10/gui-desktop-cold-start.png`
