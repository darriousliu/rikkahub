# 第 11 项桌面 GUI 验证

结果：下表各项通过。由 gpt-5.6-terra/high 子 agent 操作最终桌面分发包，使用独立 profile
`/private/tmp/cmp-rollback-11/desktop-home`。最终 JAR 哈希见 [产物记录](artifacts.json)。
测试进程 PID 3871，最终冷启动 PID 12317；两者的测试配置均位于独立目录。

| 操作步骤 | 预期结果 | 实际结果与原始截图 |
|---|---|---|
| 设置 → MCP | Good 正常，Retry 显示固定失败 | Good 正常 MCP 图标、无错误，Retry 显示固定 Streamable HTTP 错误。[初始列表](gui-desktop-initial-mcp.png) |
| Good Tools 设置 echo 审批开/启用关，image 保持启用 → Save → 重开 | 编辑值保持 | 开关与预期一致。[重开工具](gui-desktop-good-tools-persisted.png) |
| 展开 echo | 工具描述和参数可见 | 显示 `CMP11 echo description` 与 `value`；未显示的 type/required 不作 GUI 推断。[展开详情](gui-desktop-good-echo-expanded.png) |
| Good Basic Settings → Enable 关 → Save | 保存后进入停用状态 | [保存前开关](gui-desktop-good-disabled-modal.png) 为 OFF；[保存后列表](gui-desktop-good-disabled-saved.png) 显示停用图标和红色启用状态点 |
| 重新开启 Good → Save | 恢复正常连接 | 正常 MCP 图标恢复，无错误。[重新启用](gui-desktop-good-reenabled-saved.png) |
| POST 本机 `/control/desktop/recover`，再 Retry OFF → Save → ON → Save | 真实应用重连，旧错误消失 | 返回本机恢复成功后执行实际 GUI 启停；最终列表两台服务均正常。[恢复列表](gui-desktop-retry-recovered.png) |
| 助手列表 → CMP11 Desktop 详情 → MCP | Good 已选且一个工具可用，Retry 未选 | Good Connected、已选、`1/2 tools`；Retry Connected、未选、`2/2 tools`。[助手选择器](gui-desktop-assistant-mcp-picker.png) |
| 关闭独立测试进程，以同一 profile 冷启动 → Good Tools | 连接及工具配置恢复 | echo 审批开/启用关，image 启用开；父 agent 设置回读一致。[冷启动工具](gui-desktop-cold-restart-good-tools.png) |

9 张原始截图均由父 agent 目视复核，哈希和观察结果见 [截图复核记录](screenshot-review.json)。
绿色点只表示服务器 Enable；连接判断结合正常图标、真实工具列表、选择器明确的 Connected，
以及 [本机服务记录](gui-server-results.json) 中的 initialize/tools/list。

最初含空格的夹具名称被原校验拒绝保存，改为 `CMP11Good`/`CMP11Retry` 后重验，未改生产校验。
捕获流发生错误且 Stage Manager 窗口定位不稳定，后续使用原生 AX 激活指定测试进程、CGEvent 输入、
窗口 ID 截图；未将无关窗口或未展开的首次截图作为通过证据。

首次短进程冷启动 PID 12259 随启动会话退出；临时启动脚本添加 `start_new_session=True` 后，
PID 12317 使用同一独立 profile 持续运行并完成冷启动 GUI。此修改仅在临时脚本，未改产品启动器或代码。
最终 PID 12317 已停止，独立 profile 已移除，见 [桌面清理回验](desktop-cleanup.json)。

覆盖边界：本轮使用本机 Streamable HTTP 服务，未发送模型或工具调用，未执行 SSE 和外部 OAuth 浏览器回调；
没有资源查看入口。默认 profile 额外实例不参与测试证据，其关闭被自动审批拒绝，已保留并记录于
[实例处置记录](desktop-extra-instance-status.json)。
