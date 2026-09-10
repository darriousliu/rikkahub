# CMP 多余改动分项回退

基线：tag `2.4.5`（`5f39f1c1d2298cd88ce908a5a0858d4830885a6b`）。
回退起点：`006ee55dbbcd0366b490c774b7c8e195ba7043bd`。
依据：[完整审计](cmp-minimal-change-audit-2.4.5.md)及其 E/B/X 编号。

用户本轮要求：从简单到困难逐项收敛；每项先明确验证步骤和预期结果，优先代码测试，需要 GUI 时由
`gpt-5.6-terra` 子 agent 在受影响平台验证；清除临时调试内容后提交，再提出下一项建议。
用户补充指定：iOS 使用 Build iOS Apps 插件操作 iPhone 17 Pro Max 模拟器，Android 使用 `android-cli` 技能操作模拟器。

本记录中的“回退”指恢复原类、原方法和原行为，同时保留 common 必需的依赖替换与平台实现。
混合提交不做整条 `git revert`。Ktor + 平台 engine、日期/编码 util、真实 OS 边界、已确认的独立修复继续保留。
不把旧 Java/Android API 放回 common，也不为删除一个接口再增加另一个业务接口。

以下是按改动范围、依赖和验证成本估计的执行顺序。它覆盖审计的 17 组明确额外抽取、10 组混合改造和 X01
脚手架；混合组只收敛其多余部分。大项可以拆成连续的小提交，但每个提交均须留下可运行的版本。
后续若发现新的前置依赖，先更新记录并说明原因。

## 从简单到困难的顺序

“三端”指 Android 模拟器、iOS 模拟器和桌面。表中的 GUI 是实施前判断；只有实际改动证明接线未变且代码
覆盖充分，才能写明依据后免除。未验证的平台、真实协议或系统链路单独记录，不借用其他平台的通过结果。

| 顺序 / 难度 | 收敛范围 | 代码验证步骤 | 预期结果 | GUI 验证安排 |
|---|---|---|---|---|
| **01 / 很低** | **E12：移除 ConversationEntityMapper，三个方法归位** | 在改动前后用同一组真实 Room 测试保存、更新、读取、分页；与 tag 方法体比较，白名单仅时间 API 替换 | 字段、分支、空值、时间精度、异常与摘要一致；无 mapper 和转发调用 | **免 GUI**：本项只有方法原样归位，构造参数、调用入口、UI 和平台接线均不变；见下方记录 |
| 02 / 低 | E13：FolderPersistenceMapper 归位，恢复直接 ConversationDAO 依赖 | Room 测试创建、重命名、删除、列表；同文件夹多会话与无关文件夹同时准备；验证删除文件夹前清空关联 | ID、时间、排序保持；删除文件夹后会话仍在且移至未归类，无关会话不变 | 恢复构造参数涉及 DI：三端创建、移动、删除文件夹，重进页面检查结果 |
| 03 / 低 | E14：收敛 RequestLogSink / RequestTimeSource / RequestTimeMark | 原拦截器入口测试关闭日志、正常响应、重复响应头、请求失败；时间断言使用范围而非假定精确毫秒 | 请求和响应对象、头/体及异常传播保持；关闭日志不额外读 body；只使用已有日志和时间 API | 若生产调用与日志入口原样保留，可代码免 GUI；若日志写入链路不能在测试中覆盖，Android 日志页实测 |
| 04 / 低 | E16：McpTokenPolicy 判断放回 McpOAuthCoordinator | 通过 Coordinator + Clock + HTTP mock 验证未启用、无 refresh token、无 access token、未到期、60 秒边界、无 expires_in | 刷新次数、请求内容及 expiresAt 与原判断一致；不加入保护或请求合并 | 仅私有判断归位且 OAuth 接线不变时可免；不把 HTTP mock 当系统授权回调验证 |
| 05 / 低至中 | E15：Vertex token Provider 直接用 Ktor，去掉二次 HTTP Transport/DTO | Ktor MockEngine 检查 token URL、form、JWT 声明、成功/错误/取消/缓存；平台签名契约测试 | 保留原参数、缓存过期与异常语义；RSA actual 保留 | 网络与签名接线若未变可代码免 GUI；不请求真实付费服务 |
| 06 / 中 | E05：StatsRepository / StatsQueries 逻辑回到 StatsVM | 固定日期/时区和 Room 数据，核对各 token 计数、会话数、热力图边界与空库；测试 VM 原入口 | 页面统计值与原 DAO/VM 行为一致，启动次数来源不变 | 三端打开统计页、切换后返回；核对种子数据与显示结果 |
| 07 / 中 | X01：删除 RikkaHubApp 演示 Status/Capabilities 壳 | 核对 RouteActivity 和 SharedProductApp 两个调用点，编译真实入口，验证产品内容仍由原入口提供 | 移除演示页及包装，正式导航、状态与平台宿主保持 | 两个调用点覆盖三端；验证 Android/iOS/桌面冷启动、导航、返回 |
| 08 / 中 | E06：BackupRepository / Settings Gateway 的转发及 VM 方法归位 | 设置持久化测试；以既有 transport 测试备份列表、成功/失败、更新时间、恢复选项 | 原设置 key 和读写时机保持；失败不假冒成功；这一项先不重写归档/网络传输 | 三端备份页保存设置、返回再进入；连接可用的测试备份端核对列表 |
| 09 / 中 | E10 + B03：统一提示词预览，收敛模板多层包装 | 原 TemplateTransformer 入口固定模板、日期、时区、Locale、空变量、错误输入；预览和实际生成上下文用同一组样本核对 | 预览与生成输入一致；Korte 替库保留，原模板语义保持，不新增另一套 renderer | 三端预览相同模板，核对时间/日期等结果；不需真实模型请求 |
| 10 / 中 | E03：移除 TranslationRuntime 及 Android/Shared 转发 | 原 TranslatorVM 入口测试参数、流式文本、失败、取消、设置切换；暂用已共享的原翻译方法，随第 24 项最终归位 | 模型/语言/提示词/回调与原流程一致，无第二份翻译业务 | 三端翻译页开始、停止、失败、重试；优先 mock/现有配置，必要时少量 DeepSeek 请求 |
| 11 / 中 | E04：McpRuntime 与资源镜像 DTO 收敛到原 Manager/Coordinator | 测试连接、工具和资源读取、资源参数/图片、授权状态传播；直接使用 SDK 类型 | 工具/资源内容、错误及连接状态一致，系统 OAuth callback 和 URI 适配保留 | 三端对测试 MCP 服务连接/断开、资源查看；涉及 OAuth 接线时完成各端实际回调 |
| 12 / 中至高 | B10：JavaScript executor 中多余测试 Observer/Transport/DTO | 使用原脚本样本核对同步 fetch、header/body、返回值、throw、超时、取消；覆盖对应平台引擎 | 保持脚本契约和线程/取消行为，保留 QuickJS-KT 的必要适配 | 脚本工具实际接线若改变，三端执行测试脚本并中止；只测不涉及外部凭据的本地请求 |
| 13 / 高 | E09 + E11：原 SkillManager 迁到 common，去掉 SkillStore/目录镜像业务 | 临时文件目录验证解析、导入、覆盖、删除、失败、重名和设置清理；保留原 staging/rename 与路径规则 | Android 与其他端只有一套技能业务；选择 key/元数据、原子保存行为保持 | 三端导入、查看、编辑、删除测试技能，重启后确认持久化；清理测试技能 |
| 14 / 高 | B09：附件目录约定和删除接线收敛 | 对原 upload 等目录及迁移后已有目录构造样本，验证读取、复制、fork 独立性、删除及备份文件集合 | 恢复原文件约定；已有数据仍能访问，删除只影响目标，备份包含其附件；共用文件 util 保留 | 三端发送本地附件、fork 后删除源会话、重启再读取；若已用新目录产生真实数据，先明确兼容迁移方式，禁止直接删目录 |
| 15 / 高 | E08：本地备份导入/导出业务回到原 BackupVM | 固定 ChatBox/CherryStudio/原生备份样本测试助手映射、去重、设置、时间、解析失败与文件结果 | 同一套原导入业务，文件选择/读写留在平台边界 | 三端通过系统选择器导入样本、导出并重新读取；验证取消文件选择 |
| 16 / 高 | E07：统一原 S3Sync/WebDavSync，去掉双份备份与归档编排 | 测试端验证请求签名、路径、列表、上传/下载/删除、归档内容、恢复、失败及取消；接续第 14/15 项文件契约 | 保留原同步与恢复顺序，一套业务配 Ktor/平台文件能力；不会漏附件或提前更新时间 | 三端各完成一次备份—删除测试数据—恢复；覆盖启用的 S3/WebDav 协议，未实测协议单列 |
| 17 / 高 | B06：评估并替换非必要手写 ZIP 内部实现 | 先确认成熟库或平台实现满足旧格式，再用历史样本、Unicode 路径、空目录、截断包、大文件边界双向互读；保留路径安全测试 | 对用户旧包兼容；不因迁移新增 ZIP64 等限制；保留窄归档 API 和已确认的安全修复 | 使用新归档实现完成受影响平台实际备份/恢复；不能仅靠自产包互读判定兼容 |
| 18 / 高 | E02：ImgGenVM 原业务共享，移除双份 ImageGenerationRuntime | mock 图片生成响应，验证原参数、分页、落盘、数据库、失败/取消、删除；覆盖图像编码 actual | 请求和历史记录行为保持，图像业务单份、平台只处理实际 I/O/编解码 | 三端生成/查看/保存/删除测试图片；优先固定图片 mock，避免付费图像请求 |
| 19 / 高 | B07：通知 Policy/Coordinator 中业务归回原 Manager | 原事件序列测试前后台、流式节流、完成、错误、取消、会话切换；Presenter 平台边界保留 | 原通知时机、替换/清除规则一致，避免新增策略改变生命周期 | 受影响平台前后台切换、通知出现/更新/消失及点击导航；不支持端明确说明 |
| 20 / 高 | E17：收敛 WebServerController/Runtime 双状态及新增规则 | 原 Manager 入口测试启动、停止、重复调用、端口占用、配置更改、失败；HTTP 实连与并发行为按 tag 核对 | 真实 engine/服务发现/前台服务边界保留，状态和原生命周期一致 | 对提供服务器能力的平台实际启动、浏览器连接、后台、停止与重启；无 engine 的平台只核对既有能力呈现 |
| 21 / 很高 | B05：TtsSchedulingStore 业务回归 TtsController | 可控播放器验证入队、分段、缓存、顺序、停止、重复播放、错误和并发；保留原缓存/取消契约 | 容器适配限于平台差异，无新增业务 Store；不会串音、漏段或停止后继续播放 | Android/iOS/桌面实际音频播放、暂停/停止、快速切换及生命周期验证 |
| 22 / 很高 | B04：审查 AtomicSnapshotMap，自研并发机制只在证据充分时替换 | 各调用点并发契约测试、重入/递归创建、取消、失败重试及压力测试；与原容器语义比较 | 通用 util 可保留；若简化其内部，必须保持原子性/可见性且不恢复已修复的自锁/ANR | 覆盖聊天切换、MCP 连接/授权等实际调用链；三端生命周期运行验证 |
| 23 / 很高 | B02：聊天页面整块 platform 接口收窄到系统操作 | 将可共享原 UI 原样归位；测试输入状态、附件、选择、操作菜单与事件流，不改布局/交互设计 | common UI 共用，平台保留权限、文件选择、键盘等具体系统功能 | 三端输入、附件、发送、选择/编辑、菜单、导航和键盘；逐项留截图/操作证据 |
| 24 / 最高 | E01 + B01 + B08：统一 ChatService/GenerationHandler，移除 SharedChatRuntime 与剩余重复业务/DI | 对照 tag 建立编辑分支、重新生成、空会话保存、引用回收、取消收尾、流式转换、工具循环、异常与持久化测试；已有 Generator 方法原样归位；共享原业务 module | 恢复一套原聊天业务，真正不可共享的文件/系统操作由窄边界提供；消除双 runtime 导致的行为分叉和重复 DI | 三端完整聊天链路和生命周期验证，重点覆盖上述历史分叉；优先测试 provider，必要时经授权的少量真实模型请求 |

第 22 项属于“需要证据才替换”的混合部分：若现有 util 的实现确有必要，应保留并说明，不能为减少类数量
破坏并发语义。第 17 项同样先验证依赖可行性，不能换成另一套手写归档实现。第 24 项中的 DI 清理随原业务
归位逐步完成；提前删除整套 module 会让后续各项失去可运行入口。

## 每项交付方式

1. 说明具体原方法、当前多余结构和本次边界；必要依赖替换与平台能力单独列明。
2. 尽可能先跑改动前的契约测试，再用同样数据测改动后。修改共同代码时检查受影响平台编译。
3. 记录每个场景的操作/输入、预期、实际结果和未覆盖范围。编译通过不等于 GUI 或真实协议通过。
4. 需要 GUI 时，显式委派 `gpt-5.6-terra` 子 agent，提供构建版本、测试数据和预期；同轮核对证据。
   启动或恢复后先核对实际模型配置，不能仅凭最初的创建参数认定模型未变；恢复不能保留时，重新创建指定模型的子 agent。
5. 临时日志只能记录验证必需信息；清除调试代码及临时数据后，复查最终 diff，再用中文 conventional commit 提交。
6. 报告本项提交与结果，给出下一项范围。出现新的行为选择或既有用户数据兼容歧义时及时询问，不擅自改业务规则。

凭据沿用用户授权的 macOS 钥匙串 service `dev.rikkahub.gui.deepseek`，仅实际请求确有需要时读取。
不把密钥写入文件、截图、Gradle/应用日志或 Git；测试优先使用 mock 和已有测试数据。

## 第 01 项：ConversationEntityMapper

状态：**已完成**。回退前后七项契约测试均通过；回退后 `composeApp:jvmTest` 共 180 项通过，
common、JVM、Android、iOS 真机目标及 iOS 模拟器目标编译通过。
命令、覆盖范围及源码等价复核见 [第 01 项验证记录](evidence/cmp-rollback-01-2026-09-09/verification.md)。
提交：`c64962ed`（`refactor(cmp): 撤销会话实体映射的额外抽取`）。

生产改动仅为 `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/data/repository/ConversationRepository.kt`：

- 删除 `ConversationEntityMapper` 和 `entityMapper` 属性。
- 将 `conversationToConversationEntity`、`conversationEntityToConversation`、`conversationSummaryToConversation`
  的方法体恢复到 tag 中的位置、名称和参数。
- 保留 `kotlin.time.Instant` 的 `toEpochMilliseconds` / `fromEpochMilliseconds` 替换。
- 既有 Room 事务、文件清理边界、数据库读取异常策略保持现状。本项不承担其他审计项的修复。

新增 `ConversationRepositoryPersistenceTest`，复用现有 Room 内存库、BundledSQLiteDriver 和 FTS 初始化方式，
直接测试原 Repository 入口。没有为测试新增生产接口或 mock Repository。

| 场景 / 验证步骤 | 预期结果 | 回退前 | 回退后 |
|---|---|---|---|
| 插入包含全部持久字段和两个分支的会话，再更新后读取 | 元数据、UUID 集合、Unicode/换行、节点和选中分支不丢失；临时 newConversation 不落库，旧 nodes 列仍是 `[]` | 通过 | 通过 |
| 直接写入旧字段格式与空节点后读取 | JSON/毫秒字段正确解码；空节点被过滤，非空节点及 selectIndex 保持 | 通过 | 通过 |
| null 和空字符串写入后检查原始列，再读取 | 可选字符串/文件夹写 `""`、集合写 `[]`；读取可选字符串为 null | 通过 | 通过 |
| 写入 epoch 前与纳秒时间，再读取 | 维持原毫秒精度，包括 epoch 前的向下取整 | 通过 | 通过 |
| Base64 消息分别尝试更新与插入 | 原 IllegalArgumentException 仍抛出；旧数据不变，新会话不写入 | 通过 | 通过 |
| 将已存建议字段改为非法 JSON 后读取 | 原解码异常继续向调用方传播 | 通过 | 通过 |
| 分页、续页、文件夹、未归类与搜索查询 | 摘要 ID/助手/标题/置顶/时间/文件夹保留，正文及详细字段不被加载，顺序与页边界正确 | 通过 | 通过 |

GUI 决策：本项免 GUI。三个纯转换方法归位；Repository 构造和调用方、DAO SQL/schema、平台数据库构建、
文件删除接线、界面状态/导航、资源及生命周期均未改动。七项 Room 代码测试覆盖被移动逻辑，另做受影响平台
编译。此豁免不代表 Android/iOS 实库、GUI 或其他审计项已经通过验证。

第 02 项建议：`FolderPersistenceMapper` 及 `clearConversationFolder` 回调。先恢复原文件夹转换方法和直接
`ConversationDAO`，用 Room 验证文件夹操作及删除后会话归属，再由 Terra 子 agent 核对三端 DI 与页面链路。

## 第 02 项：FolderPersistenceMapper 与 DAO 回调

起点：`c64962ed`。状态：**代码与功能验证完成**，iOS 输入覆盖限制和桌面执行模型偏差见下文。
提交：`3c8a274e`（`refactor(cmp): 撤销文件夹映射与DAO回调抽取`）。
回退后 JVM 28 类、187 项测试通过，新增文件夹测试 7 项回退前后均通过。
命令和实际覆盖范围见 [第 02 项验证记录](evidence/cmp-rollback-02-2026-09-09/verification.md)。

`FolderRepository.kt` 整个文件恢复到 tag 的结构：原 `createFolder` 方法体、文件级私有 `toFolder` / `toEntity`
扩展、直接 `ConversationDAO` 依赖。仅替换时间 import、`Instant.now()`、毫秒读取及转换 API；不再需要 mapper
类、mapper 属性和用于该包装的 Clock 构造参数。Android `RepositoryModule` 与 `sharedProductModule` 的对应
单条 Koin 绑定恢复直接注入 DAO。没有调整 DAO SQL/schema、VM 行为或界面交互。

| 场景 / 验证步骤 | 预期结果 | 回退前 | 回退后 |
|---|---|---|---|
| Repository 创建 Unicode/换行、空串、空格及同名文件夹，重新读取 | 原样保留名称，ID 独立、sortIndex 为 0、时间在调用区间且落库为毫秒；不新增 Repository 校验 | 通过 | 通过 |
| 写入旧格式、epoch 前时间、不同排序和助手的文件夹后查询 | 全字段正确恢复，按 sortIndex/createAt 排序且助手隔离；不存在的 ID 返回 null | 通过 | 通过 |
| 重命名目标，再对不存在的 ID 重命名 | 只改目标名称，原 ID/时间/排序与其他文件夹保持；不存在的 ID 不产生新记录 | 通过 | 通过 |
| 删除含两个会话的文件夹，另准备其他文件夹和未归类会话 | 目标文件夹删除，原会话正文/分支/元数据完整且移至未归类，无关数据保持 | 通过 | 通过 |
| 删除不存在但仍有会话引用的文件夹，重复删除 | 沿用先清空引用的原逻辑，不新增“文件夹不存在即返回”判断 | 通过 | 通过 |
| 用内存库 trigger 使 clearFolder 失败 | 异常继续传播，文件夹和会话归属保持 | 通过 | 通过 |
| 用内存库 trigger 使 deleteById 失败 | 异常继续传播，文件夹仍在，但先前清空归属的写入保留；不新增跨两条 DAO 调用的事务 | 通过 | 通过 |

GUI 决策：**需要**。构造依赖和 Android/非 Android Koin 接线改变，委派 `gpt-5.6-terra` 子 agent 在三端
操作创建、重命名、移动测试会话、删除文件夹及重进页面/重启。预期会话保留并返回未归类，无关数据不变，
无依赖注入错误。只使用专用测试数据，不需模型请求或读取钥匙串；验证完清理测试数据。

实际：Android 上述场景通过；iOS 创建、重命名回调、移动、删除及重启通过，但精确名称的纯 GUI 输入重试
受自动化工具限制（停止应用后曾修正测试记录），没有把该项标为通过。桌面此前因 Mac 锁屏受阻；用户解锁后
已通过创建、精确重命名、移动、删除、目标会话返回未归类、对照数据保持和冷启动验证。三端测试数据、临时日志
与工具均已清理。各平台 GUI 使用空会话，正文与分支完整性由 Room 测试覆盖。

执行模型偏差：Android/iOS 为 Terra；桌面恢复后的实际上下文变为 `gpt-6-astra / max`，父 agent 未在恢复时
及时发现，未遵守用户指定低成本模型的要求。该次桌面补验据实记录为 GPT-6，后续按上面的启动/恢复核验步骤执行。

第 03 项建议：撤销 `RequestLoggingInterceptor` 的 `RequestLogSink`、`RequestTimeSource`、`RequestTimeMark`
等额外测试接口，保留必要的 common 日志/时间 API 适配。先用请求拦截器测试覆盖日志关闭、请求/响应正文、
异常原样传播和耗时范围；若日志实际应用链路不能由测试覆盖，再由 Terra 补充 GUI。执行结果见下一节。

## 第 03 项：RequestLogSink / RequestTimeSource / RequestTimeMark

起点：`3c8a274e`。状态：**验证完成，已签名提交 `e5c363e6`**。
生产代码只修改 `RequestLoggingInterceptor.kt`，增加 6 行、删除 44 行，净减少 38 行。
三个专用接口、两个转发对象以及包装用的内部构造参数归零；直接调用现有 `Logging` 和
`TimeSource.Monotonic`，恢复 tag 中的类结构、方法体位置和 `startTime` 名称。
保留 common 日志包与现有单调计时选择（独立提交 `614f3f34`）；Ktor 插件仍承担必要的替库能力。

同一组 10 项测试在回退前后均通过；回退后全部 app JVM 测试共 10 类、63 项通过，Android APK 构建通过。
测试使用原无参构造、真实 Logging 和 OkHttp Chain，并通过本地 HTTP 服务检查真实网络拦截器往返，
没有为测试添加新的生产接口或依赖。

| 验证步骤 | 预期结果 | 实际 |
|---|---|---|
| 日志关闭/开启、同实例切换及请求途中关闭 | 实时读取开关，条数正确；关闭不额外序列化，进行中关闭不写最终日志 | 回退前后通过 |
| Unicode 内容、重复头、GET 和 400/429/500 响应 | 对象与内容保持，重复头沿用最后值，HTTP 错误状态仍返回响应；响应体不提前读取 | 回退前后通过 |
| IOException、无消息异常、取消、Error、请求序列化失败 | 原对象向外抛出，原异常边界与日志有无保持，不增加失败保护 | 回退前后通过 |
| 后续链实际等待、本地 HTTP 服务往返 | 耗时单位/范围正确，请求和完整响应保持；记录可从日志页所用的真实存储入口取得 | 回退前后通过 |

GUI 决策：**免 GUI**。原生产无参入口、network interceptor 注册、日志存储及日志页接线保持；
真实 Logging 和本地 HTTP 代码测试覆盖所改调用链。未改 UI、导航、生命周期或平台资源。本轮未启动子 agent。
完整命令、逐项步骤/预期、覆盖边界和清理记录见 [第 03 项验证记录](evidence/cmp-rollback-03-2026-09-10/verification.md)。

第 04 项建议：撤销 `McpTokenPolicy` 的业务判断抽取，将刷新判断和 `computeExpiry` 放回
`McpOAuthCoordinator`。执行结果见下一节；tag 中刷新判断位于 `ensureFreshToken` 内，没有独立 `needsRefresh` 方法。

## 第 04 项：McpTokenPolicy

起点：`e5c363e6`。状态：**验证完成，已签名提交 `b4314f7f`**。
删除 `McpTokenPolicy.kt`，将刷新前置判断放回 `ensureFreshToken`，将私有 `computeExpiry` 放回
`McpOAuthCoordinator` 原位置。生产代码增加 20 行、删除 34 行，净减少 14 行。
构造参数改为已有标准类型 `Clock`，默认 `Clock.System`；保留现有秒转毫秒 API、Ktor 和平台 OAuth 回调实现。
没有重新增加 `needsRefresh` 或单次时间读取包装方法，没有改锁、请求、异常、取消或持久化流程。

同一组 11 项测试在回退前后均通过，只有夹具构造参数由 `McpTokenPolicy(clock)` 改为直接传 `clock`。
测试使用真实 Coordinator、OAuthClient、SettingsStore 和 SHA-256 actual；HTTP 为 Ktor MockEngine，
底层 Preferences 与浏览器回调为内存夹具，不使用真实服务或凭据。

| 验证步骤 | 预期结果 | 实际 |
|---|---|---|
| 未启用、缺少 token/端点/clientId，未到期或未知到期时间 | 沿用原前置判断；不请求、不写入、返回当前配置 | 回退前后通过 |
| 距到期 60,001 / 60,000 / 59,999 ms，以及空 access token | 60,001 ms 不刷新，60,000 ms 起刷新；空 access token 在具备刷新条件时刷新 | 回退前后通过 |
| 同实例推进时钟、再次传入旧配置、改用最新已存凭据 | 按当前时间与最新配置判断，不缓存旧时钟或旧凭据，不重复刷新已更新 token | 回退前后通过 |
| HTTP/SSE 两种配置刷新，核对 form、header 和序列化重读 | 原 resource、scope、client 字段保持；只更新目标 OAuth，其他配置及传输信息保持 | 回退前后通过 |
| 请求期间推进 5 秒；响应省略或返回非正/正 expires_in | 省略/非正保存 0；正值从响应时刻计算；刷新 token/scope 缺失沿用旧值，空串保持 | 回退前后通过 |
| 服务器已不在设置中，或 HTTP/解析/传输/取消失败 | 缺失项不插入；失败不重试、不写入并返回旧配置，保留现有取消处理 | 回退前后通过 |
| 通过既有回调接口夹具完成授权码交换 | PKCE/state/redirect/resource 参数保持；先保存授权元数据，再保存 token 与正确 expiry，关闭回调会话 | 回退前后通过 |

回退后全部 composeApp JVM 测试 29 类、198 项通过；common metadata、JVM、Android、iOS Arm64 和
iOS Simulator Arm64 编译通过，Android Debug APK 打包通过。

GUI 决策：**免 GUI**。本项只归位原判断/计算；唯一生产构造点 `McpManager` 仍使用默认参数，
OAuth 浏览器与系统回调、UI、导航、生命周期、存储实现及平台接线均未改动。
代码覆盖所改判断及刷新/授权两条到期时间保存链路；不把回调夹具视为操作系统 OAuth 验证。
没有启动子 agent、读取钥匙串或增加生产临时日志；临时 Gradle 输出已清理。
完整步骤、预期、命令、源代码对比与覆盖边界见 [第 04 项验证记录](evidence/cmp-rollback-04-2026-09-10/verification.md)。

第 05 项建议：撤销 Vertex token Provider 的二次 HTTP Transport/DTO 包装，回到 Provider 直接使用 Ktor。
保留平台 RSA 签名实现；先验证 token URL、form、JWT 声明、缓存过期和成功/错误/取消行为，再判断 GUI 需求。
执行结果见下一节。

## 第 05 项：Vertex token HTTP Transport / DTO

起点：`b4314f7f`。状态：**已签名提交 `c1b49204`**。
生产代码只修改 `ServiceAccountTokenProvider.kt`，删除 `ServiceAccountTokenTransport`、
`ServiceAccountTokenHttpResponse`、`KtorServiceAccountTokenTransport`，以及包装构造函数。
原 `fetchAccessToken` 直接持有并使用传入的 Ktor HttpClient，恢复原 `resp` / `body` 局部变量。
标准 `Clock`、`RsaSha256Signer` 及默认平台签名实现保留。增加 19 行、删除 45 行，生产代码净减少 26 行。

HTTP 表单方法体从包装原样归位，仅恢复局部变量名和原固定端点；同方法的其余语句未变。
保留缓存容器、5 分钟缓冲、请求开始时间计过期、空 token、错误正文及取消传播行为，没有加入重试、锁或请求合并。
测试新增一项 `commonTest` 依赖：仓库已有版本的 Ktor MockEngine；生产依赖不变。

新增 12 项 Provider 契约测试和 2 项平台 RSA 测试，同一组 14 项在回退前后分别于 JVM、Android host、
iOS 模拟器全部通过。仅测试夹具构造参数由 Transport 改为直接 HttpClient，输入与断言保持一致。

| 验证步骤 | 预期结果 | 实际 |
|---|---|---|
| 固定时钟，使用专用测试 PKCS#8 私钥请求 token | URL、POST/form、JWT header/claims 与无 padding 编码保持；签名符合 OpenSSL 独立结果 | 三个目标回退前后通过 |
| 变换 scope 顺序/重复项、邮箱、私钥，跨越 5 分钟边界 | 原缓存键与 JWT scope 顺序保持；有效缓存不重签；边界精确刷新 | 三个目标回退前后通过 |
| 缺失/非正/短 expires_in，及请求过程中推进时间 | 原默认 1 小时、短有效期和按请求开始时刻计算过期保持 | 三个目标回退前后通过 |
| 成功/空 token、HTTP 错误、缺失字段、解析/传输/签名失败和取消 | 原返回值、错误类型/正文与传播保持；不缓存失败、不自动重试 | 三个目标回退前后通过 |
| 两个并发缓存未命中请求，随后读取缓存 | 原来分别发送两次请求，完成后读缓存；不新增合并 | 三个目标回退前后通过 |
| GoogleProvider 使用默认构造进行两次模型列表请求 | 只换取一次 token；两次下游请求均携带 Bearer，邮箱去空格/PEM 转义接线保持 | 三个目标回退前后通过 |
| PKCS#8 的 LF/CRLF 与损坏输入 | 实际签名匹配 OpenSSL 测试向量，损坏输入失败 | 三个目标回退前后通过 |

回退后 ai 全量结果：JVM 94 项、Android host 144 项、iOS 模拟器 94 项，全部通过。
common/JVM/Android/iOS Arm64/iOS Simulator Arm64 编译、Android Debug APK、桌面 Kotlin 编译通过。

GUI 决策：**免 GUI**。请求构造原样归位；GoogleProvider 仍使用原默认构造和同一客户端，
平台 engine、RSA actual、UI、导航和生命周期接线均未变；真实 Provider 调用及 iOS Security 签名已有代码执行证据。
Android host 是在主机 JVM 上执行 Android actual，不等于 Android 设备加密验证；本项未操作三端 GUI。
没有启动子 agent 或读取钥匙串，专用测试密钥不对应任何真实账号；临时文件已清理。
完整步骤、预期、命令和覆盖边界见 [第 05 项验证记录](evidence/cmp-rollback-05-2026-09-10/verification.md)。

第 06 项建议：将 `StatsRepository` / `StatsQueries` 的统计逻辑归回原 `StatsVM`。
先用固定日期/时区和 Room 数据核对 token、会话数及热力图边界；涉及 VM/界面数据接线，
由显式指定 `gpt-5.6-terra` 的子 agent 验证 Android、iOS 和桌面统计页。执行结果见下一节。

## 第 06 项：统计逻辑归回 StatsVM

起点：`c1b49204`。状态：**已签名提交 `2466dfa8`**。

删除 `StatsRepository`、`StatsQueries`、`RoomStatsQueries` 和 `heatmapStartDate`；
`AppStats` 归回原 `StatsVM.kt`，VM 直接使用原 DAO 与 SettingsStore。
Android 和共享 Koin 入口改为直接构造 VM。7 个生产文件增加 72 行、删除 119 行，净减少 47 行。

与 tag 比较，`AppStats` 定义完全一致；整个 `loadStats` 方法仅替换 common 不可用的日期 API。
原 50ms 延迟、IO 范围、查询顺序、启动次数读取时机及快照行为保持。
标准 Clock/TimeZone 使用已有默认值，测试可传固定值；没有新增业务接口或时钟包装。

新增 7 项真实 Room/VM 契约测试，回退前后输入和断言相同；回退后全部 composeApp JVM 测试
30 类、205 项通过。common、Android、iOS Arm64、iOS Simulator Arm64 编译通过，
Android APK、桌面分发包及 iOS 模拟器应用构建成功。

GUI 决策：**需要三端验证**。VM 构造和 Koin 数据接线发生变化，编译及 JVM 测试不足以证明三个实际入口均能加载。
三端均由新建且实际上下文已核对为 `gpt-5.6-terra / high` 的子 agent 执行。
使用离线专用数据核对会话/消息/token、热力图、切换返回及冷启动，不发真实模型请求。
三端实际显示均为会话 3、消息 6、输入 124、输出 74、缓存 28，热力图两格深浅不同；
返回与冷启动后保持。Android 启动次数按原逻辑 5→6→7→8，iOS/桌面为 0。
三端按专用 ID 清理并通过无关行指纹与数据库完整性检查，GUI 恢复零统计、缓存卡片消失。
完整步骤、预期、源代码比较和结果见 [第 06 项验证记录](evidence/cmp-rollback-06-2026-09-10/verification.md)。

第 07 项建议：删除 `RikkaHubApp` 的 Status/Capabilities 演示外壳。
已查明仅 `RouteActivity` 与 `SharedProductApp` 两个实际调用点，均传入正式产品内容并立即返回，演示分支未使用。
下一项保留正式导航与平台宿主，删除冗余外壳，并对两个调用点覆盖的三端进行冷启动、导航和返回验证。

## 第 07 项：删除 RikkaHubApp 演示外壳

起点：`2466dfa8`。状态：**已签名提交 `cbb224b4`**。

删除共享 `RikkaHubApp.kt` 的 Status/Capabilities 演示 UI、导航选择函数和专用 `SharedEntryTestTags`。
`RouteActivity` 直接调用原 `AppRoutes()`；`SharedProductApp` 直接调用原 `ProductNavigationHost(...)`。
4 个生产文件增加 7 行、删除 180 行，净减少 173 行。

两个实际调用点原本始终传入 `productContent`，包装立即调用它并返回，未执行演示主题、状态或导航分支。
已逐文件核对仅删除这层包装与调整缩进；原主题、Koin、导航参数、生命周期效果保持。
Android 同名 `Application` 类和清单未动；真实功能使用的平台能力判断、expect/actual 和桌面 smoke 路径保留。

回退前后同一批现有测试全部通过：composeApp JVM 205 项、Android app 单元测试 63 项。
common、Android、JVM、iOS Arm64/iOS Simulator Arm64 编译及 Android APK、桌面分发包、iOS 模拟器应用构建成功。
没有新增只检查已删除包装的测试或测试依赖。

GUI 决策：**需要三端验证**。本项修改根 Composition 的调用层级，现有单元测试不能证明实际宿主、导航返回及草稿状态接线。
三端由新建且已核对实际模型为 `gpt-5.6-terra / high` 的子 agent 验证正式入口、统计/设置导航、页面往返草稿及冷启动。
三端实际通过：草稿往返保留，清空后同 profile 冷启动正常，未发送草稿未增加会话或消息。
Android 首次无设备未计通过，启动既有模拟器后由新的 Terra agent 完成重试；Android 实际设置覆盖为助手设置层级，
iOS 为偏好设置/界面偏好设置，Desktop 为偏好设置。测试实例、草稿和临时文件已清理，本轮启动的 Android 模拟器已关闭。
首次签名因 `1Password: failed to fill whole buffer` 失败；用户确认已解锁后沿用原签名设置重试成功。
完整步骤、预期与实际结果见 [第 07 项验证记录](evidence/cmp-rollback-07-2026-09-10/verification.md)。

第 08 项建议：把 `BackupRepository` 的转发和备份完成时间更新归回原 `BackupVM`，直接使用 `SettingsStore`，
删除 `BackupSettingsGateway`。保留必要的 WebDAV/S3 transport 与本地文件平台能力，先验证设置读取时机、列表排序、
成功更新时间及失败传播，再核对三端备份页。

## 第 08 项：备份转发与设置访问归位

起点：`cbb224b4`。状态：**代码及三端受影响接线验证完成，随本轮签名提交**。
iOS 文本输入未覆盖，见下方边界。用户已改用原生 ssh-agent，本仓库签名器随之切换为系统 ssh-keygen。

删除 `BackupRepository`、`BackupSettingsGateway`、`SettingsStoreBackupSettingsGateway`。
原 `BackupVM` 直接依赖 `SettingsStore` 和现有 WebDAV/S3 transport；网络方法和 `recordBackupTime` 归回 VM。
`BackupArchiveService` 及两端本地文件服务直接访问同一个 SettingsStore，Koin 删除包装注册并直接构造 VM。
8 个生产文件增加 78 行、删除 132 行，净减少 54 行；没有新增生产接口、依赖或业务工具函数。

本地导出的时间更新暂时原样内联在两个已有文件服务的原调用位置。这样保留 Android 的 IO 范围与失败删临时文件、
FileKit 已生成归档在时间持久化失败后保留的既有差异；第 15 项再连同完整本地导入/导出业务归回 VM。
没有提前统一该行为，也没有重写 WebDAV/S3、ZIP 或导入器。标准 Clock 从被删 Repository 移到实际持有方法的类，默认值不变。

新增 14 项 BackupVM/SettingsStore/真实 FileKit ZIP 契约测试，回退前后输入和断言完全相同。
覆盖列表状态/排序、异步设置更新与原 key、当前配置及恢复选项、成功/失败/取消、并发独立请求、完成时间读取时机、
SettingsStore 原有写失败语义、真实归档保存/恢复设置及导出时间顺序。包括“返回 false 但未抛异常仍记录时间”的原行为。
回退后 composeApp JVM 219 项、Android app 单元测试 63 项全部通过；common/各平台编译、APK、桌面分发包、iOS 模拟器应用构建通过。

GUI 决策：**需要三端验证**。VM 构造、Koin 和设置访问接线变化，须通过真实备份页验证设置写入、重新进入和冷启动后的持久化，
以及列表加载。使用本机只读 WebDAV/S3 固定响应服务，不访问真实云端、不读取钥匙串，不执行上传/恢复/删除。
桌面及 Android 两协议列表、配置持久化已通过。Android 需从系统界面临时授予本地网络相关权限，
测试字段及未授权状态已恢复；权限请求缺口属于既有平台接线问题，本项未顺带改动。
iOS 早期因锁屏及 Pro 的输入工具问题未完成。随后按用户指定，改用 Build iOS Apps 插件操作 iPhone 17 Pro Max。
文本输入工具仍未生效，采用本机假配置夹具，实际通过 GUI 验证两协议连接/列表，以及备份选项改变后冷启动重新进入的持久化，
再通过 GUI 恢复勾选。该流程验证本次 VM/SettingsStore 接线，不声称键盘文本输入已覆盖。
Max 两组假配置在应用停止后精确恢复，其他 44 项设置和未知 protobuf 字段保持；没有读取真实密钥。
旧 Pro 中本轮写入的假 WebDAV 字段已按记录恢复，其他 45 项设置及未知 protobuf 字段经比较保持不变；
这次程序清理不计为 GUI 验证通过。
完整步骤、预期及实际结果见 [第 08 项验证记录](evidence/cmp-rollback-08-2026-09-10/verification.md)。

第 09 项建议：收敛提示词预览与模板的多层包装，保留 Korte 替库；以相同模板、固定日期/时区/Locale、
缺失变量及错误样本核对预览与实际生成上下文，再由三端 GUI 验证模板预览。
