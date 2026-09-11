# CMP 第14项：iOS GUI 验证证据

日期：2026-09-11
设备：iPhone 17 Pro Max，UDID `03C090DA-107B-4F9F-BCCD-8D5265D32820`。开始时已启动，结束保持启动。
构建目标：`iosApp` / Debug / `me.rerere.rikkahub.ios`

## 最终包

冻结源码上的最终 `build_sim` 成功，构建产物与模拟器安装包逐项 SHA-256 一致：

| 文件 | SHA-256 |
| --- | --- |
| `RikkaHub` | `f51787721d5d0a8e9979b1ed07f0f0ed92d6765fd8e9407b0d6431baef807283` |
| `RikkaHub.debug.dylib` | `4792e47818e2cfb9657aa73d14754d5ee0bd74120bf760053d3ad0477aa7f664` |

前述 PNG/TXT/fork 流程使用上一包（主程序 `4a6d25331151fa725f34d354a7d09aed33e07569c37cb4eae6108e201b0be8bd`、dylib `520dbf11b9c35d30640e28a369a826eae460b95976d6601615b4500e538626b2`）；14A 之后只补验头像和背景的平台生命周期。其消息和 fork 逻辑未在本次最终包前变更。

## 初始状态与夹具

- 开始前读取指定模拟器状态，并建立私有恢复备份 `/private/tmp/cmp-rollback-14/ios/private-backup/RikkaHub-initial`：目录 `0700`、文件 `0600`。备份在恢复与父代理复核后已删除，未写入仓库。
- Files 选择器 staging 使用本地 FileProvider，metadata 为 `group.com.apple.FileProvider.LocalStorage`；唯一的 `File Provider Storage/cmp-rollback-14` 目录已精确删除，未触碰 CloudDocs。
- 照片库只导入两张本轮 64×64 色块 PNG，均来自本地 `/private/tmp`，未接触 iCloud：头像红图 SHA-256 `8aefc5dca34cb5fd8ed07f4a1fea9776bdce11b0303875d89c27ad98207dffaa`，背景蓝图 SHA-256 `1c16f2b6b8031b26f107224277108a0b35e038906240e2da0f904af92132a7cb`。

## GUI 结果

| 操作 | 预期 | 实际 |
| --- | --- | --- |
| PNG 选择与离线长按发送 | Files 导入后保存用户消息，不请求模型 | 通过（上一包）。真实选择器导入 `e2563984-5fd0-4131-b934-250cfb6afe3b.png`；插件长按保存用户消息，无助手回复。 |
| TXT 选择 | Files 导入本轮 TXT 并显示 | 通过（上一包）。导入 `0eaa2ffd-7bf6-4456-9e81-8a1cdaaa35e9.txt`，见 [native-picker-txt-selected.jpg](native-picker-txt-selected.jpg) 和 [txt-attachment-visible.jpg](txt-attachment-visible.jpg)。 |
| TXT 离线发送补试 | 长按不请求模型 | 未通过（上一包）。坐标级长按被界面识别为普通发送，出现 TLS 失败提示；无成功模型响应或凭据输出。临时会话已 GUI 删除，不计为离线发送通过。 |
| GUI fork | 新节点 ID，保留原 message ID；附件 URI 不同且字节相同 | 通过（上一包）。源会话 `a41712eb-d132-4b18-a53c-288b88b75b09` / 节点 `4460dbcf-a891-4348-925b-0bfe94517151`；fork `17147e5a-fde0-4127-b56b-d1c0b4736ccd` / 节点 `3f5e8d5e-9d99-43dd-86fc-f1d58bda79e1`。两者 message ID 均为 `6493146c-5368-436d-a4c4-7280ca80a6aa`，标题为空；fork PNG 为 `46f2d07f-1edc-4e19-98a1-96c7cebfde9d.png`，两份字节 SHA-256 均为 `bfaa79bbba10c4659c25d505b3813ccb18689923aa593b0bb8f66dc794dc48a5`。 |
| 删除源会话后冷启动 fork | fork 图片继续显示 | 通过（上一包）。GUI 删除源会话、冷启动后缩略图仍显示，见 [fork-png-visible-after-source-delete-cold-restart.jpg](fork-png-visible-after-source-delete-cold-restart.jpg)。 |
| 临时助手头像 | 在最终已安装包中经 Photo Picker 选择后，仅停止/启动，红图实际可见 | **通过**。复用“GUI 回归助手”，冷启动后红色 64×64 图片在助手列表中实际渲染，见 [final-avatar-visible-after-cold-restart.png](final-avatar-visible-after-cold-restart.png)。当前容器 `4CA184F2-5B4B-4783-823B-813A4060C7C2` 的 `file:` URI 后缀为 `upload/c4da71ea-35e7-42f5-97b9-90ced27cdf47.png`；文件 136 bytes，SHA-256 `8aefc5dca34cb5fd8ed07f4a1fea9776bdce11b0303875d89c27ad98207dffaa`。 |
| 临时助手背景 | 在最终已安装包中经 Photo Picker 选择后，仅停止/启动，蓝图实际显示 | **通过**。冷启动后的聊天页实际显示整页蓝色背景，见 [final-background-visible-after-cold-restart.png](final-background-visible-after-cold-restart.png)。同一当前容器的 `file:` URI 后缀为 `upload/89f3f8e2-ac99-4850-921d-dd1bc5344acb.png`；文件 136 bytes，SHA-256 `1c16f2b6b8031b26f107224277108a0b35e038906240e2da0f904af92132a7cb`。 |

## 首次复验中的旧容器路径

早先先保存图片、再安装最终包时，持久化 URI 仍指向前一数据容器，头像呈现浅粉占位矩形，背景也未显示。
`final-avatar-visible-after-cold-launch.jpg`、`final-background-configured-after-cold-launch.jpg` 和
`final-chat-background-not-visible-after-cold-launch.jpg` 仅保留为失败诊断，不能作为显示通过的证据。
上表通过结果来自最终已安装包内重新选择图片后，仅停止/启动的复验；期间没有再次安装。
旧容器绝对路径的存量兼容仍留给 14B，不因本次新文件显示正常而视作已修复。

## 清理与保留状态

- GUI 已删除本轮源会话、fork 和 TXT 临时会话。复用“GUI 回归助手”完成最终包图片补验后，原始 `settings.preferences_pb` 已在停 app 后从私有备份逐字节恢复；未恢复或覆盖数据库。
- `ConversationFileStore` 的产品删除接线仍为 noop，因此**产品物理附件删除不通过**。已手工精确删除先前五个本轮 `Documents/upload/` 文件；图片补验产生的新 UUID `c4da71ea-35e7-42f5-97b9-90ced27cdf47.png` 与 `89f3f8e2-ac99-4850-921d-dd1bc5344acb.png` 也已精确删除并核验不存在。此清理不构成 14B 产品通过。
- **Photos 清理完成（普通删除）。** 两张本轮 64×64 红、蓝合成测试图已在正常图库中精确选择并普通删除；图库计数从 2,889 降至 2,887，删除后两图不再显示。普通删除后的系统 Recently Deleted 保留这两项，未进入受保护相簿、未请求认证、未执行永久擦除；未选择或删除任何其它图片。
- 已停止 RikkaHub 进程；Pro Max 保持 Booted。LocalStorage staging 和 `/private/tmp/cmp-rollback-14/ios/fixtures` 均复核不存在；父代理完成审查后，私有初始备份、诊断日志和相册清理截图均已删除。

## 原始截图

- [fork-png-visible-before-restart.jpg](fork-png-visible-before-restart.jpg)
- [fork-png-visible-after-source-delete-cold-restart.jpg](fork-png-visible-after-source-delete-cold-restart.jpg)
- [native-picker-txt-selected.jpg](native-picker-txt-selected.jpg)
- [txt-attachment-visible.jpg](txt-attachment-visible.jpg)
- [final-avatar-visible-after-cold-launch.jpg](final-avatar-visible-after-cold-launch.jpg)
- [final-background-configured-after-cold-launch.jpg](final-background-configured-after-cold-launch.jpg)
- [final-chat-background-not-visible-after-cold-launch.jpg](final-chat-background-not-visible-after-cold-launch.jpg)
- [final-avatar-visible-after-cold-restart.png](final-avatar-visible-after-cold-restart.png)
- [final-background-visible-after-cold-restart.png](final-background-visible-after-cold-restart.png)

本平台 GUI 验证未修改生产源码、测试或用户现有 Xcode 修改；提交由主 agent 统一完成。

相册清理截图仅用于本机核验，不纳入仓库证据；仓库保留应用内测试画面及清理结果记录。
