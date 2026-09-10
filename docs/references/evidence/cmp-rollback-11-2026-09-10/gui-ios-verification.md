# 第 11 项 iOS GUI 验证

结果：下表各项通过。由 gpt-5.6-terra/high 子 agent 使用 Build iOS Apps 插件操作
**iPhone 17 Pro Max**，设备 `03C090DA-107B-4F9F-BCCD-8D5265D32820`，包名 `me.rerere.rikkahub.ios`。
使用本轮最终构建，实际 Kotlin 代码位于 [产物记录](artifacts.json) 中的 RikkaHub.debug.dylib。

| 操作步骤 | 预期结果 | 实际结果与原始截图 |
|---|---|---|
| 进入设置 → MCP | Good 正常连接，Retry 显示固定失败 | Good 正常图标、无错误，Retry 显示固定 Streamable HTTP 错误。[初始列表](gui-ios-01-initial-list.jpg) |
| Good → Tools → 展开 echo；设置审批开/启用关，image 保持启用 → Save | 两个工具、描述和 `value` 参数可见，开关可编辑保存 | 控件和描述符合预期；此图为保存前，持久化由后续选择器/冷启动验证。[工具配置](gui-ios-02-tools-configured.jpg) |
| Good Basic Settings → Enable 关 → Save | Good 停用状态可见 | 出现停用图标/红色启用状态点。[停用列表](gui-ios-03-good-disabled.jpg) |
| 重新开启 Good 并保存 | 恢复连接，保留工具开关 | 正常图标恢复，无错误。[重连列表](gui-ios-04-good-reconnected.jpg) |
| 调用本机 iOS recover 控制端点后实际重启应用 | Retry 发起新连接，旧错误消失 | 后续列表中 Retry 为正常 MCP 图标，旧错误消失；父日志确认恢复后的 initialize/tools/list 成功。[重连列表](gui-ios-04-good-reconnected.jpg) |
| 助手列表 → 当前“GUI 回归助手”详情 → MCP | Good 已选且仅一个工具可用，Retry 未选 | Good 为 Connected、已选、`1/2 tools`；Retry 为 Connected、未选、`2/2 tools`。[助手 MCP](gui-ios-05-assistant-mcp-picker.jpg) |
| 再次冷启动，进入 Good Tools | 工具开关持久化 | echo 审批开/启用关，image 启用开。[冷启动工具](gui-ios-06-cold-tools.jpg) |

iOS 当前没有聊天 MCP 快捷按钮，通过实际助手详情中的 McpPicker 验证；没有虚构 Retry 按钮，
失败恢复通过实际应用重启触发。列表绿点只表示 Enable，Connected 结合工具/选择器和服务日志判断。

最初含空格的夹具名被原名称校验拒绝保存；改为 `CMP11Good`/`CMP11Retry` 后重验，
未改变业务校验，也未把初轮截图作为通过证据。6 张原始截图均由父 agent 目视复核，
哈希见 [截图复核记录](screenshot-review.json)。本项无需键盘输入或 LLDB 辅助。

应用结束时已停止，整个设置文件已恢复为原始字节和 SHA-256，见 [清理回验](ios-cleanup.json)。
本轮仅覆盖本机 Streamable HTTP 页面接线；未发送模型/工具请求，未执行 SSE 或外部 OAuth 浏览器回调。
UI 中未显示的 type/required 元数据未列为 GUI 通过；参数/结果由真实 SDK 契约测试覆盖。
