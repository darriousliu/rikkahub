# 本 session CMP 迁移最小化审查

日期：2026-09-09。审查范围为 `66e8370c1` 之后、本次压缩提交 `d8ab61c8` 之前的全部 session 改动，
包含压缩提交本身；不包含更早的 CMP 工程改造。用户原有的两项未提交 Xcode 配置保持不动。

用户要求：优先直接移动文件或方法体，仅对 common 不支持的 Java/Android API 做必要适配；
禁止借迁移大量改写业务逻辑。这一要求已写入根目录 `AGENTS.md`，对后续迁移同样适用。

## 提交逐项结论

| 原提交 | 内容 | 审查发现 | 本次处理 |
|---|---|---|---|
| `9b1e709ac` | 持久化 LRU Key 轮换 | 文件 I/O、缓存目录与同步锁需要平台适配；LRU 算法、JSON、过期及失败降级未改 | 保留；`java.io.File` → kotlinx-io，原同步互斥在各端适配 |
| `d1b187b9b` | AI 自动标题 | 新增空响应过滤、改名保护、标题 CAS 和 FTS 字段更新，超过原样迁移范围 | 撤回；恢复原 `choices[0]`、空标题、数据库重读、整会话保存和 `runCatching` |
| `252a0b541` | AI 追问建议 | 新增请求 ID、锁、取消/开关/消息二次检查、清空落盘和字段事务 | 撤回；恢复原内存清空、DB→内存→传入快照回退、整会话保存和错误处理 |
| `4dab04e3d` | Android 会话创建自锁 | GUI 中实际观察到 ANR；同步观察者在 Map 的未完成工厂中重入 | 保留独立缺陷修复及仪器测试；只将版本通知移到插入完成后 |
| `1d55ce6d5` | 聊天消息翻译 | 请求参数/空 chunk 处理被改写；新增请求状态机、双锁、不可取消清理、来源校验、字段事务和清除落盘 | 撤回；恢复原请求方法体、回调流式更新、完成后整会话保存、异常与同步内存清空 |
| `db93b1a79` | 桌面原生包运行时 | GUI 真实启动失败：DataStore/Protobuf 需要 `sun.misc.Unsafe` | 保留 `jdk.unsupported` 模块，属于已验证的打包兼容修复 |
| `d8ab61c8` | 会话上下文压缩 | 压缩主体与旧 Android 一致；对话框仅资源 API/加载 slot 适配 | 保留；模型回退、分块、空白/非正参数、取消及原保存行为均未重写 |

标题、建议、翻译不再通过专用的条件更新或字段写入绕开原保存路径。
`ConversationDAO`、`MessageFtsManager`、`ConversationRepository` 三个文件已恢复为 session 基线内容，
数据库 schema、消息序列化、FTS 的原整会话维护流程均保持原样。

## 保留的必要接线

| 位置 | 保留原因 |
|---|---|
| `BackgroundTextGeneration` | 原 Android 参数 helper 移动到 common；参数不变 |
| `ConversationTitleGenerator` / `ConversationSuggestionGenerator` | 方法体共用，通过回调访问各 runtime 已有的设置、内存、数据库与错误资源 |
| `TextTranslationGenerator` | 原 `GenerationHandler.translateText` 方法体移动；Android `Dispatchers.IO` 和共享 `Default` 留在原包装层 |
| `MessageTranslationManager` | 仅承载原 ChatService 的翻译、流式回调、保存和清空流程；Locale/资源/scope 由平台提供 |
| `SharedChatRuntime` | 将原缺失功能接到共享实现；标题和建议按 Android 的既有方式作为后台任务启动 |
| `SharedProductApp` | 向原 ProviderManager/SearchService 注入同一个持久化轮换器 |
| `ThinkTagTransformer` | 删除 common 不支持且已被 `[\s\S]` 覆盖的 `DOT_MATCHES_ALL`，匹配规则不变 |
| `SharedCompressContextDialog` / 更多菜单 | 原 Android 对话框主体共用，Android 动画通过 slot 保留；iOS/Desktop 添加现有功能入口 |
| 测试依赖 | 仅已有版本目录中的 coroutines-test / Ktor MockEngine，无生产依赖或工具链升级 |

没有移动整个 Android `ChatService` 或 `GenerationHandler`，因为其中其余内容仍依赖平台服务；
只抽取目标方法及少量输入/输出回调，不引入新的服务架构。
非 Android 的语言名称复用既有 `TranslationLanguage` 元数据；不在列表的标签继续传给模型，
不再添加白名单拒绝。此类标签的 code 将连字符转为下划线，名称保留原标签；Android 保留原 Locale 解析。

## 与之前 session 实现的行为差异

以下均是恢复 `66e8370c1` 的原行为，不是本次新增规则：

- 标题请求返回后重读数据库，再保存生成标题；空白标题可以保存，请求期间手动改名不另加保护。
- 建议开始请求前只清空内存，失败时不另行写入数据库；不新增旧请求失效或设置二次检查。
- 翻译使用原模型参数和普通/Qwen MT 分支，空流不会新增清理 loading 的逻辑。
- 翻译完成后保存当前会话；出错或用户清除仅修改内存，后续原有保存流程才将清空落盘。
  清除操作不新增取消正在执行的翻译请求。

历史 GUI 文档记录的是对应提交的实测结果。特别是 `db93b1a79` 的“清除译文立即落盘”验证，
不能作为当前行为结论；该额外逻辑已按用户要求撤回。翻译展示、折叠等 UI 本次未改。

## 验证步骤与预期结果

| 功能 | 代码验证步骤 | 预期结果 |
|---|---|---|
| LRU | 保留三端真实文件契约测试 | 旧格式、过期、轮换、失败降级及同步互斥保持一致 |
| 标题 | Fake Provider 检查模型/提示词/选中分支、空响应、失败、数据库重读和保存 | 与原 Android 方法的分支及参数一致 |
| 建议 | Fake Provider 检查开关/模型/提示词/解析，以及内存清空与保存回退 | 与原流程一致，不依赖删除的字段更新 API |
| 翻译 | 普通/Qwen MT 请求测试，回调/空流/取消/失败/清除/完成保存测试 | 原请求参数、调用顺序及错误处理保持一致 |
| 翻译 SQLite | 通过 Manager 完成翻译、清除和异常，再重新读取真实数据库 | 成功整会话保存；清除只在后续普通保存后落盘，消息及元数据保持 |
| 压缩 | 三端契约与 SQLite 测试 | 原参数、分块、最近消息及整会话保存不变 |
| 平台编译 | common metadata、Android、JVM、iOS arm64 / Simulator arm64 | 全部通过，公共层没有新增 Java/Android 依赖 |

验证结果：

| 对象 | Android host | JVM | iOS Simulator |
|---|---:|---:|---:|
| ai | 130 | 80 | 80 |
| composeApp | 110 | 114 | 110 |

以上全部通过，无失败、错误或跳过；另有 Android `ChatServiceTest` 1 项通过，共 625 次测试执行。
Android 应用、JVM、iOS arm64、iOS Simulator arm64 和 common metadata 编译均通过。
本次审查相对 `d8ab61c8` 的生产代码净减少 294 行；翻译 Manager 从 220 行收敛为 92 行。
命令与结果见[验证记录](evidence/cmp-session-audit-2026-09-09/verification.txt)。

当前审查已移除只验证新增加固行为的测试，保留原协议契约与真实保存验证；
不再维护单独的 CAS/字段更新及锁竞争测试。审查阶段全部测试使用 Fake/MockEngine，不增加真实模型请求。

下一项建议：**消息分支选择（低难度）**。`ChatService.selectMessageNode` 只有查找节点、校验索引、
替换 `selectIndex`、保存四步，可直接抽取到 common；保留 Android Web 路由 facade，再接共享运行时。
