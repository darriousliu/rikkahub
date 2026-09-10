# 第 11 项 Android GUI 验证

结果：下表各项通过。由 gpt-5.6-terra/high 子 agent 使用 android-cli 操作
`emulator-5554` / `Pixel_10_Pro_XL`，包名 `me.rerere.rikkahub.debug`。
实际安装的 base.apk 与 [最终产物](artifacts.json) 哈希一致。

| 操作步骤 | 预期结果 | 实际结果与原始截图 |
|---|---|---|
| 进入设置 → MCP | Good 连接成功，Retry 显示固定失败 | Good 正常 MCP 图标、无错误；Retry 显示固定 Streamable HTTP 错误。[初始列表](gui-android-01-server-initial.png) |
| Good 齿轮 → Tools → 展开 echo | 两个工具可见，echo 描述和参数正确 | 显示 `CMP11 echo description` 和 `value` 参数。[工具详情](gui-android-03-echo-schema.png) |
| echo Needs Approval 开、Enable 关，image Enable 保持开 → Save → 重开 | 编辑值保存并重新显示 | 三个开关值与预期一致。[重开工具](gui-android-05-tools-reopened.png) |
| Good Basic Settings → Enable 关 → Save；随后重新开启并保存 | 停用时显示 inactive，重启后恢复连接并保留工具开关 | 停用图标/红色启用状态点出现；重新启用后恢复正常。[停用状态](gui-android-06-good-disabled.png) |
| 调用本机 Android recover 控制端点，再 Retry OFF → Save → ON → Save | 真实应用重新连接，旧错误消失 | 两个服务恢复正常图标，无旧错误。[恢复列表](gui-android-07-retry-recovered.png) |
| 聊天输入 More options → MCP Servers | 当前 Default Assistant 只选 Good；只提供 Good 已启用工具 | Good 为 Connected、已选、`1/2 tools`；Retry 为 Connected、未选、`2/2 tools`。[选择器](gui-android-08-picker.png) |
| force-stop 后冷启动同一包，重新进入 Good Tools | 连接恢复，工具开关仍保留 | echo 审批开/启用关，image 启用开。[冷启动工具](gui-android-09-cold-launch-tools.png) |

列表绿色点只表示服务器 Enable，不单独作为 Connected 证据；连接结合正常图标、实际工具列表、
选择器中的 Connected 和父 agent 保存的真实 initialize/tools/list 日志判断。

最初夹具名带空格，被 tag 2.4.5 和当前页面共有的原名称校验拒绝保存。改为 `CMP11Good`/
`CMP11Retry` 后重验，未修改生产校验。初轮无效截图已排除，不用于判断通过或持久化回归。

7 张原始截图均由父 agent 目视复核，哈希见 [截图复核记录](screenshot-review.json)。
结束时应用已停止，原 MCP/助手偏好及本地网络权限已恢复并回读，模拟器已通过 android-cli 停止；
原偏好唯一剩余差异为正常启动计数，见 [清理结果](android-cleanup.json)。

覆盖边界：本轮验证本机 Streamable HTTP 的实际页面接线，不发送模型或工具调用。
UI 只展示 `value` 参数名，未把未展示的 type/required 元数据列为 GUI 通过；真实 SDK 契约测试覆盖参数和结果。
没有资源查看入口；未执行 SSE 或外部 OAuth 浏览器回调，相关生产实现没有改动。
