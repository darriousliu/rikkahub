# CMP 回退 14A：桌面端 GUI 证据

日期：2026-09-11（Asia/Shanghai）。最终包通过
`-Duser.home=/private/tmp/cmp-rollback-14/desktop-retry/profile` 验证消息/fork，
`desktop-final/profile` 验证头像/背景。未读取或修改默认数据库与设置，也未停止或操作用户原有的 PID 99566。

最终启动器为 `desktopApp/build/compose/binaries/main/app/RikkaHub.app/Contents/MacOS/RikkaHub`，SHA-256：
`c8fae8ebe831e8299871c4cc54eb571ce7e4f18886006ebb84c5777babe781ba`。
业务 JAR 为 `composeApp-jvm-f015905426541663e481894eab5d7076.jar`，SHA-256：
`aa47e48298939b7b9a5cf7e805477acbc4180ebb3d72df7425aed788c4ead6f1`。

## 已完成验证

1. `native-picker-fixtures.png` 是真实 macOS 文件选择器，显示本轮 `pixel.png` 和 `note.txt`。
2. `message-sent-with-attachments.png` 显示长按离线发送后，唯一正文、PNG 和 `note.txt` 位于消息列表，编辑框已清空。
3. 只读隔离 SQLite 证明 source/fork 会话和节点 ID 分别不同：
   `413d4801-59c2-4045-9bdf-72dcd02067c3` /
   `bbfdf1de-8597-4131-b6d7-cf31db1a7286`，以及
   `4c531637-4a65-492c-ad68-f4afc1b86484` /
   `2514f8d3-9226-4ca2-9b12-e1e3782587ca`；保留的 message ID 相同：
   `c9fb88a0-477c-4290-a146-b1cc4da4661a`。两组附件 URI 使用不同 UUID。
4. 已经通过 GUI 删除 source 会话；停止隔离 PID 后以同一隔离 profile 冷启动最终包。
   `fork-after-source-delete-coldstart.png` 来自该冷启动后的 fork 窗口，仍显示正文、图片和文档。
5. `attachment-hashes.json` 按四个确切 URI 逐项读取，证明 PNG source/fork 都是 171 字节、SHA-256
   `cfa59c9ffeb583baa12d9d609f8bc76c0bae6207bac34bdeca6d7c3a65996f6b`；TXT source/fork 都是 39 字节、SHA-256
   `917f1c2e3799dd0c59095fb9cb002fb7e78f392de724529f61976a09c2d30684`。

## 截图与哈希

* `native-picker-fixtures.png`：`0fb95a7aa4642df3437737f034cc484977c0c13eb8890aee5197645d8e736ac0`
* `message-sent-with-attachments.png`：`b40381cebaa0a61b17e0e7f2619fad988af2a6fc65485b3496db06ede97bf286`
* `fork-after-source-delete-coldstart.png`：`b40381cebaa0a61b17e0e7f2619fad988af2a6fc65485b3496db06ede97bf286`

后两张分别在发送进程 PID 99596 与 source 删除后的冷启动进程 PID 3156 中独立捕获。两次界面像素一致，因此文件哈希相同。

## 头像、背景与冷启动

临时助手为 `CMP14-avatar-background`（`358b0077-9fac-471b-8d59-f9b15ea717ff`），
位于本轮新建的隔离 `desktop-final/profile` 中。

| 操作 | 预期 | 实际证据 |
|---|---|---|
| 在基础设置关闭渐变背景，经背景按钮、从相册选择打开原生 Picker | 导入指定的本地测试图片 | [background-native-picker-selected.png](background-native-picker-selected.png) 中选中 `background-fixture.png`，Open 可用。 |
| 切换到临时助手聊天页 | 实际显示蓝绿测试背景 | [background-chat-visible.png](background-chat-visible.png) 中助手名与背景正确。 |
| 停止 PID 53836，以同一最终 bundle 和同一隔离 profile 启动 PID 59076 | 背景跨冷启动呈现 | [cold-start.png](cold-start.png) 中临时助手和蓝绿背景恢复。 |
| 冷启动后打开助手列表 | 临时助手头像仍为实际蓝绿图片 | [cold-avatar-list.png](cold-avatar-list.png) 中第三行名称、头像正确；最终导航和截图由父代理完成，Terra 独立目视复核。 |
| 按隔离设置记录的确切 URI 核验文件 | 头像/背景为不同 UUID，字节与测试图片一致 | 两文件均为 171 字节，子 agent 与父代理独立核验；见 [avatar-background-hashes.json](avatar-background-hashes.json)。 |

头像：`upload/4938ee89-8eb6-49de-96a8-896bc8dfd63d.png`。
背景：`upload/5651d6c2-2046-4bdb-98f0-cfea74aa0eb7.png`。
两者 SHA-256：`cfa59c9ffeb583baa12d9d609f8bc76c0bae6207bac34bdeca6d7c3a65996f6b`。

## 自动化诊断与清理

桌面验证曾受全屏 Space、前台状态及截图缩放坐标影响；CG 的全窗口列表还包含已关闭的原生 Picker，
其旧画面不能证明当前可操作。最终以可见窗口、实际界面内容和深层辅助功能控件核对操作，未更改生产实现来适配自动化。

本轮最终测试进程 PID 59076 已停止；此前消息/fork 和排障进程也已停止。原消息/fork 的四个文件
`a6049dea-6ad6-44d9-af77-4ebef641970d.png`、`e2fd3305-3390-4978-81e9-abfc3fc08d87.png`、
`2e68c515-d712-4b83-bc89-1fe1e7217208.txt`、`27627bac-c97e-483a-b416-f3dbab41210e.txt`，
以及上述两张头像/背景均已逐项确认不存在。只访问这些已记录的测试 UUID，没有枚举用户上传目录。

本轮隔离 profile、夹具、私有截图、诊断日志和自动化 helper 已统一删除，见 [cleanup.json](../cleanup.json)。
临时助手随本轮私有 profile 一起清理，未声称通过 GUI 删除该助手；产品 `AssistantAssetCleaner` 仍留给 14B。
整个流程未调用模型或读取测试密钥。
