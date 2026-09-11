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
| 11 / 中 | E04：调用点直接使用 McpManager，删除 McpRuntime 和无调用点的资源 DTO/API | 真实 SDK 测试连接、工具同步/参数/图片/嵌入资源、错误、取消、授权状态 | 活跃方法体保持；闲置资源列表/读取没有原产品入口，直接删除；系统 OAuth callback 保留 | 三端连接/断开、工具开关、错误重试及 MCP 选择器；无资源查看入口，OAuth 平台接线未改 |
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

起点：`cbb224b4`。状态：**已签名提交 `07f91940`，签名校验通过**。
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

## 第 09 项：提示词预览与模板包装收敛

起点：`07f91940`。状态：**已签名提交 `90fc09ab`，签名校验通过**。审计 E10 + B03。

恢复原 `AssistantPromptPage` 直接调用 `TemplateTransformer`、原 `AssistantTemplateLoader` 查找方式；
删除独立预览 Runtime、ContextFactory 和模板的多层转发接口。使用 Korte 原生类型，保留当前 Pebble 兼容标签/过滤器、
RAW 输出和编译失败缓存清理。原消息时间、角色、非文本部分、异常行为保持。
共删除 10 个额外类型，生产代码净减少 129 行。Korte 替库及现有兼容标签/过滤器保留。

必要接线：Android 原有设置变化后清空模板缓存的行为继续保留，iOS/桌面补回同一行为。
共享宿主先创建原生模板 engine，再构造 SettingsStore 的失效回调，最后配置原 loader，避免新增回调接口或业务容器。

验证步骤及预期：回退前后运行相同模板契约样本，核对固定日期/时区/Locale、角色、多个文本部分、非文本元数据、
按助手 ID 查找、缺失模板、编译与渲染错误、缓存复用与失效、include/layout 和现有过滤器语义。
单独记录当前共享预览与生成上下文的差异；回退后预览回到原 Transformer 入口。
GUI 判断：**需要三端验证**。预览调用及缓存接线变化，须实际修改模板、核对预览、错误后改回有效模板、
页面往返/冷启动并恢复测试数据；使用 Terra 子 agent，Android 用 android-cli，iOS 用 Build iOS Apps 插件和 17 Pro Max。

实际结果：原 11 项契约样本回退前后通过，另加 1 项验证原时区读取位置的测试；全部 JVM 测试共 294 项通过，
common/各平台编译、Android APK、桌面分发包和 iOS 模拟器应用构建通过。
诊断确认此前 common 独立预览使用固定格式，而原 Transformer 使用本地化日期/时间；本项随原入口恢复本地化显示。
Android/iOS 的两条预览、页面往返、真实重置、同数据冷启动和无效模板后的真实重置恢复均通过。
两端精确键盘替换不可靠，A/invalid 使用单字段夹具准备，不声称键盘 A→B 编辑通过。
Android 测试助手已删除并关闭模拟器；iOS 测试助手原模板已恢复，但导航前未记录原选中助手，仍保留“GUI 回归助手”选中。
无关设置经复核保持；私有快照、临时日志和脚本已清理。
桌面在用户解锁后使用同一最终产物、新独立 profile 完成 GUI 粘贴 A→B、两条预览、页面往返、冷启动、
实际 Korte 错误、有效模板恢复和真实重置。7 张最终截图均经父 agent 逐张复核；首次未拍到预览的截图已移除。
桌面 `type_text` 的花括号输入问题未覆盖，未归因于应用；完整编辑通过 `sky.paste`，未程序写入模板夹具。
完整操作、预期、结果、构建标识及限制见 [第 09 项验证记录](evidence/cmp-rollback-09-2026-09-10/verification.md)。

提交后下一项：第 10 项移除 `TranslationRuntime` 及 Android/Shared 转发，恢复 TranslatorVM 直接访问 SettingsStore；
暂时复用既有共享翻译方法，保持参数、流式响应、异常/取消和各端执行上下文，完整方法归位留在第 24 项。

## 第 10 项：翻译 Runtime 转发收敛

起点：`90fc09ab`。状态：**代码、构建及三端 GUI 验证完成，测试环境已清理**。审计 E03。

删除 `TranslationRuntime`、`AndroidTranslationRuntime`、`SharedTranslationRuntime`；
`TranslatorVM` 恢复直接依赖 SettingsStore，暂用现有 TextTranslationGenerator 的原翻译方法。
原 GenerationHandler 的完整归位仍留在第 24 项。本项不改 Generator 方法体、语言映射、请求参数或原 VM 状态流程。
使用协程库原生 CoroutineDispatcher 保留 Android 的 IO 与共享端的 Default，避免新增另一层业务接口。

先用真实 SettingsStore、现有 SharedTranslationRuntime/Generator 和可控 Provider 测试 VM 原入口；
回退后保持场景和断言，只调整构造夹具。预期：懒订阅和设置 key 保持，输入在点击时捕获、语言在请求开始时读取；
普通/Qwen 参数、流式累计、空输入不取消、错误保留部分结果、重试、取消、替换请求和 VM 清理保持原行为。
继续运行既有 TextTranslationGenerator 测试及相关模块测试，并编译三端。

GUI 决策：**需要三端验证**。VM/Koin/调用接线改变，代码测试不能证明真实页面中的开始、流式结果、取消、
错误与重试、模型/语言切换。由实际核验为 Terra 的子 agent 操作；Android 使用 android-cli，iOS 使用
Build iOS Apps 的 iPhone 17 Pro Max，桌面使用独立 profile。优先使用本机固定响应服务，避免真实模型请求。

代码结果：8 项基线场景及断言回退前后保持相同并通过；另加原生 IO/Default 上游调度验证，回退后 VM 共 9 项，
既有 Generator 8 项通过。完整相关 JVM 测试 303 项通过，common/各平台编译、Android APK、桌面分发包与 iOS 应用构建通过。
生产代码增加 25 行、删除 98 行，净减少 73 行；原 Generator、GenerationHandler 和语言枚举逐字节保持。
三端由实际 gpt-5.6-terra/high 子 agent 完成翻译、语言切换、取消、错误、重试、页面往返和冷启动模型持久化，
25 张原始截图经父 agent 复核。本机固定服务共 17 次请求，三端取消均在 45 秒延迟完成前断开。
iOS 键盘遮挡通过仅清焦点的原生 LLDB 辅助处理，其他操作使用 Build iOS Apps；普通软键盘收起路径未覆盖。
iOS 取消后无原图，判断依据实际运行时快照和服务断开记录；未把取消前截图作为取消后证据。
Android 临时本地网络权限已恢复拒绝，三项测试设置已精确恢复并回读；iOS 整个设置文件恢复原哈希，
Android 仅正常启动计数变化。独立 profile、测试进程、服务、私有快照和临时日志/文件已清理。
完整记录见 [第 10 项验证记录](evidence/cmp-rollback-10-2026-09-10/verification.md)。

本项已提交：`f6010b2619ba6b59705aca93d0f359fd42035433`，SSH 签名已验证。

下一项建议：第 11 项收敛 `McpRuntime` 与资源镜像 DTO，使调用点直接依赖原 `McpManager`。

## 第 11 项：MCP Runtime 与闲置资源 API 收敛

起点：`f6010b2619ba6b59705aca93d0f359fd42035433`。状态：已完成回退、代码/三端 GUI 验证和独立测试资料清理。
提交：`28568f63`（`refactor(cmp): 移除 MCP Runtime 与闲置资源包装`）。

原 McpManager 已是 common 实现，不需要另一层 McpRuntime。页面、McpPicker 和 SharedChatRuntime 直接
注入 Manager；原客户端可用性判断恢复 getClient(config) == null。删除没有产品调用点的 McpResource/
McpResourceContent，以及 Manager/Registry 的 listResources/readResource 闲置方法。tag 2.4.5 也无资源入口，
因此修正本表原先误列的“资源查看”验证要求。工具结果中的 SDK 嵌入资源 JSON 转换仍保留。

生产改动为 8 文件，增加 24 行、删除 177 行，净减少 153 行。保留的 Manager 方法体和 Registry 其余内容
逐字节核对通过；SharedChatRuntime 只改依赖类型/名字，完整业务归位仍在第 24 项。OAuth、图片落盘和
Android ChatService 没有改动；未增加依赖、平台接口或业务抽象。

代码步骤与预期、基线和构建证据见 [第 11 项验证记录](evidence/cmp-rollback-11-2026-09-10/verification.md)。
新增 7 项真实 SDK 契约测试在回退前后均通过，原 14 项 MCP 测试继续通过。完整测试共 310 项通过；
其中既有 OAuth 测试补上等待 SettingsStore 发布新 token 的同步点，未改变断言或生产授权行为。

三端由实际 gpt-5.6-terra/high 子 agent 完成连接、工具描述/开关、服务器启停、失败恢复、选择器及冷启动验证；
22 张原始截图经父 agent 复核。Android 使用 android-cli，iOS 使用 Build iOS Apps 的 iPhone 17 Pro Max；
桌面使用独立 profile，捕获流异常后用原生 AX/窗口截图完成。三端均未发送真实模型请求。
Android 和 iOS 测试偏好已恢复并回读；独立桌面 profile、服务、辅助脚本、私有快照和临时日志已清理。
修正夹具后 51 次 JSON-RPC POST 的固定请求头均保持。默认桌面额外实例的关闭被自动审批拒绝，
已保留并记录处置边界，未把未关闭的实例标为已清理。

下一项建议：第 12 项检查 JavaScript executor 中的测试 Observer、Transport 和镜像 DTO，按同一原则收敛，
保留 QuickJS-KT 必需的平台能力与原脚本错误、超时、取消和 fetch 语义。


## 第 12 项：JavaScript HTTP 包装与空观察器

提交：`6ac2e6e9db098250a718dd2b4a43931135c79b92`，SSH 签名已验证。

起点：`28568f63f25e678ec555b20c5c175bd80e9dee57`。状态：回退、代码/三端 GUI 验证及全部本轮临时资料清理完成。

移除空 RuntimeObserver/NoOp、HTTP Transport/Call 及镜像请求/响应，共 7 个类型。执行器直接接收已有
HttpClient，原 Ktor 请求方法体归入同名 QuickJSFetch；活动请求以原生 Job 保存并保留既有取消流程。
单个 QuickJS-KT 兼容执行器及结果、console、超时语义继续保留；传给 JavaScript 的 HttpResponseDto 与
fetch polyfill 原本就在 tag 中，不作多余抽象删除。未添加生产依赖、平台接口、锁、校验或空值兜底。

生产代码共 5 文件，增加 102 行、删除 191 行，净减少 89 行。fetch polyfill 与 tag 逐字节一致；
bodyForMethod 的方法体保持，CustomJsSearchService 仅更换构造接线。新增 13 项真实 QuickJS/Ktor 契约测试，
JVM、iOS 原生、Android 设备的前后测试体/断言完全相同，仅夹具去掉 Transport 构造。最后完整回归共
351 次跨平台测试执行通过，失败/错误/跳过均 0（包含相同测试在不同平台的执行次数）。

代码步骤与预期、平台打包、GUI 实际范围及清理结果见
[第 12 项验证记录](evidence/cmp-rollback-12-2026-09-11/verification.md)。三端采用 gpt-5.6-terra/high，
Android 使用 android-cli，iOS 使用 Build iOS Apps/iPhone 17 Pro Max，桌面使用独立 profile。
真实模型聊天工具和抓取脚本 GUI 不在本项通过范围；它们的业务方法/入口未改，无 fetch 执行与共同契约由
真实引擎代码测试覆盖。改动构造接线的自定义搜索页验证成功、错误重试、离页取消、重入及冷启动。

三端最终各 6 次真实 GUI 请求，共 18 次，固定方法、平台头和参数一致；取消分别为 Android 10.926 秒、
iOS 10.149 秒、桌面 1.279 秒。18 张原始截图经父 agent 复核。移动端偏好与临时权限已恢复，独立桌面 profile、
测试服务/日志/原生 helper 已清理；本轮全部测试进程和子 agent 已结束，未读取模型密钥或发送模型请求。

下一项建议：第 13 项 E09 + E11，收敛 SkillManager/SkillStore 与目录镜像业务。先对照 tag 恢复原 SkillManager
职责，将能通用的原方法体迁入 common，仅保留文件系统平台适配；用临时目录验证解析、导入、覆盖、删除、
失败和设置清理，再做三端导入/查看/编辑/删除及冷启动验证。


## 第 13 项：SkillManager 与助手技能目录收敛

起点：`6ac2e6e9db098250a718dd2b4a43931135c79b92`。审计 E09 + E11。状态：代码回退、测试、三端 GUI 与测试资料清理完成。

提交：`6211dd359e055d157292330bb6832180501ff1c9`（`refactor(cmp): 将技能业务收回原 SkillManager`），SSH 签名已验证。

将 tag 原 SkillManager/SkillMetadata、SkillPaths 迁入 common；删除 SkillStore、SkillSummary、StoredSkillFile、
AndroidSkillStore、FileKitSkillStore、AssistantSkillCatalog、AssistantSkillMetadata、AndroidAssistantSkillCatalog，
共 8 个额外类型。调用点直接依赖原 Manager/Metadata；文件树恢复原目录递归，保留已经修复的显示名/目录名映射。
助手选择仍按 frontmatter 名称持久化，列表项 key 使用原技能目录路径。

文件类型用已有 kotlinx-io 的 Path/SystemFileSystem 替换 java.io.File，复用同步文件 util 的原 Boolean/异常约定。
canonicalFile、renameTo 使用窄 expect/actual：Android/JVM 调用原 java.io.File，iOS 使用系统 URL/rename；
没有新增业务接口。FileKit 保留在原应用目录和系统文件选择边界。staging/backup 的 100 次命名尝试、rename、
回滚、finally 清理顺序以及 parse/delete/prune 方法体保持；源代码机械替换后相等性另有证据。

验证基线：临时 JVM 夹具仅将 tag Context.filesDir 改为传入 File，原 18 项真实目录契约均通过；
既有详情页 5 项、工具 4 项测试通过。回退后同一组 Manager 测试只改构造夹具；原 mock Store 测试改成真实目录，
继续覆盖显示名不同、工具文件读取与错误、名称不可修改。另补原生路径/保存和真实 ZIP/GitHub mock 导入测试。

GUI 决策：需要三端验证。Manager/Koin/技能选择器及文件 I/O 接线变化，单元测试不足以证明系统选择器、
页面编辑、选择持久化与重启后的实际应用链路。由 gpt-5.6-terra 子 agent 在 Android CLI、Build iOS Apps 的
 iPhone 17 Pro Max、独立桌面 profile 中导入离线 ZIP、查看/编辑辅助文件、勾选技能、冷启动、删除与检查引用清理。
不需要模型请求或密钥。

结果：生产源码 29 个路径变动（含移动前后路径），增加 501 行、删除 758 行，净减少 257 行。Manager/Paths
机械替换后与 tag 原方法体相等，frontmatter parser 原样保持。最终 363 次测试执行通过（JVM 284、Pro Max 原生
技能测试 19、Android app 单元测试 60），失败/错误/跳过均 0；三端构建及 common/iOS 编译通过。

三端由实际 `gpt-5.6-terra/high` 完成导入、查看、编辑保存、助手启用、冷启动、删除及落盘清理检查。
采用完整流程后追加最终包补验，实际范围见 [第 13 项验证记录](evidence/cmp-rollback-13-2026-09-11/verification.md)。
30 张原始截图经父 agent 复核。旧助手 VM 只在初始化时读取技能列表是 tag 已有行为，保留并用冷启动复核。
Android 早期空列表未被单独作为清理通过依据，重新通过 GUI 删除并检查实际目录及全部引用为零。

Android 偏好仅启动计数变化；iOS 本轮技能引用归零，未逐键核对其余偏好，不声称整体设置恢复原字节。
测试技能、下载件、私有备份、独立桌面 profile、临时工具/日志与本轮测试进程均已清理。未动原有 Xcode 配置改动。

下一项建议：第 14 项 B09，恢复原附件目录约定，接通会话删除文件清理，验证已有数据、fork 独立性与备份文件集合。

## 第 14 项：附件目录、fork 与清理

起点：`6211dd359e055d157292330bb6832180501ff1c9`。审计 B09。
本项拆为连续的 14A、14B：当前迁移版本的非 Android fork 共用附件路径，恢复删除前必须先明确存量兼容方式。
用户已明确“继续，保留，以后说不定还能用到”；14B 按保留存量文件与引用执行。

**14A：写入、复制与备份。** 状态：已完成（`6785ac7999fca830aa705d6db862cc801df3ee88`），代码、构建及本项所需三端 GUI 已验证。

- 恢复 `upload/UUID.扩展名`，头像/背景/角色卡背景恢复文件 URI。
- 删除 `FileStoreArea`、`PlatformFileStore` 接口和 `StoredPlatformFile`，保留多处复用的 FileKit I/O 工具。
- 原 `buildUuidFileName` 方法移入 common；只有 MimeTypeMap 查询使用窄 expect/actual，Android 实现保持原样。
- 恢复复制逐个失败、失败目标不额外清理、字节写入异常传播的原约定；移除统一 Result/取消包装。
- 原 fork 主方法恢复，四类本地附件生成独立副本，保留原 message ID、节点选择与元数据规则。
- 新文件回到原备份范围；旧 `platform-files/attachments`、`platform-files/images` 保留原路径并加入备份/恢复。
- 修正 FileKit iOS `absolutePath()` 返回 URI 引起的重复前缀/编码，并验证中文/空格/百分号等路径。

代码验证最终 **378 次执行**（JVM 303、Android 60、Pro Max 原生 15），均通过。
真实 Runtime/Room 测试使用实际文件复制和删除验证 fork 独立性；测试注入的清理实现不代表非 Android 产品 DI 已接通。
GUI 发现 Android 裁剪输出在异步保存前被删除；该迁移既有问题一并按原“先保存、后清理”顺序作最小平台适配。
生产代码共 18 个文件，增加 189 行、删除 190 行；另增 3 个测试文件、479 行。
最终包的三端头像/背景均完成实际显示与冷启动验证；临时日志、测试资料和本轮上传件已清理。
iOS TXT 离线发送补试发生 TLS 失败，不计作通过；具体分平台构建版本及验证范围在记录中单列。
原方法比对、详细用例、GUI 依据与实际范围见 [第 14 项验证记录](evidence/cmp-rollback-14-2026-09-11/verification.md)。

**14B：存量兼容与删除接线。** 已签名提交 `0e8f36b8`，签名校验通过；以 `6785ac7999` 为起点，
代码、构建和本项所需三端 GUI 均通过。

- 用具体文件清理实现替换非 Android 的 `ConversationFileStore` / `AssistantAssetCleaner` 两处空接线。
- 旧 fork 保持原文件和引用；清理时检查剩余消息（含所有分支与工具结果）、收藏快照及设置中的本地资产，
  跳过仍被使用的文件。没有批量复制、引用计数表、启动业务状态机或新锁。
- 原助手 VM 在写入设置前清理，因此仅扣除本次移除的资产引用；不改原复制助手时保留背景的业务规则。
- iOS 利用现有 Room `platformOnOpen` 和 DataStore `DataMigration` 修复旧重复前缀、重复编码及旧容器地址。
  只处理当前 Documents 下实际存在的 `upload`、旧 attachments/images 文件；保留会话/消息/节点 ID、选择、文本和其余设置。
  文件尚未恢复或地址不可解析时保持原值，后续重新打开存储时可再次修复。
- 清理接口改为 suspend 以等待 Room 的存量引用检查；Android 原 FilesManager 方法体、原 VM 调用顺序不变。

生产代码 13 个文件，增加 196 行、删除 11 行；净增加的 185 行用于恢复两处空删除接线并保留存量数据，
新增两个具体实现，没有新增业务接口。原仓库、助手 VM 和 Android 接线仅增加 suspend，原方法体及调用顺序保持。

最终代码验证 **385 次执行**（JVM 317、Android host 60、Pro Max 原生 8）均通过，
覆盖原 fork Runtime + 真实 Room 清理、旧共享路径、独占四类文件、分支/工具、收藏、助手共享资产及 iOS 存储重开。
三端 GUI 均由 `gpt-5.6-terra` 在最终包执行：删除源会话、fork 冷启动显示、最后引用删除、助手头像/背景清理均通过。
iOS 另确认旧路径头像和保留助手背景实际呈现；桌面使用隔离 profile，只操作登记文件。
Android/iOS 原设置已按字节恢复，iOS 原会话/消息/收藏散列未变；测试进程、夹具、私有备份、临时工具和日志已清理。
范围与证据见 [14B 验证记录](evidence/cmp-rollback-14b-2026-09-11/verification.md)。

下一项建议：第 15 项 E08，将本地备份导入导出业务收回原 BackupVM；先用固定备份样本核对映射、设置、文件结果和失败行为，
再由 Terra 在三端验证系统选择器导入、导出重读及取消选择。

## 第 15 项：本地备份业务归位

起点：`0e8f36b8582661bdc1bca56d374787a097c34e44`；审计 E08。状态：本项回退完成，代码、构建及三端 GUI 验证通过，数据恢复和测试文件清理已核对。

删除 BackupLocalFileService 接口及 Android/FileKit 两套实现，将原四个本地方法与 ChatboxRestoreResult 归回 BackupVM。
VM 直接使用原 ConversationRepository 和 SettingsStore，原方法名恢复；ImportExportTab 仅调整对应方法调用名称。
WebDavBackupTransport 暂时暴露原有 prepareBackupFile/restoreFromLocalFile 两个入口，平台仍接现有归档实现；
Android 原生恢复保留 URI 转临时文件的必要 I/O，Chatbox/Cherry 直接交给已有 FileKit importer。
不新增生产文件或接口，10 个生产文件增加 139 行、删除 268 行，净减少 129 行。

Chatbox 设置读取位置、Cherry 的异步 updateSettings、导出更新时间与异常传播按 tag 原方法恢复。
Cherry 旧日志可能打印提供商密钥，因此只保留计数日志。原 importer、Archive 和 S3 方法未在本项重写。
改动前 14 项 BackupVM 契约测试通过；本轮增加 8 项固定样本及实际 SQLite 闸口测试，22 项全部通过。
完整 JVM 325 项、Android host 60 项通过；common/iOS 两架构编译、Android APK 与 macOS distributable 构建通过。

GUI 必须验证三端文件选择器和真实 VM/平台接线。Terra 负责取消选择、Chatbox 重复导入、Cherry 导入、
原生标记 ZIP 导入和导出 ZIP 独立读取；实际结果与边界在 [第 15 项说明](evidence/cmp-rollback-15-2026-09-11/verification.md)。
Chatbox 的整文件解析与 tag 的流式读取不在本项宣称等价；原生覆写数据库后的完整恢复归第 16 项测试。

iOS 的正式 GUI 通过 Build iOS Apps 插件执行临时 XCTest 驱动完成；设置/数据库及辅助文件恢复后散列匹配 5/5。
三端导出均独立读取 SQLite 主库与 WAL，核对唯一会话、两条消息及原生标记；临时驱动和测试资料已清理。

下一项建议：第 16 项 E07，统一原 S3Sync/WebDavSync 和两套归档编排。
