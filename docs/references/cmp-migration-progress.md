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
