# Compose Multiplatform 迁移边界

本文档记录 RikkaHub 从 Android 向 Compose Multiplatform（Android、JVM、iOS）迁移时的平台边界、依赖方向和后续实现约束。它是后续业务迁移的执行基线，不表示这些跨平台实现已在当前阶段完成。

## 当前阶段范围

当前阶段只完成非业务准备：

1. 将可迁移模块改造成 KMP 模块，并建立 Android、JVM、iOS source sets；现有源码仅机械移动到 `androidMain`，不改变行为。
2. 接入目标 KMP 依赖、升级工具链，并只做依赖冲突或 API 升级所必需的最小编译适配。
3. 保留旧 Android 依赖和实现，除明确要求替换坐标的冲突项外，不在本阶段迁移调用方。
4. 不实现业务 `expect`/`actual`、不共享页面、不迁移数据库/网络/模板等业务代码；这些工作在后续任务中逐项完成。

`:composeApp` 仅预接入 Room 3 和 Ktorfit 的 runtime/API。Room compiler、KSP target wiring、Room Gradle plugin 和 Ktorfit 代码生成插件将在真实数据库与 service 接口迁入时接线，避免本阶段为了空模型制造业务代码。BuildKonfig 当前只生成字符串型 `VERSION_NAME`、`VERSION_CODE`；`DEBUG` 和各平台 application/bundle identity 后续从平台 build info 提供。

`:document` 和 `:workspace` 保持 Android-only。`:app` 继续作为 Android application shell；`:composeApp` 作为 Android/JVM/iOS 共享入口。`:web` 必须创建 Android、JVM、iOS targets，但当前实现仍放在 `androidMain`，`jvmMain` 和 `iosMain` 暂时为空。

## 平台功能矩阵

下表描述后续业务迁移的目标实现。除“保留”项外，均不得在当前依赖准备阶段提前改写业务逻辑。

| 能力 | Android | iOS | JVM |
|---|---|---|---|
| 通知生命周期 | 保留 `lifecycle-process` 和当前 `ChatNotificationManager` | `ChatNotificationManager` actual，不接入 Android lifecycle | `ChatNotificationManager` actual，不接入 Android lifecycle |
| URL 打开 | `CustomTabsIntent` | `UIApplication.openURL` 打开默认浏览器 | `Desktop.browse` |
| OAuth 授权与回调 | Custom Tabs + deep link | `ASWebAuthenticationSession`，由 callback URL/deep link 回到应用 | 系统浏览器 + Ktor loopback callback；必要时再评估维护活跃的 OAuth 库 |
| 后台任务 | 迁移后移除未使用的 WorkManager 依赖 | 仅作 `BackgroundTasks` 参考 | 仅作 Quartz 或系统调度器参考 |
| 崩溃与监控 | Firebase Android SDK | Firebase Apple SDK（SwiftPM） | Sentry Java SDK |
| 二维码扫描 | KScan 相机页面 | KScan 相机页面，与 Android 共享 mobile UI | 不显示相机页面；用 FileKit 选取图片后交给 KScan 解析 |
| TTS 音频播放 | 保留 Media3 实现 | AVFoundation actual | JavaFX Media actual |
| 图片裁剪 | 保留 uCrop | 跳过裁剪 | 跳过裁剪 |
| TTS 悬浮窗 | 保留 FloatingX 当前逻辑 | `FloatingWindow` 空 actual | `FloatingWindow` 空 actual |
| JavaScript engine | QuickJS-KT | QuickJS-KT | QuickJS-KT |
| Markdown / LaTeX | JetBrains Markdown + RaTeX-CMP | JetBrains Markdown + RaTeX-CMP | JetBrains Markdown + RaTeX-CMP |
| HugeIcons | 项目内 `:hugeicons` KMP 模块 | 项目内 `:hugeicons` KMP 模块 | 项目内 `:hugeicons` KMP 模块 |
| GIF | 注入 `coil-gif` decoder/factory | 不注入 GIF decoder，安全回退到首帧 | 不注入 GIF decoder，安全回退到首帧 |
| Room / SQLite | Room 3 + bundled SQLite | Room 3 + bundled SQLite | Room 3 + bundled SQLite |
| Paging | Paging KMP | Paging KMP | Paging KMP |
| 网络客户端 | Ktor client + OkHttp engine | Ktor client + Darwin engine | Ktor client + OkHttp engine |
| Retrofit 替代 | Ktorfit | Ktorfit | Ktorfit |
| Web Server | Ktor Server 实现 | 空 actual，状态为 unavailable | Ktor Server 实现 |
| Terminal View | 保留 Termux terminal view | 空 actual | 空 actual |
| 日志 | Kermit | Kermit | Kermit |
| HTML 解析与实体 | Ksoup + Ksoup Entities | Ksoup + Ksoup Entities | Ksoup + Ksoup Entities |
| 模板引擎 | Korlibs Template（Korte） | Korlibs Template（Korte） | Korlibs Template（Korte） |
| Diff | `fast-kotlin-diff-core` + 项目内 common unified-diff formatter | 同 Android | 同 Android |
| Metadata Extractor | Android actual | iOS actual | JVM actual |
| JmDNS / Bonjour | JmDNS actual | Network/Bonjour API actual | JmDNS actual |
| Workspace rootfs | 保留 Android 解压与 WorkspaceShell | 不实现 | 不实现 |
| `document` / `workspace` 模块 | 保留现状 | 不迁移 | 不迁移 |

## Web Server 接口约束

`:web` 是三平台 KMP 模块，不得通过删除 iOS target 来规避平台差异。后续迁移服务器实现时遵循以下约束：

- `commonMain` 只定义平台无关的配置、状态和生命周期接口，不得 import 或暴露 `EmbeddedServer<CIO...>`、JmDNS、JWT/JVM provider 等类型。
- Android 与 JVM 可以共享 Ktor Server 实现和服务器依赖；iOS source set 不得声明 Ktor Server、JmDNS、JWT 或其他 JVM-only artifact。
- `start()` 返回平台无关的 handle/state。iOS actual 必须安全 no-op，并明确报告 `Unavailable`，不能伪装成已启动。
- `stop()` 必须幂等；未启动、启动失败和 iOS unavailable 状态下重复调用均无副作用。
- 状态至少应能区分 `Stopped`、`Starting`、`Running`、`Unavailable` 和 `Failed`；错误通过平台无关的数据表达，不把 JVM exception 类型泄露到 common API。
- Android/JVM 的监听地址、端口和关闭行为由平台实现管理，common 调用方不得依赖 CIO engine 的具体生命周期。

建议的接口形态如下，具体命名可在业务迁移时调整：

```kotlin
interface WebServerHandle {
    val state: StateFlow<WebServerState>
    fun stop()
}

expect fun startWebServer(config: WebServerConfig): WebServerHandle
```

## 依赖迁移方向

### Source set 放置

| Source set | 依赖方向 |
|---|---|
| `commonMain` | Compose Multiplatform、KScan core、QuickJS-KT、RaTeX-CMP、Kermit、Room 3、Paging、Ktor core、Ktorfit、JetBrains Markdown、Ksoup、Ksoup Entities、Korte、diff core、MCP client/core、FileKit、kotlinx-io |
| `mobileMain` | 后续放置 Android+iOS 共享的扫码页面和仅移动端 UI；KScan 图片解析 core 仍留在 `commonMain` |
| `androidMain` | Android UI/系统 API、Ktor OkHttp engine、Firebase Android、Media3、uCrop、FloatingX、`coil-gif`、JmDNS |
| `jvmMain` | Ktor OkHttp engine、Sentry Java、JavaFX Media、JmDNS |
| `iosMain` | Ktor Darwin engine；Firebase 由 SwiftPM linkage package 提供；不得混入 JVM-only artifact |
| `web/androidMain`、`web/jvmMain` | Ktor Server CIO、compression、auth-jwt 等服务器依赖 |
| `web/iosMain` | 保持无服务器依赖的可编译空 target，后续只提供 no-op actual |

### 目标依赖

| 领域 | 迁移目标 | 约束 |
|---|---|---|
| 数据 | Room 3 `3.0.1`、SQLite bundled `2.7.0`、Paging `3.5.0` | Room 3 使用 `androidx.room3` 新坐标和包名；准备期与 Room 2 并存 |
| 网络 | Ktor `3.5.1`、Ktorfit `2.7.5`、Coil network Ktor3 `3.5.0` | common 仅依赖 Ktor API，各 engine 放平台 source set |
| 通用能力 | KScan `0.9.2`、QuickJS-KT `1.0.9`、RaTeX `0.1.14`、Kermit `2.1.0` | QuickJS-KT `1.0.9` 已发布并提供 Android/JVM/iOS variants |
| 解析与模板 | JetBrains Markdown `0.7.8`、FleekSoft Ksoup `0.2.6`、Ksoup Entities `0.6.0`、Korlibs Template `6.1.0` | 旧实现先保留，后续迁移调用 |
| 工具 | fast-kotlin-diff-core `1.0.0`、FileKit `0.14.2`、kotlinx-io `0.9.1` | diff 库不提供现用 formatter，后续补 common formatter |
| UI | image-viewer `1.1.1-beta.3`、Haze `2.0.0-alpha03` | 允许使用满足 KMP variants/API 要求的预发布版本 |
| MCP | `kotlin-sdk-client`、`kotlin-sdk-core` `0.15.0` | 不再依赖 umbrella artifact；只做升级所需的最小适配 |
| 平台 SDK | Firebase Apple `12.17.0`、Sentry Java `8.51.0`、OpenJFX Media `21.0.12` | Firebase Apple 通过 Gradle SwiftPM linkage 接入，不提交配置和签名秘密 |

Kotlin Gradle SwiftPM import 当前仍是 Alpha 能力，首次接入必须关联真实 Xcode project。仓库提交 linkage package、工程引用和 `Package.resolved`，但不提交 SwiftPM checkout/cache、`GoogleService-Info.plist` 或签名信息。

其他迁移规则：

- Maven 坐标 `org.jetbrains:markdown`（Kotlin 包名仍为 `org.intellij.markdown`）本身支持 KMP，替换项目 fork 后可直接迁入 common；只为保持现有行为做最小适配。
- HugeIcons 不再依赖外部 artifact；把项目当前显式导入的 144 个图标、依赖闭包、根对象和许可证放入独立 `:hugeicons` KMP 模块，并保留 `me.rerere.hugeicons.*` 包名。许可证和来源声明同时打入 JVM artifact 的 `META-INF`。
- Pebble 迁移到 Korte：`Loader` 对应 `KorteTemplateProvider`，关闭自动转义使用 `KorteTemplateConfig(autoEscapeMode = KorteAutoEscapeMode.RAW)`，清理缓存使用 `invalidateCache()`，取模板使用 `templates.get(name)`，模板缓存使用 `KorteTemplates(root = provider, cache = true)`。Korte 无全局 `defaultLocale` 等价项，locale 必须由调用上下文显式传入。
- Apache Commons Text 的 `unescapeJson` 改为 `Json.decodeFromString<String>("\"$input\"")`；`escapeHtml4`/`unescapeHtml4` 分别改为 `KsoupEntities.encodeHtml`/`decodeHtml`。
- `kotlin("reflect")` 的使用点改为显式 registry/factory，不把 JVM 反射引入 common API。
- WorkManager 当前没有实际 Worker；迁移完成后移除依赖。iOS BackgroundTasks 和 JVM Quartz/系统调度器只记录为未来参考，不在本阶段接入。
- 项目私有 SNAPSHOT 保持原值并记录；其余依赖固定版本，禁止动态版本。

## JDK API 替代原则

| JVM API 领域 | KMP 方向 | 约束 |
|---|---|---|
| 时间 | `kotlinx-datetime` | 本地化格式、时区展示等依赖平台 locale 的行为通过 `expect`/`actual` 实现 |
| 原子类型 | `kotlin.concurrent.atomics` | 仅用于简单原子状态；遵守其内存语义和实验性 API 要求 |
| `ConcurrentMap` / `ConcurrentList` | 用 `Mutex` 封装项目内通用容器 | 不把 JVM concurrent collection 类型暴露到 common |
| 流与字节 IO | Okio 或 `kotlinx-io` | 按现有调用语义选择，不机械替换随机访问代码 |
| 网络 | Ktor | socket、HTTP 和 URL 连接由平台 engine/actual 承担 |
| Base64 / UUID | Kotlin 标准库能力 | 不保留 `java.util` 类型在 common 模型中 |
| ZIP | 平台 actual | Android/JVM 可复用 JVM 实现；iOS 单独实现 |
| 文件系统与文件选择 | FileKit | JVM 扫码图片导入也通过 FileKit 选择文件 |
| NIO buffer | `kotlinx-io-core` | 顺序协议编解码用 `Buffer`；依赖 position/limit/rewind/随机索引时封装 `BinaryReader`/`BinaryWriter` |

常见 NIO 顺序读写映射：

| JVM NIO | `kotlinx-io` |
|---|---|
| `ByteBuffer.wrap(bytes)` | `Buffer().apply { write(bytes) }` |
| `get()` / `put()` | `readByte()` / `writeByte()` |
| `getShort()` | `readShort()` |
| `getInt()` | `readInt()` |
| `getLong()` | `readLong()` |
| 小端 `getInt()` | `readIntLe()` |
| 小端 `putInt(value)` | `writeIntLe(value)` |
| 获取全部字节 | `readByteArray()` |

`kotlinx.io.Buffer` 是可消费的字节队列，读取会移除数据，并不等价于具有 `position`、`limit`、随机索引和 `rewind()` 的 `ByteBuffer`。仅顺序读写时直接使用 `Buffer`；需要游标、回退或随机访问时，应在 `ByteArray` 上封装带 position 的 reader/writer。

## 后续业务迁移门槛

开始迁移任一功能前，应先确认依赖确实发布了 Android、JVM、iOS variants，并确认 common API 不泄露 Android/JVM 类型。每个功能以“common 契约可编译、三个平台 actual 可编译、平台降级行为有测试”为完成标准；空 actual 必须显式表达 unavailable/no-op，不能静默报告成功。
