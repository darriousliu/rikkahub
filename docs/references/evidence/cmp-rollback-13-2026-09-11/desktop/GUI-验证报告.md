# cmp-rollback-13 Desktop GUI 验证报告

- 日期：2026-09-11
- 平台：Desktop（macOS，隔离 profile）
- 测试技能：`cmp-rollback-13-desktop`
- 测试 profile：`/private/tmp/cmp-rollback-13/desktop/desktop-home`
- 网络/模型请求：未使用

- 最终包：`desktopApp/build/compose/binaries/main/app/RikkaHub.app`
- 最终可执行文件 SHA-256：`b1f12466218617805fd2f2ea59f0e9b19c655cf1a8a3d7e84e8af26274ac336c`
- composeApp JAR：`Contents/app/composeApp-jvm-d2be9efdbf56dd1225449a8a95ff7b16.jar`
- composeApp JAR SHA-256：`c3ffa09687562766d30c4f3ecce47307d015a8e7edd8505137148c2deb3b55a1`
- 最终包初次隔离进程 PID：`59280`；删除后的冷启动新 PID：`65491`。

## 完整回归

| 操作 | 预期 | 实际 | 原始证据 |
| --- | --- | --- | --- |
| 经真实 macOS 文件选择器导入测试 ZIP | 技能出现在 Agent Skills 列表 | 出现 `cmp-rollback-13-desktop`，描述与 platform 均正确 | [01-imported-skill-list.png](01-imported-skill-list.png) |
| 打开技能目录 | 可见 `SKILL.md` 与 `docs/guide.md` | 目录树已展开并显示两个文件 | [02-detail-tree-expanded.png](02-detail-tree-expanded.png) |
| 查看主文档 | frontmatter 和 v1 正文可读 | 显示固定 frontmatter 与 fixture v1 | [03-skill-md-v1.png](03-skill-md-v1.png) |
| 编辑并回读辅助文档 | `docs/guide.md` 保存并显示 v2 | 保存后重新打开，显示 v2 全文 | [04-guide-v2-reread.png](04-guide-v2-reread.png) |
| 在助手扩展页启用技能 | 开关为启用 | Skills 页显示该技能且开关已勾选 | [05-assistant-skill-selected.png](05-assistant-skill-selected.png) |

## 冻结最终包补验

| 操作 | 预期 | 实际 | 原始证据 |
| --- | --- | --- | --- |
| 冷启动最终包后查看技能列表 | 已导入技能仍存在 | 列表保留测试技能 | [06-final-package-cold-list.png](06-final-package-cold-list.png) |
| 打开最终包中的目录树 | 主文档与辅助文件仍可见 | 展开后显示 `SKILL.md` 与 `docs/guide.md` | [07-final-package-tree.png](07-final-package-tree.png) |
| 读取最终包中的辅助文件 | 编辑后的 v2 持久化 | `guide.md` 显示 v2 全文 | [08-final-package-guide-v2.png](08-final-package-guide-v2.png) |
| 冷启动后查看助手 Skills | 选择持久化 | 测试技能仍为启用 | [09-final-package-selection.png](09-final-package-selection.png) |

## 删除与清理核验

删除确认后，技能管理页显示“无技能”。单独的空列表不能证明选择清理，因此随后停止 PID 59280，以同一隔离 profile 冷启动 PID 65491，并重新进入助手详情，创建新的 AssistantDetailVM。新 ViewModel 的 Skills 页显示“没有可用的 Skill”。已打开的旧详情页在导入或删除后不刷新列表是既有单次 `listSkills()` 初始化缓存行为；本次未改变此行为。

| 核验 | 预期 | 实际 | 原始证据 |
| --- | --- | --- | --- |
| 删除测试技能 | 管理列表不再列出技能 | 显示“无技能” | [10-after-delete-empty-list.png](10-after-delete-empty-list.png) |
| 新建详情 ViewModel / 冷启动后检查助手 Skills | 不再有可选择或已启用的本轮技能 | 显示“没有可用的 Skill” | [11-cold-start-fresh-assistant-empty.png](11-cold-start-fresh-assistant-empty.png) |
| 独立 profile 落盘检查 | 本轮技能目录不存在，所有助手 `enabledSkills` 均无本轮名称 | `skill_directory_absent=true`，目录计数 `0`；`assistant_enabled_name_absent=true`，名称匹配计数 `0` | 命令结果仅输出本轮 profile 路径、布尔值和计数；未输出偏好内容 |

所有保留图片均为实际 GUI 原图，已逐张复看；未包含文件选择器的无关近期文件或其他中间画面。

本轮隔离进程已停止，私有 profile、夹具、原生 AX/CGWindow 辅助工具和中间截图已删除；父 agent 已复核私有目录不存在。
自动化期间只操作已核对的本轮 PID，未操作既有桌面 PID `99566` 或用户默认 profile。
