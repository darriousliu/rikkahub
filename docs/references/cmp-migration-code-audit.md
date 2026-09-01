# CMP 迁移代码审计（待人工确认）

> 本文档和配套 CSV 是只读审计产物。当前阶段未修改生产代码，所有结论在人工确认前均不作为修复授权。

## 基线与方法

- 迁移前：`5f39f1c1d2298cd88ce908a5a0858d4830885a6b`（2.4.5 分叉点）
- 迁移后：`26a0d759c18070a16dc6d5185f07fc0fdac6cfcf`
- 主分支独有提交：`8349ef2599dfc1cfad3823554441a63fd919dcb4`，单独检查资源语义对齐
- 重命名识别：`git diff --find-renames=20%`
- 非纯移动审计项：**901**；纯移动 `R100`：**230**
- CSV 的每一行均包含 Git 对象或内容哈希证据；规范化相似度仅忽略 package、import、注释和 expect/actual 修饰，不忽略业务语句。

## 总览

| 维度 | 数量 |
|---|---:|
| 原路径修改 | 98 |
| 新增 | 476 |
| 删除且未配对 | 18 |
| 移动并修改 | 309 |
| 风险：阻塞 | 2 |
| 风险：高 | 179 |
| 风险：中 | 273 |
| 风险：低 | 447 |

### 按模块

| 模块 | 非纯移动项 |
|---|---:|
| `composeApp` | 410 |
| `hugeicons` | 151 |
| `app` | 125 |
| `speech` | 46 |
| `ai` | 39 |
| `common` | 39 |
| `search` | 26 |
| `highlight` | 15 |
| `iosApp` | 11 |
| `.swiftpm-locks` | 10 |
| `web` | 9 |
| `locale-tui` | 7 |
| `desktopApp` | 3 |
| `docs` | 2 |
| `material3` | 2 |
| `.gitattributes` | 1 |
| `.gitignore` | 1 |
| `build.gradle.kts` | 1 |
| `gradle` | 1 |
| `settings.gradle.kts` | 1 |
| `web-ui` | 1 |

### 按业务域

| 业务域 | 非纯移动项 |
|---|---:|
| 通用业务 | 181 |
| 图标资产 | 137 |
| 共享UI | 114 |
| 依赖注入 | 83 |
| 文件与图片 | 51 |
| 语音 | 45 |
| 备份同步与归档 | 37 |
| 数据库与持久化 | 34 |
| 主题 | 30 |
| 搜索 | 29 |
| 聊天 | 25 |
| AI Provider | 24 |
| 依赖与构建 | 23 |
| MCP与OAuth | 17 |
| 网络与SSE | 14 |
| 代码高亮 | 14 |
| 设置与DataStore | 13 |
| 助手 | 12 |
| 应用壳与导航 | 6 |
| 资源与本地化 | 6 |
| Web Server | 6 |

## 阻塞项

| ID | 路径 | 原因 | 建议 |
|---|---|---|---|
| CMP-0804 | `iosApp/iosApp.xcodeproj/project.pbxproj` | Xcode 工程硬编码 `DEVELOPMENT_TEAM` | 硬编码开发团队会污染共享工程；审核后恢复空值或迁入不跟踪的本地 xcconfig |
| CMP-0818 | `material3/material-color-utilities` | origin 不包含锁定 gitlink `d936fe80010b`，全新检出不可复现 | 远端无法获取锁定 gitlink d936fe80010b；审核后恢复可达 ref/镜像，或在确认源码语义后更新 gitlink |

## Android/JVM 重复物理实现

发现 **11** 组字节级相同 Kotlin 文件。

| Android | JVM | Blob | 建议 |
|---|---|---|---|
| `common/src/androidMain/kotlin/me/rerere/common/archive/PlatformZipArchive.kt` | `common/src/jvmMain/kotlin/me/rerere/common/archive/PlatformZipArchive.kt` | `afda852fbe78` | 单一共享目录，androidMain/jvmMain 同时引用 |
| `common/src/androidMain/kotlin/me/rerere/common/crypto/PlatformCrypto.android.kt` | `common/src/jvmMain/kotlin/me/rerere/common/crypto/PlatformCrypto.jvm.kt` | `64b352abf82e` | 单一共享目录，androidMain/jvmMain 同时引用 |
| `common/src/androidMain/kotlin/me/rerere/common/crypto/PlatformSecureRandom.android.kt` | `common/src/jvmMain/kotlin/me/rerere/common/crypto/PlatformSecureRandom.jvm.kt` | `6942619d1f9d` | 单一共享目录，androidMain/jvmMain 同时引用 |
| `common/src/androidMain/kotlin/me/rerere/common/js/JavaScriptDispatchers.android.kt` | `common/src/jvmMain/kotlin/me/rerere/common/js/JavaScriptDispatchers.jvm.kt` | `8ab20f26a12b` | androidJvmMain；可行时继续评估 commonMain |
| `composeApp/src/androidMain/kotlin/me/rerere/rikkahub/platform/AndroidCharacterCardMetadataReader.kt` | `composeApp/src/jvmMain/kotlin/me/rerere/rikkahub/platform/JvmCharacterCardMetadataReader.kt` | `0fd897d27599` | 单一共享目录，androidMain/jvmMain 同时引用 |
| `composeApp/src/androidMain/kotlin/me/rerere/rikkahub/platform/AndroidQrCodeRenderer.kt` | `composeApp/src/jvmMain/kotlin/me/rerere/rikkahub/platform/JvmQrCodeRenderer.kt` | `0e08f3b78cd3` | androidJvmMain；可行时继续评估 commonMain |
| `composeApp/src/androidMain/kotlin/me/rerere/rikkahub/platform/AndroidQrImageDecoder.kt` | `composeApp/src/jvmMain/kotlin/me/rerere/rikkahub/platform/JvmQrImageDecoder.kt` | `4bd552fc9928` | androidJvmMain；可行时继续评估 commonMain |
| `composeApp/src/androidMain/kotlin/me/rerere/rikkahub/platform/ApplicationLifecycle.android.kt` | `composeApp/src/jvmMain/kotlin/me/rerere/rikkahub/platform/ApplicationLifecycle.jvm.kt` | `30f14e1f66fe` | androidJvmMain；可行时继续评估 commonMain |
| `composeApp/src/androidMain/kotlin/me/rerere/rikkahub/utils/SharedUiFormatter.android.kt` | `composeApp/src/jvmMain/kotlin/me/rerere/rikkahub/utils/SharedUiFormatter.jvm.kt` | `085b53d291c4` | 单一共享目录，androidMain/jvmMain 同时引用 |
| `highlight/src/androidMain/kotlin/me/rerere/highlight/core/HighlightLock.android.kt` | `highlight/src/jvmMain/kotlin/me/rerere/highlight/core/HighlightLock.jvm.kt` | `6752d78c13dc` | androidJvmMain；可行时继续评估 commonMain |
| `search/src/androidMain/kotlin/me/rerere/search/SearchLocale.android.kt` | `search/src/jvmMain/kotlin/me/rerere/search/SearchLocale.jvm.kt` | `96dbd796af6b` | 单一共享目录，androidMain/jvmMain 同时引用 |

## 未由 Git 配对的删除

| ID | 旧路径 | 静态替代结论 | 风险 |
|---|---|---|---|
| CMP-0051 | `ai/src/main/java/me/rerere/ai/util/SSE.kt` | 由 common/src/commonMain/.../http/SSE.kt 的共享 Ktor SSE 实现替代 | 高 |
| CMP-0063 | `app/src/main/java/me/rerere/rikkahub/data/ai/mcp/transport/SseClientTransport.kt` | 由 Kotlin MCP SDK/Ktor transport 替代，需核对断线、取消和错误传播 | 高 |
| CMP-0064 | `app/src/main/java/me/rerere/rikkahub/data/ai/mcp/transport/StreamableHttpClientTransport.kt` | 由 Kotlin MCP SDK/Ktor transport 替代，需核对 session 与重连语义 | 高 |
| CMP-0077 | `app/src/main/java/me/rerere/rikkahub/data/api/RikkaHubAPI.kt` | 由共享 Ktor/Ktorfit API 边界替代 | 高 |
| CMP-0091 | `app/src/main/java/me/rerere/rikkahub/data/sync/s3/S3Client.kt` | 由 composeApp/commonMain 的 S3Client 替代 | 高 |
| CMP-0092 | `app/src/main/java/me/rerere/rikkahub/data/sync/webdav/WebDavClient.kt` | 由 composeApp/commonMain 的 WebDavClient 替代 | 高 |
| CMP-0120 | `app/src/main/java/me/rerere/rikkahub/ui/components/richtext/LatexText.kt` | 由 composeApp/commonMain 的 RaTeX LatexText 替代 | 中 |
| CMP-0128 | `app/src/main/java/me/rerere/rikkahub/ui/components/ui/permission/README.md` | 说明文档删除，需确认信息是否已并入迁移文档 | 高 |
| CMP-0138 | `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantImporter.kt` | 由 composeApp/commonMain 的 AssistantImporter 与平台文件边界替代 | 高 |
| CMP-0139 | `app/src/main/java/me/rerere/rikkahub/ui/pages/backup/BackupVM.kt` | 由 composeApp/commonMain 的 BackupVM 替代 | 高 |
| CMP-0152 | `app/src/main/java/me/rerere/rikkahub/utils/CacheUtil.kt` | 由 cache4k/共享缓存调用替代，需核对 TTL 与清理边界 | 中 |
| CMP-0155 | `app/src/main/java/me/rerere/rikkahub/utils/CoroutineUtils.kt` | 旧 JVM coroutine helper 被移除，需逐调用点确认结构化并发等价 | 中 |
| CMP-0158 | `app/src/main/java/me/rerere/rikkahub/utils/DiffUtils.kt` | 由 composeApp/commonMain 的 UnifiedDiff 实现替代 | 中 |
| CMP-0162 | `app/src/main/java/me/rerere/rikkahub/web/NsdServiceRegistrar.kt` | 由 ServiceRegistrar 的 Android/JVM/iOS 平台实现替代 | 中 |
| CMP-0176 | `app/src/test/java/me/rerere/rikkahub/utils/DiffUtilsTest.kt` | 旧测试文件未随生产实现提交；迁移验证需使用临时同断言测试 | 低 |
| CMP-0215 | `common/src/main/java/me/rerere/common/http/SSE.kt` | 由 common/src/commonMain/.../http/SSE.kt 替代 | 高 |
| CMP-0896 | `web/src/androidTest/java/me/rerere/rikkahub/web/ExampleInstrumentedTest.kt` | 示例测试删除，无产品能力替代要求 | 低 |
| CMP-0901 | `web/src/test/java/me/rerere/rikkahub/web/ExampleUnitTest.kt` | 示例测试删除，无产品能力替代要求 | 低 |

## 依赖台账

- 版本目录移除 library alias：34；新增：42。
- 移除：`androidx-camera-core, androidx-lifecycle-viewmodel-navigation3, androidx-material3-adaptive, androidx-material3-adaptive-layout, androidx-material3-adaptive-navigation3, androidx-navigation3-ui, androidx-paging-runtime, androidx-room-compiler, androidx-room-ktx, androidx-room-paging, androidx-room-runtime, androidx-room-testing, androidx-work-runtime-ktx, barcode-scanning, coil-okhttp, commons-text, diffutils, floatingx-compose, guava-listenablefuture, huge-icons, jlatexmath, jlatexmath-font-cyrillic, jlatexmath-font-greek, jsoup, koin-androidx-workmanager, modelcontextprotocol-kotlin-sdk, pebble, quickie-bundled, quickjs, retrofit, retrofit-serialization-json, slf4j-android, slf4j-api, sqlite-android`
- 新增：`androidx-exifinterface, androidx-paging-common, androidx-room3-compiler, androidx-room3-paging, androidx-room3-runtime, androidx-room3-testing, androidx-sqlite-async, androidx-sqlite-bundled, cache4k, coil-ktor3, composewebview-jvm, dokar-quickjs, fast-kotlin-diff-core, filekit-core, filekit-dialogs, filekit-dialogs-compose, fleeksoft-ksoup, jetbrains-lifecycle-runtime-compose, jetbrains-lifecycle-viewmodel-navigation3, jetbrains-material3-adaptive, jetbrains-material3-adaptive-layout, jetbrains-material3-adaptive-navigation3, jetbrains-navigation3-ui, json-jvm, kermit, koin-compose-viewmodel, korlibs-template, kotlinx-coroutines-swing, kotlinx-coroutines-test, kotlinx-io-core, kscan, ksoup-entities, ktor-client-darwin, ktor-client-mock, ktor-client-websockets, ktor-http, ktorfit-lib, ktorfit-lib-light, modelcontextprotocol-kotlin-sdk-client, modelcontextprotocol-kotlin-sdk-core, ratex, sentry`

### 版本变化

| Key | 迁移前 | 迁移后 |
|---|---|---|
| `image-viewer` | `1.1.0-alpha.7` | `1.1.1-beta.3` |
| `kotlin` | `2.4.10` | `2.4.20-Beta2` |
| `markdown` | `d79a97cc8e` | `0.7.8` |
| `mcp` | `0.14.0` | `0.15.0` |

## commonMain 平台泄漏

静态扫描未发现 commonMain 直接导入 `android.*`、`java.*`、`javax.*` 或 `sun.*`。仍需通过各 target 依赖解析确认 artifact 没有泄漏。

## iOS 互操作结论

- Firebase SwiftPM linkage 暴露的是 Objective-C API，当前 Kotlin/Native 直接调用方式有效，不应额外增加 Swift 转发层。
- 仅当依赖是纯 Swift、无法导出给 Kotlin/Native 时，才使用 common 接口 + iosMain 顶级 `lateinit var` + Swift adapter，并在 Koin/MainViewController 前赋值。
- Firebase 缺少 `GoogleService-Info.plist` 时仍必须安全禁用并继续启动。

## master 独有提交语义对齐

| Locale | Android 与 master 相同 | Compose 与 Android 相同 |
|---|---|---|
| `values` | 是 | 是 |
| `values-ja` | 是 | 是 |
| `values-ko-rKR` | 是 | 是 |
| `values-ru` | 是 | 是 |
| `values-zh-rTW` | 是 | 是 |
| `values-zh` | 是 | 是 |

## 最高改动量高风险项（完整清单见 CSV）

| ID | 业务域 | 迁移前 | 迁移后 | + / - | Git | 规范化相似度 |
|---|---|---|---|---:|---:|---:|
| CMP-0059 | 依赖注入 | `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt` | `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt` | 174 / 727 | M | 23% |
| CMP-0275 | 设置与DataStore | `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/data/datastore/SettingsStore.kt` | 120 / 397 | R050 | 71% |
| CMP-0091 | 备份同步与归档 | `app/src/main/java/me/rerere/rikkahub/data/sync/s3/S3Client.kt` | `—` | 0 / 453 | D | — |
| CMP-0092 | 备份同步与归档 | `app/src/main/java/me/rerere/rikkahub/data/sync/webdav/WebDavClient.kt` | `—` | 0 / 436 | D | — |
| CMP-0029 | AI Provider | `ai/src/main/java/me/rerere/ai/provider/providers/GoogleProvider.kt` | `ai/src/commonMain/kotlin/me/rerere/ai/provider/providers/GoogleProvider.kt` | 202 / 229 | R070 | 77% |
| CMP-0431 | 文件与图片 | `app/src/main/java/me/rerere/rikkahub/ui/components/ui/UIAvatar.kt` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/ui/components/ui/UIAvatar.kt` | 137 / 254 | R032 | 43% |
| CMP-0185 | 备份同步与归档 | `—` | `common/src/commonMain/kotlin/me/rerere/common/archive/CommonZipArchive.kt` | 379 / 0 | A | — |
| CMP-0064 | MCP与OAuth | `app/src/main/java/me/rerere/rikkahub/data/ai/mcp/transport/StreamableHttpClientTransport.kt` | `—` | 0 / 369 | D | — |
| CMP-0140 | 依赖注入 | `—` | `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/AndroidChatPagePlatformContent.kt` | 366 / 0 | A | — |
| CMP-0477 | 依赖注入 | `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt` | 78 / 286 | R061 | 74% |
| CMP-0363 | 依赖注入 | `—` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/shared/ProductNavigationHost.kt` | 362 / 0 | A | — |
| CMP-0815 | 依赖注入 | `—` | `locale-tui/src/services/resource_compatibility_overlay.py` | 354 / 0 | A | — |
| CMP-0089 | 备份同步与归档 | `app/src/main/java/me/rerere/rikkahub/data/sync/S3Sync.kt` | `app/src/main/java/me/rerere/rikkahub/data/sync/S3Sync.kt` | 176 / 175 | M | 64% |
| CMP-0093 | 备份同步与归档 | `app/src/main/java/me/rerere/rikkahub/data/sync/webdav/WebDavSync.kt` | `app/src/main/java/me/rerere/rikkahub/data/sync/webdav/WebDavSync.kt` | 176 / 174 | M | 66% |
| CMP-0498 | 共享UI | `app/src/main/java/me/rerere/rikkahub/ui/pages/imggen/ImgGenVM.kt` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/ui/pages/imggen/ImgGenVM.kt` | 78 / 264 | R022 | 29% |
| CMP-0516 | 依赖注入 | `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderPage.kt` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/ui/pages/setting/SettingProviderPage.kt` | 188 / 133 | R066 | 78% |
| CMP-0338 | 备份同步与归档 | `—` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/data/sync/webdav/WebDavClient.kt` | 313 / 0 | A | — |
| CMP-0028 | AI Provider | `ai/src/main/java/me/rerere/ai/provider/providers/ClaudeProvider.kt` | `ai/src/commonMain/kotlin/me/rerere/ai/provider/providers/ClaudeProvider.kt` | 144 / 168 | R071 | 76% |
| CMP-0336 | 备份同步与归档 | `—` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/data/sync/s3/S3Client.kt` | 298 / 0 | A | — |
| CMP-0138 | 依赖注入 | `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantImporter.kt` | `—` | 0 / 291 | D | — |
| CMP-0274 | 设置与DataStore | `—` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/data/datastore/SettingsModels.kt` | 290 / 0 | A | — |
| CMP-0030 | AI Provider | `ai/src/main/java/me/rerere/ai/provider/providers/OpenAIProvider.kt` | `ai/src/commonMain/kotlin/me/rerere/ai/provider/providers/OpenAIProvider.kt` | 113 / 165 | R052 | 62% |
| CMP-0493 | 共享UI | `app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/skills/SkillsVM.kt` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/ui/pages/extensions/skills/SkillsVM.kt` | 99 / 169 | R040 | 53% |
| CMP-0372 | 依赖注入 | `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ChatInput.kt` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/ui/components/ai/ChatInput.kt` | 80 / 183 | R071 | 87% |
| CMP-0358 | 文件与图片 | `—` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/service/SharedImageGenerationRuntime.kt` | 261 / 0 | A | — |
| CMP-0102 | 文件与图片 | `—` | `app/src/main/java/me/rerere/rikkahub/service/AndroidImageGenerationRuntime.kt` | 258 / 0 | A | — |
| CMP-0366 | 依赖注入 | `—` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/shared/SharedProductModule.kt` | 245 / 0 | A | — |
| CMP-0488 | 依赖注入 | `app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/PromptPage.kt` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/ui/pages/extensions/PromptPage.kt` | 105 / 137 | R081 | 89% |
| CMP-0259 | MCP与OAuth | `app/src/main/java/me/rerere/rikkahub/data/ai/mcp/McpOAuthClient.kt` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/data/ai/mcp/McpOAuthClient.kt` | 120 / 118 | R064 | 65% |
| CMP-0031 | AI Provider | `ai/src/main/java/me/rerere/ai/provider/providers/openai/ChatCompletionsAPI.kt` | `ai/src/commonMain/kotlin/me/rerere/ai/provider/providers/openai/ChatCompletionsAPI.kt` | 82 / 152 | R081 | 89% |
| CMP-0458 | 依赖注入 | `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantLocalToolPage.kt` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantLocalToolPage.kt` | 95 / 134 | R033 | 40% |
| CMP-0514 | 依赖注入 | `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingPreferencesUIPage.kt` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/ui/pages/setting/SettingPreferencesUIPage.kt` | 69 / 159 | R070 | 76% |
| CMP-0365 | 依赖注入 | `—` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/shared/SharedProductApp.kt` | 227 / 0 | A | — |
| CMP-0329 | 备份同步与归档 | `—` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/data/sync/BackupArchiveService.kt` | 226 / 0 | A | — |
| CMP-0128 | 共享UI | `app/src/main/java/me/rerere/rikkahub/ui/components/ui/permission/README.md` | `—` | 0 / 222 | D | — |
| CMP-0314 | 文件与图片 | `—` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/data/files/FileKitSkillStore.kt` | 219 / 0 | A | — |
| CMP-0515 | 依赖注入 | `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderDetailPage.kt` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/ui/pages/setting/SettingProviderDetailPage.kt` | 94 / 122 | R086 | 94% |
| CMP-0139 | 备份同步与归档 | `app/src/main/java/me/rerere/rikkahub/ui/pages/backup/BackupVM.kt` | `—` | 0 / 214 | D | — |
| CMP-0850 | 语音 | `speech/src/main/java/me/rerere/asr/providers/StepASRController.kt` | `speech/src/androidMain/kotlin/me/rerere/asr/providers/StepASRController.kt` | 8 / 200 | R054 | 65% |
| CMP-0469 | 备份同步与归档 | `app/src/main/java/me/rerere/rikkahub/ui/pages/backup/tabs/ImportExportTab.kt` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/ui/pages/backup/tabs/ImportExportTab.kt` | 72 / 133 | R039 | 61% |
| CMP-0095 | 依赖注入 | `app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt` | `app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt` | 114 / 90 | M | 59% |
| CMP-0063 | MCP与OAuth | `app/src/main/java/me/rerere/rikkahub/data/ai/mcp/transport/SseClientTransport.kt` | `—` | 0 / 201 | D | — |
| CMP-0333 | 备份同步与归档 | `app/src/main/java/me/rerere/rikkahub/data/sync/importer/ChatboxImporter.kt` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/data/sync/importer/ChatboxImporter.kt` | 49 / 151 | R076 | 83% |
| CMP-0218 | 依赖与构建 | `—` | `composeApp/build.gradle.kts` | 199 / 0 | A | — |
| CMP-0465 | 助手 | `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/BackgroundPicker.kt` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/ui/pages/assistant/detail/BackgroundPicker.kt` | 69 / 118 | R037 | 45% |
| CMP-0101 | 通用业务 | `app/src/main/java/me/rerere/rikkahub/service/ChatNotificationManager.kt` | `app/src/main/java/me/rerere/rikkahub/service/AndroidChatNotificationPresenter.kt` | 44 / 141 | R025 | 39% |
| CMP-0509 | 依赖注入 | `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingPage.kt` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/ui/pages/setting/SettingPage.kt` | 82 / 88 | R077 | 79% |
| CMP-0525 | 共享UI | `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/PropertyEditor.kt` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/ui/pages/setting/components/CustomRequestEditors.kt` | 69 / 92 | R051 | 62% |
| CMP-0258 | MCP与OAuth | `app/src/main/java/me/rerere/rikkahub/data/ai/mcp/McpManager.kt` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/data/ai/mcp/McpManager.kt` | 95 / 65 | R044 | 47% |
| CMP-0034 | AI Provider | `ai/src/main/java/me/rerere/ai/provider/providers/openai/ResponseAPI.kt` | `ai/src/commonMain/kotlin/me/rerere/ai/provider/providers/openai/ResponseAPI.kt` | 47 / 112 | R087 | 92% |
| CMP-0200 | 通用业务 | `common/src/main/java/me/rerere/common/js/QuickJSFetch.kt` | `common/src/commonMain/kotlin/me/rerere/common/js/JavaScriptExecutor.kt` | 86 / 71 | R027 | 33% |
| CMP-0309 | 通用业务 | `app/src/main/java/me/rerere/rikkahub/data/export/ExportHooks.kt` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/data/export/ExportHooks.kt` | 59 / 97 | R026 | 42% |
| CMP-0036 | AI Provider | `ai/src/main/java/me/rerere/ai/provider/providers/vertex/ServiceAccountTokenProvider.kt` | `ai/src/commonMain/kotlin/me/rerere/ai/provider/providers/vertex/ServiceAccountTokenProvider.kt` | 88 / 66 | R042 | 48% |
| CMP-0632 | 依赖与构建 | `gradle/libs.versions.toml` | `gradle/libs.versions.toml` | 81 / 61 | M | 65% |
| CMP-0629 | 应用壳与导航 | `—` | `desktopApp/src/jvmMain/kotlin/me/rerere/rikkahub/desktop/Main.kt` | 138 / 0 | A | — |
| CMP-0893 | 依赖与构建 | `web/build.gradle.kts` | `web/build.gradle.kts` | 90 / 47 | M | 50% |
| CMP-0557 | 通用业务 | `app/src/main/java/me/rerere/rikkahub/utils/StringUtils.kt` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/utils/StringUtils.kt` | 35 / 99 | R024 | 43% |
| CMP-0051 | 网络与SSE | `ai/src/main/java/me/rerere/ai/util/SSE.kt` | `—` | 0 / 132 | D | — |
| CMP-0094 | 依赖注入 | `app/src/main/java/me/rerere/rikkahub/di/AppModule.kt` | `app/src/main/java/me/rerere/rikkahub/di/AppModule.kt` | 111 / 16 | M | 59% |
| CMP-0506 | 依赖注入 | `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingMcpPage.kt` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/ui/pages/setting/SettingMcpPage.kt` | 63 / 64 | R089 | 94% |
| CMP-0297 | 数据库与持久化 | `app/src/main/java/me/rerere/rikkahub/data/db/fts/MessageFtsManager.kt` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/data/db/fts/MessageFtsManager.kt` | 76 / 46 | R035 | 45% |
| CMP-0876 | 语音 | `speech/src/main/java/me/rerere/tts/provider/providers/QwenTTSProvider.kt` | `speech/src/commonMain/kotlin/me/rerere/tts/provider/providers/QwenTTSProvider.kt` | 57 / 65 | R034 | 70% |
| CMP-0517 | 依赖注入 | `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingSearchDetailPage.kt` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/ui/pages/setting/SettingSearchDetailPage.kt` | 52 / 68 | R087 | 94% |
| CMP-0335 | 备份同步与归档 | `app/src/main/java/me/rerere/rikkahub/data/sync/s3/AwsSignatureV4.kt` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/data/sync/s3/AwsSignatureV4.kt` | 57 / 62 | R058 | 69% |
| CMP-0846 | 依赖与构建 | `speech/build.gradle.kts` | `speech/build.gradle.kts` | 64 / 55 | M | 26% |
| CMP-0471 | 备份同步与归档 | `app/src/main/java/me/rerere/rikkahub/ui/pages/backup/tabs/S3Tab.kt` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/ui/pages/backup/tabs/S3Tab.kt` | 56 / 60 | R081 | 89% |
| CMP-0262 | MCP与OAuth | `app/src/main/java/me/rerere/rikkahub/data/ai/mcp/McpSessionRegistry.kt` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/data/ai/mcp/McpSessionRegistry.kt` | 81 / 32 | R085 | 89% |
| CMP-0013 | 依赖与构建 | `ai/build.gradle.kts` | `ai/build.gradle.kts` | 51 / 61 | M | 32% |
| CMP-0472 | 备份同步与归档 | `app/src/main/java/me/rerere/rikkahub/ui/pages/backup/tabs/WebDavTab.kt` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/ui/pages/backup/tabs/WebDavTab.kt` | 53 / 57 | R081 | 89% |
| CMP-0178 | 依赖与构建 | `common/build.gradle.kts` | `common/build.gradle.kts` | 54 / 55 | M | 37% |
| CMP-0328 | 通用业务 | `app/src/main/java/me/rerere/rikkahub/ui/pages/stats/StatsVM.kt` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/data/repository/StatsRepository.kt` | 47 / 60 | R024 | 36% |
| CMP-0299 | 数据库与持久化 | `app/src/main/java/me/rerere/rikkahub/data/db/migrations/Migration_11_12.kt` | `composeApp/src/commonMain/kotlin/me/rerere/rikkahub/data/db/migrations/Migration_11_12.kt` | 47 / 57 | R033 | 66% |
| CMP-0038 | 通用业务 | `ai/src/main/java/me/rerere/ai/util/KeyRoulette.kt` | `ai/src/commonMain/kotlin/me/rerere/ai/util/KeyRoulette.kt` | 44 / 59 | R029 | 44% |
| CMP-0167 | 资源与本地化 | `app/src/main/res/values-ja/strings.xml` | `app/src/main/res/values-ja/strings.xml` | 50 / 50 | M | 96% |
| CMP-0168 | 资源与本地化 | `app/src/main/res/values-ko-rKR/strings.xml` | `app/src/main/res/values-ko-rKR/strings.xml` | 50 / 50 | M | 96% |
| CMP-0172 | 资源与本地化 | `app/src/main/res/values/strings.xml` | `app/src/main/res/values/strings.xml` | 50 / 50 | M | 96% |
| CMP-0819 | 依赖与构建 | `search/build.gradle.kts` | `search/build.gradle.kts` | 55 / 44 | M | 28% |
| CMP-0236 | 共享UI | `app/src/main/java/me/rerere/rikkahub/ui/components/ui/AIIcon.kt` | `composeApp/src/androidMain/kotlin/me/rerere/rikkahub/ui/components/ui/AndroidAutoAIIcon.kt` | 34 / 64 | R031 | 36% |
| CMP-0821 | 搜索 | `search/src/main/java/me/rerere/search/BingSearchService.kt` | `search/src/commonMain/kotlin/me/rerere/search/BingSearchService.kt` | 56 / 42 | R039 | 51% |
| CMP-0052 | 依赖与构建 | `app/build.gradle.kts` | `app/build.gradle.kts` | 22 / 74 | M | 84% |

## R100 纯移动 blob 汇总

每个模块的摘要由 `旧路径<TAB>新路径<TAB>blob` 排序后计算 SHA-256；所有项目的旧/新 blob 相同。

| 模块 | 数量 | 清单 SHA-256 |
|---|---:|---|
| `ai` | 18 | `8d6604ad9249e54dc5c932519ddad175895fdea34b11aa437e4f376236d4d66f` |
| `common` | 11 | `e56cb0f260aaf215e1bdc576a62801ffa933d0aa79e0389ee97198dde923b32d` |
| `composeApp` | 51 | `3b0955d51ba6194686c33a3a2034e22fcca6e26e03ffccb1e7ed08f6750610f3` |
| `highlight` | 128 | `f234aaae569d407354eabff4a135da698f2d9398651c7e64dbac7b6a29397837` |
| `material3` | 4 | `c34b3ad94638bd53eb2d4f0609ec0a0714ff1a2717c96f61a209239465e1a522` |
| `search` | 9 | `1af9aadd7e4f3da61b3e9cd5d2d68e819973577a7f011d8edcc9dca476af5a48` |
| `speech` | 7 | `5e1e33e822a5c52f39c4f0498092055c02f74cc131947d6fdcc61f734e9b7c15` |
| `web` | 2 | `26905ef20124b10c4cb2651a78612a78a82fde30fafceefe3e03fa7f84f089fd` |

## 工具与可复现性结论

- `material3/material-color-utilities` 从 `6fd88eb3e95b` 更新到 `d936fe80010b`；目标对象仅在当前本地子模块中存在，origin fetch 返回 `not our ref`，全新工作树无法直接初始化。
- Koin Compiler Plugin 1.1.0 要求 Kotlin 2.3.20+，但其声明的已测试上限为 2.4.0；在当前 Kotlin 2.4.20-Beta2 的临时 app/composeApp 图检查中触发 `IrGenerationExtensionException` / `NoSuchMethodError: IrFactory.createSimpleFunction`，故不启用插件。
- 临时 `verify()` 回退已运行：排除 Android `Context` 和动态 `Boolean` 后，Koin 4.2.2 仍将 lambda 构造的 `EmojiData.categories: List` 识别为缺失定义。该结果是动态提供值的静态误报，需要专用 fixture/注入参数声明后才能继续，当前保持 Blocked。
- 官方参考：[Koin Compiler Plugin 配置](https://insert-koin.io/docs/setup/compiler-plugin/)、[Compile Safety](https://insert-koin.io/docs/reference/koin-compiler/compile-safety/)。

## 验证状态

| 项目 | 状态 | 证据/后续 |
|---|---|---|
| 清单完整性 | 通过 | CSV 901 项；M/A/D/R=98/476/18/309；R100 230 项；重复 actual 11 组；HEAD 未变化 |
| locale-tui 资源校验 | Blocked | `locale-tui/baselines/compose-resources-v1.json` 未提交，`resource-verify` 无法建立比较基线 |
| target 依赖图 | 通过 | 已解析 common、Android、JVM、iosArm64、iosSimulatorArm64；common/iOS 未匹配 Android/JVM-only artifact |
| Koin compiler 临时兼容性 | 不兼容 | 1.1.0 + Kotlin 2.4.20-Beta2 在 IR 阶段 `NoSuchMethodError`；插件未保留 |
| Koin `verify()` 回退 | Blocked | 已运行 app 合并模块；动态 lambda 值被当作构造注入，停在 `EmojiData.categories: List` 误报；临时测试未保留 |
| 子模块全新检出 | Blocked | origin 无法获取 `material3` 锁定 commit；本次仅借助当前工作区已有对象完成临时编译 |
| 生产代码修改 | 未发生 | 本阶段仅新增审计 CSV/Markdown |

## 人工审核方式

1. 在 CSV 的“人工结论”列填写 `同意`、`需修改` 或具体意见。
2. 优先审核风险为“阻塞/高”的项目，再检查所有中低风险非纯移动项。
3. 未经确认，不进入生产代码修正阶段。
