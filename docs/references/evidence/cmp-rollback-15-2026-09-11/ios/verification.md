# CMP rollback 15 — iOS GUI 验证

日期：2026-09-11。设备为 iPhone 17 Pro Max `03C090DA-107B-4F9F-BCCD-8D5265D32820`，bundle 为 `me.rerere.rikkahub.ios`。已安装最终二进制与冻结构建核对一致（main `35fce2d3c45922a3458832e523b897da43f2d05e84a74914211aa1f98bcc3236`；debug dylib `75b148147f5c2ff567d7ba574c8864158901e2e9e23c7d84449931450576e71e`）。操作由隔离 XCTest 驱动已安装应用完成，未重建或重新安装产品。

- 在“设置 → 数据备份 → 本地”中，先后取消备份文件选择和导出保存；应用停止后，settings、数据库、WAL、SHM 与锁文件均匹配 GUI 前基线。
- Chatbox JSON 经真实 Files 选择和确认重启后，固定会话、中文正文和固定回复均可见；重复导入后抽屉只显示一个该会话。
- Cherry ZIP 经真实 Files 选择和确认重启后，提供商列表显示本轮提供商、一个模型与禁用状态。父 agent 对实际 GUI 导出的结构复核确认名称唯一、模型、禁用状态、密钥和地址字段正确；导入器补全地址路径属于既有行为。
- native ZIP 经真实 Files 选择和确认重启后，在实际 data container 中唯一找到 marker。
- GUI 导出实际点击保存。父 agent 独立复核归档的 CRC、settings JSON、主库和 WAL、固定会话唯一性、中文正文/固定回复与 native marker，结果见 [parent-export-check.json](parent-export-check.json)。归档随后已删除，未进入仓库。

曾有一次跨页坐标尝试进入内置 RikkaHub provider 详情；它不构成通过证据。之后的正式导航使用实际截图和可见标签。settings/数据库此时已包含正式导入数据，未将基线差异用于判断误导航是否造成写入。

清理前已停止应用并将 settings、主库、WAL、SHM、锁文件恢复到 GUI 前私有基线，逐文件散列匹配 `5/5`。随后精确删除本轮 Files 夹具与导出件、应用 Documents 夹具、native marker、临时源 ZIP 及私有基线；应用容器中的 `cmp15` 命名残留为 `0`。模拟器保持 Booted；清理保留用户原有 DeviceHub 进程。

父 agent 复核最终四张截图、应用散列与夹具目录清理，并卸载独立 XCTest runner、删除临时驱动工程和登记的 155 项工具产物，见 [cleanup.json](cleanup.json)。

截图：[中文会话](screenshots/chatbox-imported-conversation.jpg)、[重复导入](screenshots/chatbox-duplicate-deduplicated.jpg)、[Cherry 提供商](screenshots/cherry-provider-visible.jpg)、[保存选择器](screenshots/gui-export-save-panel.jpg)。
