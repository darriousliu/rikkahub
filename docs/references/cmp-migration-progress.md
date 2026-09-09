# CMP 逐项迁移与验证记录

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
