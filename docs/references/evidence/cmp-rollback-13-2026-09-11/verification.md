# 第 13 项：技能业务收回原 SkillManager

起点：`6ac2e6e9db098250a718dd2b4a43931135c79b92`；对照 tag `2.4.5`。审计 E09 + E11。
生产源码 29 个路径变动（含移动前后路径），增加 501 行、删除 758 行，净减少 **257 行**。
测试及验证材料另计，明细见 `production-diff.json`。

本项代码回退、代码测试、三端 GUI 和测试数据清理均已完成。以下记录步骤、预期、实测结果与覆盖边界。

## 本次回退

原 `SkillManager` 在 Android 上几乎未改，另加的 `FileKitSkillStore` 却重新实现了相同业务。
本项将原 Manager、SkillMetadata、SkillPaths 迁到 common，删除以下 8 个额外类型：

- SkillStore、AndroidSkillStore、FileKitSkillStore。
- SkillSummary、StoredSkillFile。
- AssistantSkillCatalog、AndroidAssistantSkillCatalog、AssistantSkillMetadata。

SkillsVM、SkillDetailVM、助手扩展选择器和两个聊天调用点直接使用原 Manager/Metadata。
文件树恢复原递归遍历和目录/文件排序；SkillFile 恢复携带真实文件路径。
助手 enabledSkills 仍保存 frontmatter 的名称，列表项 key 恢复技能目录路径。
既有 `3a6b115c7` 的“显示名不同于目录名时按目录访问”修复保留；详情读取也仍经过原 Store 使用的
resolveSkillFile 检查，没有因去掉接口而绕过路径边界。

没有移动其余聊天业务；SharedChatRuntime/ChatService 的整体统一仍在第 24 项。
S3/WebDAV 两个 Android 调用点只将 File 转为 Path 做原路径检查，再转回 File 交给原归档代码。

## 必要适配和额外逻辑的区别

| 部分 | 本项处理 | 原因 |
|---|---|---|
| java.io.File / Context | 用现有 kotlinx-io 的 Path/SystemFileSystem，构造时传入各端原 filesDir | common 无法使用 Java/Android 类型；没有换技能目录 |
| canonicalFile / renameTo | 仅两个窄 expect/actual 扩展 | 原路径检查和 staging 算法依赖精确系统语义；Android/JVM 调原 File，iOS 调系统路径/rename |
| 同步读写与 Boolean 文件结果 | 共用 FileSystem.kt util | Manager、文件树和工具读取复用相同非业务文件能力，保留 IOException/false/null 约定 |
| FileKit | 保留应用目录和系统文件选择器入口 | 这是实际平台能力，继续用已引入的依赖 |
| IO / Main 调度 | 原 VM 的文件操作在 IO，UI 回调在 Main | 删除 AndroidSkillStore 后将原调度恢复到 VM；没有增加任务、锁或状态机 |
| staging / backup / rename | 原样保留 | tag 已有，不是迁移新加功能；仍用原 100 次临时目录命名尝试 |
| DTO、目录 Catalog、Store 双份业务 | 删除，直接使用 SkillMetadata/SkillManager | 没有平台差异，只有镜像与转发 |

对 Manager 类体进行机械 API 替换后，与 tag 类体逐字节一致，见 `manager-equivalence.json`。
替换范围只有构造 filesDir、File→Path、parentFile→parent、UTF-8 编码和日志路径；所有判断、try/catch/finally、
settings copy 和循环保留。SkillPaths 同样保留 canonical 父目录和包含关系判断。

非 Android 的重复实现中，下列行为随收敛恢复到原规则；这不是另加功能：

| 原重复实现 | 恢复的原 Manager 规则 |
|---|---|
| 技能按显示名重新排序 | 保留文件系统列举顺序 |
| 目录名 trim，额外禁止控制字符；相对路径预先改斜杠并拒绝所有 `.` / `..` | 保留原名称字符串和 canonical 路径规则；规范化后仍在目录内的路径可用 |
| 要求输入 Map 有精确 `SKILL.md` key | 先写 staging，再按原 `exists()` 判断；不新增 regular-file 校验 |
| UUID staging/backup 名称 | 恢复原 `.技能名.staging/backup.0..99.tmp` |
| saveSkill 只回报写入成功 | 保留原解析结果；非法元数据可以落盘，但返回 null 且不列入技能 |
| 删除不存在的技能返回 false，不清理助手引用 | 原 deleteRecursively 对不存在路径视为成功，继续清理该名称引用 |
| 单文件写入 catch Throwable 后返回 false | 原 I/O 异常继续传播；原子保存仍使用原 Exception catch/回滚 |
| finally 与恢复操作用 runCatching 吞异常 | 保留原 finally/回滚次序和 Boolean 结果处理 |
| 助手目录以显示名作 UI key | 使用原目录路径 key；enabledSkills 仍用名称，不改持久化格式 |

## 代码测试

基线先将 tag 的 Manager/Paths 放入临时 JVM 测试夹具，只把 Context.filesDir 换为传入 File；原方法体不重写。
18 项真实临时目录测试通过，原详情 5 项/工具 4 项也通过。基线夹具已从源码树移除，最终测试直接调用
common Manager，18 项测试的场景与断言不变，仅构造改成 Path。详情/工具原 fake Store 改成真实文件夹具。

| 输入 / 步骤 | 预期结果 | 结果 |
|---|---|---|
| CRLF、中文、description/compatibility/allowed-tools，显示名与目录名不同 | 原元数据和正文保持；按目录读写，显示名不被当成目录 | JVM 基线/最终、详情与工具测试通过 |
| 缺少描述、无 SKILL.md、普通文件、相同显示名的两目录 | 无效条目不列出；非法 saveSkill 元数据可写但返回 null；重名元数据不被去重 | JVM 基线/最终通过 |
| 多文件覆盖、二进制附件 | 整目录替换，旧附加文件移除，新字节准确，无遗留 staging/backup | JVM 基线/最终与 iOS 原生通过 |
| staging 先写成功后遇到越界路径；缺 SKILL.md | 原目录保持，已写的 staging 被清理 | JVM 基线/最终与 iOS 原生通过 |
| 100 个 staging/backup 候选全被占用 | 原尝试次数用尽后返回失败，原目录不变 | JVM 基线/最终；iOS 测 staging 碰撞通过 |
| skills 根路径是普通文件；SKILL.md 本身为目录；单文件父路径被文件占用 | 保留原 false/null/异常与 exists 判断，不引入兜底逻辑 | JVM 基线/最终与相关 iOS 原生场景通过 |
| 空白/嵌套技能名、相邻目录前缀、绝对越界、允许的内部绝对路径、内部 `..`、空格/反斜杠文件名 | 与原 canonical 规则一致 | JVM 基线/最终、iOS 原生通过 |
| 外指符号链接，目标子文件尚不存在 | 解析已存在父路径的链接后拒绝越界 | JVM 基线/最终、iOS 原生通过 |
| 删除存在/不存在技能，多个助手引用，prune 幽灵名 | 只清理原名称规则指定的引用，保留其他设置/名称 | JVM 基线/最终通过 |
| 详情查看/保存/删除，修改 frontmatter name、读取越界相对路径 | 使用真实目录；拒绝改名；仍拒绝越界读取 | JVM 与 iOS 原生通过 |
| use_skill 默认正文/相对文件、未启用名称、不存在文件 | 内容/参数契约和原错误保持 | JVM 与 iOS 原生通过 |
| Markdown、标准 Java ZipOutputStream 包，大小写 SKILL.md、嵌套技能、二进制、ZIP 魔数 | 原导入规则保持，二进制不变，子技能不混入父技能 | 7 项 JVM 导入测试中的相应场景通过 |
| ZIP 两目录有相同 frontmatter 名称；后一个技能格式错误 | 按原排序顺序覆盖并去重返回名；后续失败不回滚已完成的前一个导入 | JVM 导入测试通过 |
| ZIP 越界或找不到技能；GitHub mock 下载中途失败 | 错误返回，原已存技能不被部分覆盖 | JVM 导入测试通过 |
| GitHub mock 成功，嵌套目录 | 请求 URL、Accept、原两次 SKILL.md 下载及附加文件保存保持 | JVM 导入测试通过，无实际外网请求 |

最终执行：

```bash
./gradlew :composeApp:jvmTest \
  :composeApp:iosSimulatorArm64Test --device 03C090DA-107B-4F9F-BCCD-8D5265D32820 --tests '*Skill*Test' \
  :app:testDebugUnitTest :composeApp:compileCommonMainKotlinMetadata :composeApp:compileKotlinIosArm64 \
  :app:assembleDebug :desktopApp:createDistributable --console=plain
```

最终共 **363 次测试执行**：composeApp JVM 284、指定 Pro Max 上的原生技能测试 19、Android app 单元测试 60，
失败/错误/跳过均为 0。不是 363 个不同场景，iOS 会再次执行共同测试。
原 Android SkillPathsTest 的 3 项移到 composeApp JVM，故 app 测试数由 63 降为 60，覆盖没有删除。
详细 suite 计数见 `baseline-tests.json`、`regression-tests.json`；三端最终产物指纹见 `build-artifacts.json`。

初次 iOS 路径测试发现 NSURL 在缺失子文件时未解析父目录链接，已经在窄 actual 中修正，未改 SkillPaths 规则。
初次原生运行使用 Gradle 默认设备；最终明确用 `--device` 指定用户要求的 Pro Max，不能把初次运行混作最终通过。

未做断电、磁盘满或第二次 rename 系统调用的定点故障注入；相关恢复方法体经源码相等性确认，实际错误测试覆盖
写 staging 失败、非法路径、缺文件及临时目录耗尽。没有为故障注入增加生产 Observer/Store 接口。
本项不改归档实现，ZIP 兼容性完整审计仍属第 17 项；不把本次导入测试等同于完整 ZIP 格式验证。

## GUI 与清理

三端均需要 GUI：系统选择器、Koin、文件树、选择器和冷启动是实际应用链路，代码测试不能代替。
实际模型/effort 由会话 turn_context 核验为 `gpt-5.6-terra/high`，记录见 `agents.json`。
Android 使用 android-cli，iOS 使用 Build iOS Apps 的 iPhone 17 Pro Max，桌面只使用独立 profile。
测试数据均为固定离线技能 ZIP 和 guide v1/v2，无真实模型请求，未读取钥匙串密钥。

| 操作 | 预期 | 实际结果 |
|---|---|---|
| 使用系统文件选择器导入固定技能 ZIP | 技能元数据及主文件、附加目录可见 | 三端通过 |
| 查看主文档与附加文件，将 guide 从 v1 编辑为 v2 后回读 | 主文档保持；附加文件可保存，内容准确 | 三端通过 |
| 助手扩展中勾选技能，冷启动后查看文件和开关 | 编辑内容与选择持久化 | 三端通过 |
| GUI 删除技能，冷启动新建助手详情页，再回读实际目录和设置 | 技能目录、可用列表及本轮名称引用均清理 | 三端通过；不能仅凭初始空列表认定成功 |

三端采用先完整流程、后冻结最终包补验的方式。最终包的最后改动只恢复详情读取时的 `resolveSkillFile` 边界并清理
imports；Android 最终包另做导入、目录展开、附加文件读取和删除复核，iOS/桌面最终包实际回读 v2、检查开关和删除。
不把较早包的完整操作说成全部在最终包上重跑。Android APK、桌面实际 composeApp JAR，以及 iOS 主程序/业务
debug dylib 的哈希均已核对；iOS 的 Kotlin framework 为静态链接。

操作与原始证据见 [Android 报告](android/验证报告.md)、[iOS 报告](ios/README.md)、
[桌面报告](desktop/GUI-验证报告.md)。保留的 **30 张原始截图**均由父 agent 逐张复核；截图哈希见 `screenshots.json`。
iOS 的系统文件选择器未提供可用 elementRef，使用 Build iOS Apps 插件自带 AXe 发送真实坐标触摸；
编辑时也用该能力定位光标并操作屏幕键盘。固定 ZIP 经过一次 localhost 下载，记录见 `local-fixture-download.json`。
桌面使用绑定本轮 PID 的原生 AX/CGWindow helper；没有用默认 profile 或操作既有桌面实例。

GUI 中还观察到旧助手详情页不会即时刷新技能目录列表。源码核对表明，tag `2.4.5`、本项前的 Catalog 版本及
本项后的 `AssistantDetailVM` 都只在 `init` 中列举技能一次；已有页面可继续显示旧列表，助手勾选设置则单独订阅
settings flow。本项保留该行为，以新建 VM / 冷启动后的列表和实际落盘引用验证导入、删除结果，不顺带加刷新逻辑。

Android 早期最终包空列表截图与后续实际目录复核不一致，该截图已从“清理通过”依据中撤下；重新通过真实 GUI
确认删除，再检查目录不存在、全部助手引用为零及冷启动列表为空。没有直接删除技能文件来掩盖结果。

三端测试技能和本轮引用均已清理。Android 私有偏好逐键比较仅 `launch_count` 变化，未恢复其他字段；
iOS 核对范围是本轮名称引用计数归零，整体偏好字节不同，未完成其他字段逐键等价核验，也未整体覆盖偏好。
桌面独立 profile、移动端 ZIP 复制/下载件、私有备份和辅助脚本已删除；本轮应用进程、Android AVD、
localhost 服务和防休眠进程已结束。iPhone 17 Pro Max 原本已开机，继续保持开机。
本项没有增加临时生产日志，没有读取模型密钥或发送真实模型请求。
清理计数、检查范围和进程状态汇总见 `cleanup.json`。

下一项建议是第 14 项 B09：收敛附件目录约定及删除接线，先验证既有 `upload` 和迁移新增目录中的样本、fork
独立性及备份文件集合，再修改接线；已有数据的兼容读取与迁移必须明确后再处理。
