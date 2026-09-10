# cmp-rollback-13 iOS GUI 验证

- 日期：2026-09-11
- 设备：iPhone 17 Pro Max，UDID `03C090DA-107B-4F9F-BCCD-8D5265D32820`
- Bundle ID：`me.rerere.rikkahub.ios`
- 工程与 scheme：`iosApp/iosApp.xcodeproj` / `iosApp`

## 最终包

使用 Build iOS Apps 的 `build_sim` 重新构建冻结的生产源码并安装到上述设备。构建产物与安装后 app bundle 中的文件 SHA-256 一致：

| 文件 | SHA-256 |
| --- | --- |
| `RikkaHub` | `0f551c88440f3257eb25b56f79b922d19c118fa5584e67125c9de4a5c4fba9e5` |
| `RikkaHub.debug.dylib` | `86b2ccdf5d26c27b14d6c4eb9a17b396904b3b6f4bfe27df4c7cd3a49cf50c70` |

## 操作、预期与实际

| 操作 | 预期 | 实际 |
| --- | --- | --- |
| 经 iOS 系统文件选择器导入 `cmp-rollback-13-ios-v1.zip` | Agent Skills 出现测试技能 | 出现 `cmp-rollback-13-ios`，见 `01-import-list.jpg`。 |
| 展开目录树，打开 `SKILL.md` | 显示固定 frontmatter 和正文 v1 | 显示名称、描述与 `Main document version: v1`，见 `02-main-v1.jpg`。 |
| 打开 `docs/guide.md`，编辑为 v2 并重新打开 | 辅助文件保留为精确 v2 | 最终安装包中实际回读为 `Auxiliary document version: v2`，见 `03-final-package-guide-v2.png`。 |
| 冷启动后进入助手的 Extensions / Skills，启用测试技能 | 技能存在且开关保持启用 | 最终安装包中技能可见且开关为启用，见 `04-final-package-enabled.png`。 |
| 在 Agent Skills 的更多操作中删除测试技能，等待异步加载 | 技能列表清空 | 页面显示“无技能”，见 `05-delete-manager-empty.png`。 |
| 删除后冷启动，重新创建助手详情页并进入 Skills | 助手 Skills 为空 | 页面显示“没有可用的 Skill”，见 `06-delete-coldstart-assistant-skills-empty.png`。 |

导入时，系统文件选择器未向 MCP 快照提供可用 elementRef。先用 `describe-ui` 确认逻辑坐标系，再用 Build iOS Apps 插件 bundled AXe 的物理触摸在 ZIP 图标中心执行一次真实触摸；导入后立即回到插件 `snapshot_ui` 验证“技能已导入”。应用内导航、列表选择、开关与删除确认均在每次导航后的新 `snapshot_ui` 中取得 elementRef 再操作。编辑 `guide.md` 时，另使用 bundled AXe 坐标定位文本光标和屏幕数字键盘的 `2` 键；编辑后的内容由应用界面重新打开回读确认。

导入后已存在的助手详情 ViewModel 没有自动刷新技能列表；冷启动重新创建该 ViewModel 后技能出现。这与父 agent 已核对的既有 `AssistantDetailVM` 初始化时一次性 `listSkills()` 行为一致，未改动产品代码。

## 清理核对

只按本轮名称在新数据容器中作内存内二进制计数，未输出偏好内容：

| 检查 | 结果 |
| --- | --- |
| `skills_fixture_dir_absent` | `true` |
| `skills_fixture_dir_count` | `0` |
| `enabledSkills_fixture_reference_absent` | `true` |
| `enabledSkills_fixture_reference_count` | `0` |
| 导入前私有偏好备份中的本轮引用计数 | `0` |

偏好检查只比较本轮技能名称在导入前私有备份与当前偏好中的出现计数；没有逐 key 解码或比较用户偏好字段。整体字节比较不相等，但本验证未归因该差异，也未据此断言其仅由正常启动状态造成。通过 GUI 删除后，本轮触及的技能关联已回到导入前的零引用状态，因此没有整体还原偏好，也没有恢复其他设置字段。

已删除的 ZIP 范围为指定模拟器 UDID 的 `data` 树内所有精确命名为 `cmp-rollback-13-ios-v1.zip` 的本轮副本，包括应用容器及系统文件提供方/下载流程保留的副本；同时删除了 `/private/tmp/cmp-rollback-13/ios` 下本轮私有夹具、截图、helpers 和备份。应用已停止，iPhone 17 Pro Max 保持原先的 booted 状态。
