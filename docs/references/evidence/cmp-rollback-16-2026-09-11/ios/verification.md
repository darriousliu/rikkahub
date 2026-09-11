# 第 16 项 iOS GUI 验证

设备：iPhone 17 Pro Max `03C090DA-107B-4F9F-BCCD-8D5265D32820`；产品 bundle：`me.rerere.rikkahub.ios`。
正式产物与父端构建散列一致。通过 Build iOS Apps 插件执行独立 XCTest，实际操作模型为 Terra/high。
两种远端协议、本地 ZIP 系统选择器和重启链路已完成，原数据已恢复；原始数据库物理散列的限制单列如下。

## 验证步骤与实际结果

输入为两个独立 Chatbox 合成会话，各有两条固定正文，以及 upload 和旧 attachments 各一个标记。
应用停止后仅预置 WebDAV/S3 两项测试配置，其余偏好 entry 原始字节保留。没有读取钥匙串或发送模型请求。

| 步骤 | 预期 | 实际 |
| --- | --- | --- |
| WebDAV GUI 上传完整备份，独立读取 ZIP | 配置、主库/WAL、唯一会话、两正文和两附件匹配 | 通过，[独立检查](webdav-parent-archive-check.json) |
| GUI 删除 WebDAV 会话，移除两个标记，再从远端恢复并重启 | 两正文重新显示，两附件字节恢复 | 通过；最终正文断言见 `webdav-restored-messages-visible-…000040` |
| S3 GUI 上传完整备份，独立读取 ZIP | 同上，S3 参数及 SigV4 请求有效 | 通过，[独立检查](s3-parent-archive-check.json) |
| S3 恢复前仅选 DATABASE | 正文恢复，附件跳过；归档设置恢复后 FILES 可能重新显示选中 | 与原行为一致；正文断言见 `s3-restart-verify-chat-…000056`，不算完整附件恢复通过 |
| 保持 DATABASE+FILES，重新恢复同一个 S3 ZIP | 两附件在重启前后均存在、SHA 匹配 | 通过；`s3-recheck-restore-…000060`、`s3-recheck-restarted-…000061`。正文的 GUI 断言在前一次 DATABASE 恢复中完成 |
| GUI 删除两协议远端备份 | 删除请求成功，列表为空 | WebDAV/S3 均 DELETE 204；S3 对象为 `backup_20260911_193447.zip` |
| 本地 → 备份文件导入 → 系统 Files 选择一条目 ZIP → 原提示重启 | `upload/cmp16-ios-local.txt` 恢复，源 ZIP 保留，`restore_*` 清零 | 通过；marker SHA `78aaa9c258f3d196ecc6fa52151f2a1ee9534fdcf87e701fc278d862b8853f0e` |

两协议上传、列表、恢复、远端删除均通过应用 GUI 执行，服务器仅监听本机。
S3 的“文件跳过”来自恢复开始时传入的 config；恢复设置后选项显示变化，不会改变这一次的文件恢复范围。
复测只纠正测试选项，没有修改产品逻辑。首次本地 ZIP 的 marker 位于根目录，未按预期写入 upload；更正夹具内部路径后复测通过。

父端回查了 XCTest 的真实返回结果，保留包含失败定位尝试的
[逐次断言与结果摘要](parent-xctest-records.json)。例如 WebDAV 前两次正文定位失败，第三次两条断言才通过；
不把驱动运行成功、未完成导航或早期失败当作业务通过，也不把临时驱动运行次数并入 400 次代码测试。
[actual-evidence.json](actual-evidence.json) 记录本地文件与远端清理结果。

## 数据恢复与清理

测试前停止应用并复制主库、WAL、SHM、lock 与 settings。随后子 agent 对唯一私有基线执行 `sqlite3 .schema`，
触发 WAL checkpoint；没有执行 DML，但原始主库/WAL/SHM 的物理布局已改变。
因此不报告原始物理文件 5/5 相同。

父端在另一个完整副本中冻结 schema 和 16 张表的逻辑摘要，并在测试结束后再次独立核对当前应用数据：
逻辑内容一致、原 settings SHA 一致，检查前后源文件未变。应用已停止，五个精确测试标记均不存在。
见 [冻结基线](checkpointed-baseline.json) 和 [最终独立核对](parent-final-baseline-check.json)。

原始 settings SHA：`c0f39584b3d2e3bca04498bc63644ee61d3885abaa5a9dfa31fbe0e8d25edc92`。
当前容器为 `41A4400D-E6A9-4AD4-9531-975515D71792`。
用户旧附件保持原路径；专用 Files 夹具目录、独立 XCTest runner、临时工程、结果 bundle 和本轮工具日志已清理。
私有上传归档、配置副本与基线在父端完成独立核对后统一删除，最终清理见主验证记录。

准备阶段的 [activation-smoke.jpg](screenshots/activation-smoke.jpg) 只证明驱动可以激活产品，不作为正式恢复截图。
