# CMP 逐项迁移与验证记录

后续迁移约束（2026-09-09 用户明确要求）：优先直接移动文件，仅对 common 不支持的 Java/Android API 做少量适配。
禁止借迁移大量修改业务逻辑；保持原有判断、异常、取消、并发和持久化行为。验证用于确认迁移等价。
本 session 的全部迁移已回查并按此约束收敛，详见[逐项审查与验证](cmp-session-migration-audit.md)。
以下各项为对应提交的历史记录；标题、建议和翻译的当前行为以该审查文档为准。

## 2026-09-08：持久化 LRU Key 轮换

状态：本项实现及自动化验证完成。迁移前基线为 `66e8370c1`，不代表整个 CMP 迁移已经完成。

### 配置与范围

| 项目 | 本项选择 |
|---|---|
| 策略与适用性 | `PRESERVE` / `SUPPORTED`，保持现有模块、同步调用和 DI 结构 |
| 目标 | Android、Desktop JVM、iOS arm64、iOS Simulator arm64 |
| 工具链 | Kotlin 2.4.20-RC、CMP 1.12.0、AGP 9.3.2、Gradle 9.5.0；Gradle daemon 使用 JetBrains JDK 21，launcher 为 Zulu 21.0.11；Xcode 27.0 |
| 迁移单位 | 搜索与模型 Provider 的持久化 LRU Key 轮换 |
| 依赖 | 复用现有 kotlinx-io；仅为 commonTest 添加版本目录已有的 kotlinx-coroutines-test |
| 验证协作 | gpt-5.6-sol 子 agent 编写契约测试并审查实现，主 agent 运行测试与编译 |

### 实现与兼容性

- `ai/commonMain` 增加 `PersistentKeyRoulette.kt`，共享缓存文件读写，复用原有 LRU 算法。
- Android 的 `KeyRoulette.lru(Context, Clock)` 保持调用方式和 `cacheDir/lru_key_roulette.json` 路径，委托共享实现。
- iOS/Desktop 在 `SharedProductApp` 中使用 FileKit 的应用缓存目录创建同一轮换器，注入搜索服务及 `ProviderManager`。
- 原 JSON 格式 `Map<providerId, Map<apiKey, lastUsedTimestamp>>`、24 小时过期、读写失败不阻断请求的行为保持一致。
- 新增内部 `expect/actual withKeyRouletteFileLock`：Android/JVM 使用同步锁，iOS 使用 `NSLock`，保证同一进程中多个实例的读、修改、写入互斥。
- Android 的 Context/缓存目录选择以及各平台锁仍留在平台源集；公共层无 Android/JVM import。

新增公开 API：

```kotlin
fun KeyRoulette.Companion.persistentLru(
    cacheFile: kotlinx.io.files.Path,
    clock: Clock = Clock.System,
): KeyRoulette
```

采用独立工厂名以避免与原 `lru(Context)` 重载形成歧义，原有 `KeyRoulette.lru(get())` Koin 调用无需修改。

验证时发现迁移前已有的 common metadata 编译阻塞：`ThinkTagTransformer` 的 `DOT_MATCHES_ALL` 不在 common API 中。正则本身已使用 `[\s\S]`，移除冗余选项即可保持匹配语义并通过检查；本项没有修改流式转换的调用时机。

| 台账类别 | 技术处理 | 结果 |
|---|---|---|
| `REQUIRED_FOR_KMP`：iOS/Desktop 持久化轮换行为补齐 | `REWRITEABLE` | 已完成，共享文件实现与入口接线 |
| `REQUIRED_FOR_KMP`：同步文件访问平台边界 | `ANDROID_ONLY`（各平台分别适配） | 已完成，Android/JVM/iOS 锁 |
| `REQUIRED_FOR_KMP`：原 common metadata 正则编译阻塞 | `REWRITEABLE` | 已移除冗余选项 |
| `RECOMMENDED` | — | 本项无新增未完成事项 |
| `ARCHITECTURAL_OPTIMIZATION` | — | 未改造同步 API、跨进程访问或持久化架构 |

### 代码验证步骤与预期结果

新增测试文件：`ai/src/commonTest/kotlin/me/rerere/ai/util/PersistentKeyRouletteTest.kt`。
测试使用真实临时文件、测试 Key 和可控时钟，结束后删除临时文件。

| 用例 | 步骤 | 预期结果 |
|---|---|---|
| 跨实例恢复 | 用第一实例选择 Key，重新创建实例读取同一文件 | 新实例选择下一个尚未使用的 Key |
| 旧 Android 缓存 | 写入原格式 JSON，再调用共享轮换器 | 保留既有使用记录，优先选择未使用 Key |
| 损坏缓存 | 写入非法 JSON 后选择 Key | 正常返回有效 Key，并重建可读取缓存 |
| Provider 隔离 | 两个 Provider 使用相同 Key 列表 | 使用记录相互独立 |
| 过期边界 | 写入恰好 24 小时前的记录 | 该 Key 被视为未使用 |
| 移除 Key | 使用旧列表，再换成删除一项后的列表 | 已移除 Key 不再留在该 Provider 的缓存中 |
| LRU 选择 | 按不同时间用完所有 Key，再次请求 | 选择最久未使用的 Key |
| 写失败降级 | 将缓存路径指向目录以制造真实 I/O 错误 | 仍返回有效 Key，不向请求方抛缓存异常 |
| 并发多实例 | 32 个实例并发使用同一文件和 32 个未使用 Key | 32 次选择不重复，无使用记录丢失 |

执行命令：

```bash
./gradlew :ai:jvmTest :ai:testAndroidHostTest :ai:iosSimulatorArm64Test
./gradlew :ai:compileCommonMainKotlinMetadata \
  :composeApp:compileCommonMainKotlinMetadata \
  :composeApp:compileAndroidMain :app:compileDebugKotlin \
  :composeApp:compileKotlinJvm \
  :composeApp:compileKotlinIosArm64 :composeApp:compileKotlinIosSimulatorArm64
```

| 验证对象 | 结果 |
|---|---|
| 迁移前 AI 测试基线 | Android 121、JVM 71、iOS Simulator 71 项通过 |
| 迁移后 AI 测试 | Android 130、JVM 80、iOS Simulator 80 项通过；无失败、错误或跳过 |
| 新增契约用例 | 9 项在三个测试目标各执行一次，共 27 次通过 |
| Android 应用与共享模块编译 | 通过 |
| Desktop JVM 共享模块编译 | 通过 |
| iOS arm64 / Simulator arm64 共享模块编译 | 均通过 |
| AI 与 composeApp 的 common metadata | 通过 |
| diff 格式、平台 import 检查 | 通过 |

### 人工确认、限制与下一项

本项没有新增 UI 或权限流程，不需要 GUI 人工验收。iOS Simulator 验证运行了 Kotlin/Native 测试，包含真实文件 I/O 和平台锁；未进行真实 Provider 请求或完整应用 GUI 回归。重建实例验证了从磁盘恢复的契约，未单独进行整应用杀进程测试。

保留原有缓存语义：系统清理缓存后会重置轮换记录；锁仅保证进程内互斥，文件原地覆盖不是崩溃原子写。损坏缓存会在下一次调用时恢复，不阻断模型或搜索请求。本项未引入临时生产日志，测试代码永久保留。

下一项建议：AI 自动会话标题（中低难度），将共享运行时的首条文本截断替换为模型生成，优先通过 Mock Provider 验证模型选择、提示词、覆盖规则及失败处理。

## 2026-09-08：AI 自动会话标题

状态：本项实现及自动化验证完成。迁移前基线为 `9b1e709ac`。本项难度：中低；涉及共享请求逻辑、后台任务与标题字段保存，无新增页面。

### 配置与范围

| 项目 | 本项选择 |
|---|---|
| 策略与适用性 | `PRESERVE` / `SUPPORTED`，保留 Android `ChatService` 和 iOS/Desktop `SharedChatRuntime`，提取共享标题生成器 |
| 目标与工具链 | 与上一项一致，无版本升级 |
| 迁移单位 | AI 自动标题和手动强制重新生成标题；不包含追问建议或上下文压缩 |
| 依赖 | 仅在 `commonTest` 添加版本目录已有的 Ktor MockEngine，无新增生产依赖 |
| 验证协作 | gpt-5.6-sol 子 agent 编写测试、审查运行时和保存逻辑；主 agent 修正接线并运行验证 |

### 实现与兼容性

- `ConversationTitleGenerator` 放在 `composeApp/commonMain`，Android/iOS/Desktop 共用。iOS/Desktop 原先截断首条用户文本，现在调用模型生成标题。
- 保留 Android 的模型选择：优先 `titleModelId`，未配置或找不到时回退 `fastModelId`，两者均不可用则跳过。保留模型的 Provider override。
- 使用既有 `titlePrompt`，替换 `{locale}` 和 `{content}`；内容取当前选中分支的最近 4 条消息，每条使用 `summaryAsText(maxLength = 500)`，超长摘要附加 `...`。
- 将 `backgroundTextGenerationParams` 原样移入 commonMain，保留 `ReasoningLevel.AUTO`、模型自定义 headers/body。Android 追问建议及压缩继续调用同一函数。
- 已有非空标题默认跳过；`force = true` 可重新生成。生成期间手动改名时保留手动标题，即使本次是强制生成。
- 空白响应、空 choices、无文本消息不写入标题。普通请求/保存异常由各运行时显示既有标题生成错误及模型设置修复入口；取消异常继续传播。
- iOS/Desktop 在回复完成后快照会话，再通过运行时 scope 独立启动标题任务，避免标题网络请求延长聊天的生成中状态。
- 保存前重新读取会话，跳过已删除或标题已变化的会话；持久化时再以旧标题为条件原子更新 `title` 字段，并在同一写事务内更新 FTS 索引标题。内存只合并标题，保留下一轮尚未写入数据库的流式消息。
- Android/JVM 的 locale 仍取系统 display name；iOS 复用已有 locale identifier（例如 `zh_Hans_CN`），由模型理解目标语言。

新增公开 API：`ConversationTitleGenerator`、共享的 `backgroundTextGenerationParams`，以及 `ConversationRepository.updateConversationTitle(id, expectedTitle, title)`。`ChatRuntime.generateTitle` 的调用契约保持不变。数据库仅增加条件更新查询，无 schema 变更。

| 台账类别 | 技术处理 | 结果 |
|---|---|---|
| `REQUIRED_FOR_KMP`：共享自动标题语义 | `REWRITEABLE` | 已提取生成器并接入三个平台 |
| `REQUIRED_FOR_KMP`：语言与错误呈现 | 复用现有平台边界 | 已接入 `PlatformDeviceInfo` 和既有错误资源 |
| `REQUIRED_FOR_KMP`：标题请求与聊天任务解耦 | `REWRITEABLE` | 已独立启动后台标题任务 |
| `RECOMMENDED`：空响应及延迟保存保护 | `REWRITEABLE` | 已加入标题条件更新，避免整会话快照覆盖消息 |
| `ARCHITECTURAL_OPTIMIZATION` | — | 未重构会话生命周期、所有字段的并发写入或 DI 架构 |

### 代码验证步骤与预期结果

Mock Provider 用例位于 `composeApp/src/commonTest/kotlin/me/rerere/rikkahub/service/ConversationTitleGeneratorTest.kt`，测试不访问真实模型，HTTP 客户端在用例结束后关闭。

| 验证内容 | 步骤 | 预期结果 |
|---|---|---|
| 模型优先级 | 配置标题模型；再将标题模型设为未配置或无效 ID | 优先用标题模型，后两种情况用快速模型 |
| 无可用模型 | 标题模型和快速模型 ID 均无效 | 不请求、不保存 |
| 标题覆盖规则 | 给有标题会话分别执行普通和强制生成 | 普通生成跳过；强制生成保存去除首尾空白后的标题 |
| 提示词范围 | 输入 5 条消息、长文本和固定 locale | 仅包含最近 4 条消息，长摘要截断，locale 被替换 |
| 消息分支 | 提供含多个分支的消息节点 | 仅使用选中分支，不包含已弃用分支 |
| 请求参数 | 配置模型 headers/body 与 Provider override | 请求保留自定义参数、`AUTO` 和指定 Provider |
| 空响应 | 强制生成时返回空白、空 choices 或无消息 | 保留原标题，不保存空值 |
| 异常和取消 | Provider、存储分别抛错或请求取消 | 错误传播到调用方，不执行成功保存 |
| 生成期间改名 | 请求返回前修改会话标题 | 保留手动标题，即使原请求为强制生成 |
| 生成期间删除 | 请求返回前移除会话 | 不重新创建会话 |
| 数据库字段隔离 | 写入带消息及元数据的会话，再更新生成标题 | 仅标题和搜索索引标题变化，消息及其他字段保持一致 |
| 数据库条件更新 | 更新前已改名或已删除 | 更新失败，不覆盖标题、不重建会话、不改索引 |

执行命令：

```bash
./gradlew :composeApp:jvmTest :composeApp:testAndroidHostTest :composeApp:iosSimulatorArm64Test
./gradlew :composeApp:compileCommonMainKotlinMetadata \
  :composeApp:compileAndroidMain :composeApp:compileKotlinJvm \
  :composeApp:compileKotlinIosArm64 :composeApp:compileKotlinIosSimulatorArm64 \
  :app:compileDebugKotlin \
  :app:testDebugUnitTest --tests me.rerere.rikkahub.service.ChatServiceTest
```

数据库用例位于 `composeApp/src/jvmTest/kotlin/me/rerere/rikkahub/service/ConversationTitlePersistenceTest.kt`，使用真实 Room 内存数据库和 Bundled SQLite，结束后关闭数据库。

| 验证对象 | 结果 |
|---|---|
| 迁移前 composeApp 基线 | Android、JVM、iOS Simulator 各 69 项通过 |
| 迁移后 composeApp 测试 | Android 83、JVM 86、iOS Simulator 83 项通过；无失败、错误或跳过 |
| 新增生成器契约用例 | 14 项在三个测试目标各执行一次，共 42 次通过 |
| 新增真实数据库用例 | JVM 3 项通过，覆盖字段隔离、标题冲突和删除保护，包含 FTS 搜索断言 |
| 原 Android 请求参数回归 | `ChatServiceTest` 1 项通过 |
| Android 应用、共享 Android/JVM/iOS arm64/iOS Simulator arm64 编译 | 全部通过 |
| composeApp common metadata | 通过 |
| diff 格式、公共层平台 import、临时日志检查 | 通过，无新增临时生产日志 |
| 独立代码审查 | gpt-5.6-sol 完成；发现的标题请求阻塞聊天问题已修正，复查无必须修复事项 |

### 验证范围与下一项

自动化验证覆盖请求与保存行为；Mock Provider 不评判真实模型生成的标题质量。本项没有新增 UI，未执行完整应用 GUI 回归。iOS Simulator 运行的是 Kotlin/Native 测试；真实 Room/SQLite 的持久化用例在 JVM 执行。

标题条件更新保护本次写入；整个应用其他字段仍沿用既有会话保存方式，不声称解决了所有交错写入情形。本项没有新增临时生产日志，测试代码永久保留。

下一项建议：AI 追问建议生成（中低难度）。`SharedChatRuntime.generateSuggestion` 当前为空实现，可复用本项的后台请求参数，通过 Mock Provider 验证开关、模型回退、响应解析和保存规则。

## 2026-09-09：AI 追问建议

状态：本项实现及自动化验证完成。迁移前基线为 `d1b187b9b`。本项难度：中低，主要是共享请求、后台触发和建议字段持久化。

### 配置与范围

| 项目 | 本项选择 |
|---|---|
| 策略与适用性 | `PRESERVE` / `SUPPORTED`，保持现有模块、DI、`ChatRuntime.generateSuggestion` 调用方式 |
| 目标与工具链 | Android、Desktop JVM、iOS arm64、iOS Simulator arm64；沿用前两项工具链，无依赖或版本变更 |
| 迁移单位 | 回复成功后的自动追问建议，以及既有生成建议接口 |
| UI 与设置 | 复用共享的建议列表、点击填入输入框行为和现有 DataStore 键/默认值 |
| 验证协作 | gpt-5.6-sol 子 agent 分别编写契约测试、真实数据库测试和独立审查；主 agent 实现接线并执行验证 |

### 实现与兼容性

- 新增 `ConversationSuggestionGenerator` 到 `composeApp/commonMain`，Android `ChatService` 与 iOS/Desktop `SharedChatRuntime` 共用。
- 保留 `enableSuggestion` 开关；优先使用 `suggestionModelId`，未配置或找不到时回退 `fastModelId`。开关关闭或无可用模型时不请求、不修改已有建议。
- 提示词仍使用 `suggestionPrompt`，替换 `{locale}` 和 `{content}`。内容为当前选中分支的最近 8 条消息，每条 `summaryAsText(maxLength = 500)`；超长摘要附加 `...`。
- 复用 `backgroundTextGenerationParams`，保留 `AUTO`、模型自定义 headers/body 和 Provider override。语言信息沿用现有 `PlatformDeviceInfo`，Android/JVM 使用 display name，iOS 使用 locale identifier。
- 响应取首 choice 的文本，按换行分割、逐行 trim、过滤空白，最多保存 10 条；保留顺序、重复项和模型输出的编号，不额外去重或移除 Markdown。空 choices、无消息或空文本统一保存空列表。
- iOS/Desktop 在新回复开始时清空内存建议，并在回复完整成功、保存消息后，独立启动建议任务，与标题任务并行。建议请求不延长聊天 job 的生成状态。
- 开始请求前通过消息快照校验，持久化清空建议，再同步内存。Provider 失败、取消或生成期间关闭开关时，重新读取数据库也不会恢复本次已清空的旧建议。
- 每个会话的请求使用独立 ID；同会话较旧请求的延迟响应被丢弃，不同会话可并行请求。清空和保存操作使用短时互斥锁保持顺序，不锁住模型网络请求。
- 返回前检查最新开关和当前消息；发送新消息、编辑同 ID 内容、切换分支或删除会话后，旧结果不保存。数据库写事务内再次校验选中消息，仅更新 `suggestions` 字段；不改变标题、消息、时间、置顶、文件夹或 FTS 内容。内存也仅合并建议字段。
- 取消异常继续传播；普通失败沿用 Android 的静默降级，仅记录常规错误日志，不转为主聊天错误。

新增公开 API：`ConversationSuggestionGenerator` 和 `ConversationRepository.updateConversationSuggestions(conversationId, expectedMessages, suggestions)`。数据库建议仍使用既有 JSON 字符串格式，仅增加字段更新查询，无 schema 变更。

| 台账类别 | 技术处理 | 结果 |
|---|---|---|
| `REQUIRED_FOR_KMP`：共享模型请求与解析 | `REWRITEABLE` | 已共享，保留 Android 解析规则与参数 |
| `REQUIRED_FOR_KMP`：iOS/Desktop 自动触发 | `REWRITEABLE` | 已接入回复成功后的独立后台任务 |
| `REQUIRED_FOR_KMP`：语言、日志与显示 | `ANDROID_ONLY`（复用现有平台适配边界） | 已复用 locale、日志和共享 UI，无新平台 API |
| `RECOMMENDED`：字段隔离与旧响应保护 | `REWRITEABLE` | 已加入消息快照校验、请求 ID 和字段级保存 |
| `RECOMMENDED`：失败后旧建议重现 | `REWRITEABLE` | 已将生成前的清空操作持久化 |
| `ARCHITECTURAL_OPTIMIZATION` | — | 未改造全部消息编辑入口或会话状态架构 |

### 代码验证步骤与预期结果

生成器用例位于 `composeApp/src/commonTest/kotlin/me/rerere/rikkahub/service/ConversationSuggestionGeneratorTest.kt`；数据库及集成用例位于 `composeApp/src/jvmTest/kotlin/me/rerere/rikkahub/service/ConversationSuggestionPersistenceTest.kt`。

| 验证内容 | 步骤 | 预期结果 |
|---|---|---|
| 开关与模型 | 关闭开关；分别配置建议模型、空/无效建议模型和无效快速模型 | 关闭时不请求；优先建议模型，回退快速模型，两者无效则跳过 |
| 提示词与参数 | 输入多于 8 条消息、长文本、多个分支，并配置模型 override/headers/body | 只取最近 8 条选中消息，摘要截断且 locale 替换，保留后台参数 |
| 响应解析 | 返回含空白、重复、编号及超过 10 行的文本 | 保序、保重复、保编号，过滤空白后最多 10 条 |
| 空响应 | 返回空 choices、无消息或空白文本 | 建议为空，不越界或恢复旧值 |
| 请求失败与取消 | 先存旧建议，再令 Provider 失败或取消 | 不保存生成结果，重新读取数据库仍为空建议 |
| 中途关闭开关 | 请求期间关闭建议开关，再返回结果 | 不写入返回的建议，已清空的旧建议不恢复 |
| 过期请求 | 同会话同时生成两次，令旧请求最后返回 | 只采用新请求；不同会话各自保存 |
| 保存交错 | 暂停旧请求的保存，再启动新请求 | 清空和保存顺序受保护，最终为新建议 |
| 消息变化 | 请求期间追加消息、编辑同 ID 文本或切换分支 | 旧消息快照无法写入建议 |
| 字段隔离 | 请求期间改名、改置顶/文件夹，再保存建议 | 保留全部消息、标题和元数据，FTS 查询结果不变 |
| 删除与持久化格式 | 删除会话后更新；写入空列表和含特殊字符的建议 | 不重建会话，JSON 正确往返，空列表正常清除 |

执行命令：

```bash
./gradlew :composeApp:jvmTest :composeApp:testAndroidHostTest :composeApp:iosSimulatorArm64Test
./gradlew :composeApp:compileCommonMainKotlinMetadata \
  :composeApp:compileAndroidMain :composeApp:compileKotlinJvm \
  :composeApp:compileKotlinIosArm64 :composeApp:compileKotlinIosSimulatorArm64 \
  :app:compileDebugKotlin \
  :app:testDebugUnitTest --tests me.rerere.rikkahub.service.ChatServiceTest
```

| 验证对象 | 结果 |
|---|---|
| 迁移前 composeApp 基线 | Android 83、JVM 86、iOS Simulator 83 项通过 |
| 迁移后 composeApp 测试 | Android 97、JVM 107、iOS Simulator 97 项通过；无失败、错误或跳过 |
| 新增生成器契约 | 14 项在三个目标各执行一次，共 42 次通过 |
| 新增真实 SQLite / 生成器持久化集成 | JVM 7 项通过，覆盖字段隔离、消息变化、删除、JSON 往返及请求失败后的重新读取 |
| 原 Android 请求参数回归 | `ChatServiceTest` 1 项通过 |
| Android 应用及共享 Android/JVM/iOS arm64/iOS Simulator arm64 编译 | 全部通过 |
| composeApp common metadata | 通过 |
| diff 格式、公共层平台 import、临时日志检查 | 通过，无临时验证日志 |

首次 Native 测试编译发现测试断言的集合类型推断差异，显式标注 `List<Mutation>` 后，所有目标通过；未更改生产语义。HTTP 客户端和测试数据库均在用例结束后关闭。

### 验证范围与下一项

本项沿用已有共享 UI；点击建议只填入输入框，不自动发送。代码测试验证请求、状态回调及持久化；不评判真实模型建议的语言质量。iOS Simulator 执行 Kotlin/Native 测试，真实 Room/Bundled SQLite 用例在 JVM 执行；未做完整应用 GUI 回归。

保留既有 Android 行为：关闭开关不会删除已显示的建议；不触发回复的纯编辑、删消息或分支切换入口没有统一清空策略。本项保护这些变化期间仍在运行的旧请求，不重构所有消息操作。无临时验证日志，常规失败日志和永久测试保留。

下一项建议：聊天消息翻译（中低难度）。`SharedChatRuntime.translateMessage` 当前仍返回不可用错误，可复用已有共享翻译请求，实现流式译文、错误清理和消息保存，并优先用 Mock Provider 验证。

## 2026-09-09：DeepSeek 三端 GUI 回归补充

对 `252a0b541` 的自动标题和追问建议补充真实应用 GUI 验证。Android、iOS Simulator、Desktop 均通过
短回复、自动标题、建议显示、点击只填入输入框及重启持久化检查；由 `gpt-5.6-sol` 操作，主 agent 复核截图。

Android 打开已保存会话时发现既有 ANR：创建会话的 Pending 条目尚未就绪，状态通知同步重入会话列表读取，
导致主线程自旋。已将 `ChatService` 的通知移到 `getOrPut` 返回之后，并用原会话完成 GUI 复验，无新增模型请求。
新增真实 ChatService 的无网络仪器测试通过（1 项，0.581 秒）；既有 `ChatServiceTest` 1 项及 Android APK 构建通过。
iOS/Desktop 未改代码，沿用本轮已完成的 GUI 证据。

后续真实 API 测试按用户要求复用本机钥匙串中的 DeepSeek 测试密钥，优先 `deepseek-v4-flash`、关闭思考、
短输入和短输出；不得把密钥写入仓库。完整步骤、预期、用量可知范围、截图及 ANR 证据见
[CMP GUI 回归记录](cmp-gui-regression.md)。下一项迁移建议仍为聊天消息翻译（中低难度）。

## 2026-09-09：聊天消息翻译

迁移前基线：`4dab04e3`。本项难度：中低，复用已有共享翻译设置和消息译文 UI，补齐 iOS/Desktop 聊天调用及持久化。

### 配置与范围

| 项目 | 本项选择 |
|---|---|
| 策略与适用性 | `PRESERVE` / `SUPPORTED`，保留模块、DI、`ChatRuntime` 和 `TranslationRuntime` 接口 |
| 目标与工具链 | Android、Desktop JVM、iOS arm64、iOS Simulator arm64；沿用前述工具链，无依赖/版本升级 |
| 迁移单位 | 聊天消息翻译、流式译文更新、清除译文及保存；独立翻译页面复用同一请求实现 |
| 平台保留内容 | Android 的 Java Locale、Context 资源和 IO 调度；共享运行时使用既有语言枚举、Compose 资源及 Default 调度 |
| 验证协作 | 按用户最新要求，子 agent 全部使用 `gpt-5.6-terra`；契约测试、SQLite 集成测试、并发审查和 Desktop GUI 分工验证 |

### 实现与兼容性

- `TextTranslationGenerator` 提取 Android 与共享翻译页面的重复请求逻辑，供 `GenerationHandler`、`SharedTranslationRuntime` 和聊天运行时复用。原有 facade 签名保持不变。
- 翻译仍只使用 `translateModeId` 指定的模型，不回退聊天/快速模型；保留 Provider override、翻译提示词和思考预算。模型自定义 headers/body 现在也传入请求，可应用输出上限等设置。
- 普通模型继续流式生成，累计 assistant 文本；只有非空文本才更新。空 choices、仅用量或尚无 assistant 文本的 chunk 不会把源提示词显示成译文。
- Qwen MT 保留非流式分支、原文输入、`temperature=0.3`、`topP=0.95` 和 `translation_options`（自动源语言、英文目标语言名）；必需的翻译选项覆盖同名自定义参数。
- `MessageTranslationManager` 共享文本提取、加载状态、流式更新、错误清理及保存。仅拼接 Text 部分，以两个换行分隔并 trim；图片/推理不参与，空白输入跳过。
- 翻译任务独立于聊天生成任务。每个消息的请求有独立令牌；状态锁将请求占用、内存更新和清除排序，避免较旧请求在清除或新请求后回写。锁内不等待模型请求或磁盘写入。
- 磁盘写入单独串行化。完成或清除时，仅更新目标消息的 `translation`，不保存整份旧会话快照；消息 ID、角色和内容变化或消息/会话已删除时拒绝过期写入。
- 空结果清除加载状态并保存 null；普通失败清除自己仍拥有的译文并显示既有翻译错误；取消执行清理后继续传播，不显示普通错误。原地编辑若保留了当前请求的半截译文，也会清除该旧值。
- 支持未选中的历史消息分支；不改写其他消息、标题、建议、时间、收藏或 FTS。数据库仍使用原 `UIMessage.translation` JSON 字段，无 schema 或 DataStore 键变更。
- iOS/Desktop 支持共享 UI 提供的 9 种目标语言；Android 保留 Java Locale 对语言标签的解析。共享运行时对 UI 集合外的标签明确报错。

新增公开 API：`TextTranslationGenerator`、`MessageTranslationManager`、`Conversation.withMessageTranslation` 和
`ConversationRepository.updateMessageTranslation(conversationId, expectedMessage, translation)`；未删除或改变既有公开签名。

| 台账类别 | 技术处理 | 结果 |
|---|---|---|
| `REQUIRED_FOR_KMP`：共享翻译请求与解析 | `REWRITEABLE` | 普通流式和 Qwen MT 分支已共享，原 facade 委托共享生成器 |
| `REQUIRED_FOR_KMP`：聊天运行时接线 | `REWRITEABLE` | Android/iOS/Desktop 共用状态管理和持久化逻辑 |
| `REQUIRED_FOR_KMP`：语言与本地资源 | 复用现有平台边界 | Android 保留平台 Locale/资源；共享层复用语言枚举和 Compose 资源 |
| `RECOMMENDED`：清除后重现与字段隔离 | `REWRITEABLE` | 清除落盘，事务校验源消息，仅合并译文字段 |
| `RECOMMENDED`：空响应、取消和旧结果竞争 | `REWRITEABLE` | 空响应不显示原文/永久加载；状态更新与请求令牌同步 |
| `ARCHITECTURAL_OPTIMIZATION` | — | 未重构所有会话保存入口、消息编辑策略或聊天任务生命周期 |

### 代码验证步骤与预期结果

测试文件：commonTest 的 `TextTranslationGeneratorTest`、`MessageTranslationManagerTest`，以及 jvmTest 的
`MessageTranslationPersistenceTest`、`MessageTranslationConcurrencyTest`。Mock Provider 无网络请求；真实 SQLite
用例使用 Room/Bundled SQLite，测试结束关闭数据库和 HTTP 客户端；线程用例设超时并关闭 executor。

| 验证内容 | 步骤 | 预期结果 |
|---|---|---|
| 请求参数 | 配置翻译模型、Provider override、自定义参数和目标语言 | 使用指定模型/Provider，替换提示词并保留参数；无模型明确失败 |
| 普通流式与 Qwen MT | 普通模型分块返回；Qwen 返回完整文本 | 普通译文累计更新，Qwen 请求保留翻译选项，两者都保存最终文本 |
| 非文本及空响应 | 输入图片/空白，或返回空 choices、用量、推理、空白文本 | 非文本输入不请求；响应不泄漏源提示词，结束后没有加载占位 |
| 失败和取消 | 半截译文后令 Provider 失败或取消 | 内存和重新读取的数据库均为空；仅普通失败展示错误 |
| 清除与重启 | 保存译文后清除，再从数据库重新读取 | 清除立即反映到界面，落盘后译文保持为空 |
| 旧请求和编辑 | 新请求先完成，旧请求迟到；或原地修改消息内容/删除消息 | 旧结果不覆盖新值或已编辑内容，不重建消息/会话 |
| 并发清除 | 暂停已进入内存更新回调的旧 partial，在另一线程执行 clear | 调用不自锁，清除等待当前状态更新排序，最终内存和磁盘为空 |
| 保存交错 | 暂停旧结果数据库保存，再清除译文 | 最终保存顺序为旧值、null，旧结果不会重新显示 |
| 字段隔离与 JSON | 翻译未选中的分支，写入 Unicode/引号/换行，再清除 | 仅该消息译文变化；其他分支、元数据及 FTS 不变，JSON 正确往返 |

执行命令：

```bash
./gradlew :composeApp:jvmTest :composeApp:testAndroidHostTest :composeApp:iosSimulatorArm64Test
./gradlew :composeApp:compileCommonMainKotlinMetadata \
  :composeApp:compileAndroidMain :composeApp:compileKotlinJvm \
  :composeApp:compileKotlinIosArm64 :composeApp:compileKotlinIosSimulatorArm64 \
  :app:compileDebugKotlin \
  :app:testDebugUnitTest --tests me.rerere.rikkahub.service.ChatServiceTest
```

| 验证对象 | 结果 |
|---|---|
| 迁移前 composeApp 基线 | Android 97、JVM 107、iOS Simulator 97 项通过 |
| 迁移后 composeApp 测试 | Android 121、JVM 137、iOS Simulator 121 项通过；无失败、错误或跳过 |
| 新增共享生成器与状态管理用例 | 每目标 24 项，共 72 次通过 |
| 新增 JVM SQLite / 并发用例 | 5 项真实数据库用例、1 项真实线程并发清除用例通过 |
| 原 Android 请求参数回归 | `ChatServiceTest` 1 项通过 |
| Android 应用、共享 Android/JVM/iOS arm64/iOS Simulator arm64、common metadata 编译 | 全部通过 |

编译中修正了挂起 DAO 函数引用和测试构造参数后，全部检查通过；独立审查发现的状态更新竞态已修复并补充线程回归。
代码测试汇总见[验证记录](evidence/cmp-translation-2026-09-09/code-tests.txt)。

Desktop GUI 补充验证已由 `Blocked` 更新为 `Pass`。用户解锁后，使用电脑插件和 `gpt-5.6-terra` 子 agent
完成真实翻译、折叠/展开和重启；主 agent 补充清空后的重启验证，并独立查询 SQLite。
唯一一次 DeepSeek 请求将 `Hello` 翻译为“你好。”；重启后译文保留，清空并再次重启后为 null，原文不变。

同时修复原生包缺少 `jdk.unsupported` 导致 DataStore 读取已保存设置时找不到 `sun.misc.Unsafe` 的问题。
重新打包、检查运行时类并完成 GUI 回归；恢复常规配置后 JVM 137 项测试再次通过。
本轮只补充 Desktop GUI，详细步骤、截图、SQLite 和打包证据见
[GUI 回归记录](cmp-gui-regression.md#2026-09-09聊天消息翻译)。

### 验证范围与下一项

代码测试覆盖请求、状态与持久化契约。真实 SQLite 测试在 JVM 执行，iOS Simulator 的代码测试使用 Kotlin/Native；
它们不替代完整应用的 GUI 验证，也不评判翻译质量。Qwen MT 由 Mock Provider 验证协议，不增加真实 Qwen 请求。

本项保护翻译自身的写入；应用其他流程仍可保存整份会话快照，不声称解决全应用所有字段的并发写入。
新的翻译/清除使旧请求失效，但不增加聊天停止按钮对翻译任务的控制。

下一项建议：**会话上下文压缩（中等难度）**。共享运行时的 `compressConversation` 仍返回不可用错误；可继续提取
Android 的压缩请求，优先验证保留最近消息数、目标 token、失败回滚和摘要持久化。

## 2026-09-09：会话上下文压缩

迁移前基线：`db93b1a79`。状态：代码验证与 Desktop 限定范围 GUI 回归完成。本项难度：中等。

### 配置与范围

| 项目 | 本项选择 |
|---|---|
| 策略与适用性 | `PRESERVE` / `SUPPORTED`，沿用模块、DI 和公开压缩调用 |
| 目标 | Android、Desktop JVM、iOS arm64、iOS Simulator arm64 |
| 工具链 | 沿用已有 Kotlin 2.4.20-RC、CMP 1.12.0、AGP 9.3.2、Gradle 9.5.0、JDK 21、Xcode 27.0 |
| 迁移单位 | 压缩请求、结果替换、对话框及 iOS/Desktop 入口 |
| 基线 | Android 121、JVM 137、iOS Simulator 121 项通过；无依赖、数据库 schema 或设置键变更 |

| ID / 位置 | 源集 | 义务 / 技术处理 | 语义风险及动作 | 公开 API 影响 | 状态 / 验证 |
|---|---|---|---|---|---|
| C01 压缩请求 | app → commonMain | REQUIRED_FOR_KMP / REWRITEABLE | 保留模型回退、256 条递归分块、提示词及保留最近消息规则 | ChatRuntime 原签名保留 | 完成；8 项 common 契约测试 |
| C02 压缩保存 | 平台 runtime | REQUIRED_FOR_KMP / REWRITEABLE | 共享逻辑继续调用原 saveConversation，保持节点替换和建议清空规则 | 不变 | 完成；2 项真实 SQLite 集成测试 |
| C03 压缩 UI | app → commonMain | REQUIRED_FOR_KMP / REWRITEABLE | 共享参数、加载与取消；Android 保留原入口，iOS/Desktop 补齐更多菜单 | Android 对话框 facade 保留 | 三端编译通过；Desktop GUI 通过 |
| C04 Locale 与文件清理 | 平台 runtime | REQUIRED_FOR_KMP / ANDROID_ONLY | 复用 PlatformDeviceInfo 的 locale；文件清理及保存时机保持原 runtime 行为 | 不变 | Android locale 实现与原 Locale 调用一致，保存方法无变更 |

按用户要求，本项以原样抽取与接线为准；保持原校验、取消、空响应处理及保存行为，不新增事务、过期请求检查或参数规则。

### 移动内容与兼容边界

- `ChatService.compressConversation` 的方法体抽取到 `ConversationCompressor`；归一化比较确认，仅替换设置读取、
  错误字符串和 Locale 访问，模型参数、递归分块、异步合并及原 `runCatching` 均保持一致。
- Android 与 `SharedChatRuntime` 保留原 `compressConversation` 签名，委托共享实现并传入各自既有的 `saveConversation`。
  未改 repository、消息结构、保存流程或文件删除行为。
- `CompressContextDialog` 主体移动到 `SharedCompressContextDialog`。只将 Android 资源包装器换成 CMP 资源 API，
  用 composable slot 隔离 Android `RabbitLoadingIndicator`；原 Android facade 和加载动画保留。
  默认目标 2000、选项 500/1000/2000/4000、默认保留 32 条、输入、确认和取消流程不变。
- iOS/Desktop 输入框“更多”补充“添加附件 / 压缩历史”入口，附件选择仍调用原 FileKit launcher。

新增兼容边界为 `ConversationCompressor` 的设置、保存、错误资源与 Locale 回调，及共享对话框的加载动画 slot。
新增公开类型/函数为 `ConversationCompressor` 和 `SharedCompressContextDialog`；既有公开方法签名与调用方式不变。
没有执行 `RECOMMENDED` 或 `ARCHITECTURAL_OPTIMIZATION` 类业务改造。

### 自动化验证

| 验证步骤 | 预期结果 | 结果 |
|---|---|---|
| 指定压缩模型或回退聊天模型，配置 Provider override/自定义参数 | 请求与原 Android 模型选择、AUTO 思考参数和四个占位符一致 | 通过 |
| 压缩选中分支并保留最近消息，257 条消息分块逆序完成 | 只发送选中分支；保留 UIMessage 元数据，摘要按源顺序保存并清空建议 | 通过 |
| 测试消息不足、空 choices/null message、普通错误及取消 | 保留原 Result/异常与不调用保存的行为 | 通过 |
| 测试空白摘要、非正 keep 和目标 token 边界 | 与旧实现一致，不新增校验 | 通过 |
| 真实 SQLite 保存后重新读取并查询 FTS | 摘要和最近消息正确保存，旧文本索引移除，新索引存在 | 通过 |

Android host **129**、JVM **147**、iOS Simulator **129** 项通过；本项新增执行 26 次。
Android `ChatServiceTest` 1 项通过。Android、JVM、iOS arm64、iOS Simulator arm64 和 common metadata 编译通过。
命令及详细结果见[自动化记录](evidence/cmp-compression-2026-09-09/code-tests.txt)。

### GUI 与后续工作

Desktop 已通过“消息不足”“摘要 + 最近一条消息”“清空旧建议”“退出重启恢复”的限定回归。
一次 DeepSeek V4 Flash 请求，关闭思考并以模型 custom body 限制输出 64 token；未取得服务端实际用量。
Terra 子 agent 完成入口与失败分支操作，插件调用停滞后由主 agent 重建会话并完成有效压缩和重启。
截图、AX 与独立 SQLite 证据见[GUI 回归](cmp-gui-regression.md#2026-09-09会话上下文压缩)。
Android/iOS 本项只完成代码测试与编译，不外推为相应 GUI 已通过。
临时 app、profile、seed、Gradle init 和日志已删除；未添加临时生产日志。

按用户最新要求，下一步先回查本 session 的全部改动，删除非迁移所需的业务改写，再建议下一项迁移。

## 2026-09-09：本 session 最小迁移审查

已逐项检查 `66e8370c1..d8ab61c8` 的 7 个提交，恢复标题、建议、翻译的原判断、异常、并发和保存行为。
删除新增 CAS/事务字段更新、请求 ID/锁和额外空响应处理；数据库的 DAO、FTS、Repository 文件恢复为基线。
保留必要平台适配、已观察到的 Android 自锁与桌面打包修复，以及原样抽取的压缩。
完整提交对照、保留原因、当前行为与测试步骤见[审查表](cmp-session-migration-audit.md)。

AI 三端 130/80/80、composeApp 三端 110/114/110，以及 Android ChatServiceTest 1 项全部通过，共 625 次。
各目标编译通过；测试使用 Fake/MockEngine，本次审查没有新增真实模型请求。对应新增锁/事务的测试随其实现删除，
原模型参数、提示词/分支、取消/失败及整会话保存契约继续覆盖。临时构建日志清理后提交。

下一项建议：消息分支选择（低难度），直接抽取 Android `selectMessageNode` 的原校验和保存方法体。

## 2026-09-09：消息分支选择运行时 API

迁移前基线：`e85f9e95a`。状态：实现与自动化验证完成；难度低。策略为 `PRESERVE`，适用性 `SUPPORTED`。
目标仍为 Android、Desktop JVM、iOS arm64 / Simulator arm64；沿用现有工具链，无依赖或配置变更。
基线检查成功：Android host 110、JVM 114、iOS Simulator 110 项。

| ID / 位置 | 源集 | 义务 / 技术处理 | 语义风险与动作 | 公开 API 影响 | 状态 / 验证 |
|---|---|---|---|---|---|
| S01 `ChatService.selectMessageNode` | app → commonMain | REQUIRED_FOR_KMP / REWRITEABLE | 原样移动节点查找、索引校验、重复选择跳过与保存；保留异常类型/文案/状态码 | `ChatRuntime` 增加同签名默认方法，Android 保留原 facade | 完成；7 项 common 契约测试 |
| S02 runtime 保存边界 | commonMain / app | REQUIRED_FOR_KMP / REWRITEABLE | 默认实现直接调用既有 getConversationFlow/saveConversation，不改存储或并发 | SharedChatRuntime 直接继承；现有 Android Web 路由调用不变 | 完成；JVM SQLite 关闭重开验证 |

两种 API 异常已在 commonMain，原方法体没有 Java/Android API，无需新增兼容类型、回调或依赖。
本项只共享运行时命令；聊天页面现有分支按钮和 Android Web 路由均不改。
没有 `RECOMMENDED` 或 `ARCHITECTURAL_OPTIMIZATION` 类改动。

### 验证步骤与预期结果

| 步骤 | 预期结果 | 实际 |
|---|---|---|
| 将目标节点从第 0 条切至第 1 条 | 仅目标 selectIndex 改变；原候选消息、其余节点和会话元数据保持，保存一次 | 三端通过 |
| 再次选择已选中的条目 | 直接返回，不调用保存 | 三端通过 |
| 传入不存在的节点 ID / 越界索引 / 空节点 | 分别保留原 404 / 400 和错误文案，不调用保存；节点查找先于索引检查 | 三端通过 |
| 两次调用之间修改当前会话 | 下一次读取最新内存，保留新增消息和标题 | 三端通过 |
| 保存抛异常或取消 | 原异常传播，无重试或新增捕获 | 三端通过 |
| 切换后关闭数据库并重新打开同一 SQLite 文件 | 选中分支恢复；候选消息、会话元数据及两条分支的搜索索引保持 | JVM 通过 |

Android host **117**、JVM **122**、iOS Simulator **117** 项通过；另有 Android `ChatServiceTest` 1 项，
本轮共 357 次测试执行，新用例执行 22 次。Android 应用、JVM、iOS arm64、iOS Simulator arm64 和 common metadata 编译均通过。
[命令与结果](evidence/cmp-message-node-selection-2026-09-09/code-tests.txt)已留存。

方法含签名与方法体的逐字比较通过；生产代码只修改 `ChatRuntime` 和 Android `ChatService` 两个文件。
本项没有 UI 变更，不执行 GUI；没有模型调用、临时生产日志、依赖或数据库格式变更。
SQLite 测试临时文件已自动删除，构建临时日志在提交前清理。

下一项建议：**消息删除后的节点与分支索引计算（低难度）**，直接抽取 Android 的
`buildConversationAfterMessageDelete`，继续保留各运行时原有的保存和文件处理方式。
