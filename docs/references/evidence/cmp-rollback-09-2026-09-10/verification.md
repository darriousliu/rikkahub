# 第 09 项：提示词预览与模板包装收敛

起点：`07f91940cf76a94515e74491cd4bf79de2ba17ba`。原始依据：tag `2.4.5`。
对应审计 E10 + B03。代码、构建及三端下述 GUI 范围已通过；本项完成，提交见本文件的 Git 历史。

## 改动与必要边界

删除 10 个额外类型：`AssistantPromptPreviewRuntime`、`CommonAssistantPromptPreviewRuntime`、
`AndroidAssistantPromptPreviewRuntime`、`MessageTemplate`、`MessageTemplateSource`、`MessageTemplateRenderer`、
`TemplateCacheInvalidator`、`MessageTemplateContextFactory`、`DefaultMessageTemplateRenderer`、
`KorteMessageTemplateRenderer`。
生产代码增加 227 行、删除 356 行，净减少 129 行；不含测试、验证记录及用户原有 Xcode 改动。

`AssistantPromptPage` 恢复直接注入、调用原 `TemplateTransformer`，保留原预览消息、`produceState`、
`UiState` 和 `Model(modelId = "gpt-4o", displayName = "GPT-4o")` 的上下文。此模型仅构造模板上下文，不发送请求。
`TemplateTransformer` 恢复原消息循环、角色/文本/时间/日期映射；转 Instant 的时区在一次 transform 开始时读取一次，
每条消息的 createdAt 在映射 parts 前转换。日期/时间格式化沿用原 `TimeUtil` 的做法，各自读取当时的系统时区。
非文本部分原对象、其余消息字段保持。
恢复原名 `AssistantTemplateLoader`，按助手 ID 从同一个 SettingsStore 读取模板。

必要的替库代码保留：Pebble 的 Java Reader/Writer/Engine 换为 Korte 原生 Provider/Templates/Template；
Java 时间格式化使用已有 common 日期 util。模板工厂 `createMessageTemplateEngine` 只配置库：RAW 输出、
缓存和已存在的兼容标签/过滤器；从 `private val pebbleForTag` 到文件结尾逐字节不变。
获取/编译模板失败时清 Korte 失败 deferred 的处理，从旧包装原样归入 Transformer；
已编译模板执行失败仍不清缓存，异常及取消对象原样抛出。

设置变化清缓存是原功能。tag 的 `PreferencesStore.kt` 已在 flow 的 `onEach` 清 Pebble 缓存。
Android 继续原回调时机；iOS/桌面原共享预览每次另建 engine，转用原 Transformer 后需补回同一个缓存失效回调。
两个共享宿主先创建 Korte engine，构造 SettingsStore 时传 `engine::invalidateCache`，Koin 再安装
AssistantTemplateLoader 到原生 root/includes/layouts。没有修改 SettingsStore 的流、更新或持久化实现，
也没有新增业务容器、锁、请求保护或另一套回调接口。Korte 依赖从 implementation 改为 api，
因为桌面宿主需要传递这一原生类型；版本未变、没有新增库。

## 回退前诊断

固定消息时间 `2021-01-03T04:05:06`、Locale.US，以 `{{ time }}|{{ date }}` 同时调用当前 Transformer 和
当前 common 预览，得到：

- 原 Transformer 路径：`4:05:06 AM|Jan 3, 2021`（AM 前为 U+202F）。
- common 独立预览：`04:05:06|2021-01-03`。

临时诊断测试以 assertNotEquals 确认差异，UTC `2026-09-10T07:10:05.186Z` 通过；随后移除诊断代码及临时日志，
仅在 [代码结果](code-results.json) 保留上述无凭据的结果摘要。
这说明被删除的预览包装已让 iOS/桌面的时间/日期与生成输入不同。本项恢复原调用路径，因此这处显示随之恢复，
不能把它描述为“迁移前后所有输出原本相同”。

## 代码验证步骤与预期

新增 `TemplateTransformerContractTest`，使用真实 SettingsStore、真实 Korte 和内存 Preferences。
原 11 项测试的输入、操作和断言不变（一处长行仅换行），夹具构造从被删包装改为原生 engine 和恢复的 loader。
没有给生产代码新增测试接口或开关。

| 步骤 | 预期 | 实际 |
|---|---|---|
| USER/ASSISTANT/SYSTEM/TOOL，各含多个 Text、空文本、Image、metadata 和其他消息字段 | 每个 Text 渲染角色/原文；非 Text 原对象及其余字段保持；RAW 不转义 | 回退前后通过 |
| US/中国 Locale × UTC/纽约/上海时区，重复渲染固定历史消息 | 使用消息时间；本地化 MEDIUM 日期/含秒时间稳定，不取当前时间 | 回退前后通过 |
| context 的模板副本与存储值不同，切换两个助手 | 仍按助手 ID 读取存储模板，不读副本 | 回退前后通过 |
| 空消息列表、不存在的助手 | 仍加载模板；缺失助手抛原生 NotFoundException，并保留 ID | 回退前后通过 |
| 静默改夹具来源，再显式 invalidate | 失效前复用旧编译结果，失效后读新来源 | 回退前后通过 |
| 通过真实 SettingsStore 改模板及无关设置 | 每次设置流变化清缓存；assistants 原 key 持久化，模板刷新 | 回退前后通过 |
| 无效标签编译失败后修正来源 | 清失败 deferred，下次可重试 | 回退前后通过 |
| 对 String 使用 join 导致渲染失败，再静默修来源 | 已编译失败模板仍缓存；显式失效后恢复 | 回退前后通过 |
| 来源抛 IllegalStateException / CancellationException | 原异常对象传播；不污染后续重试 | 回退前后通过 |
| include/extends 使用另一个助手 ID | 同一 loader/context，包含与布局内容正确 | 回退前后通过 |
| null/空集合 for、loop index、default、join、capitalize、upper/lower、replace、缺失变量/RAW | 保留现有 Pebble 兼容实现语义 | 回退前后通过 |
| 在首个变量执行中将系统时区由纽约切到 UTC，输入 DST 缺口 02:30、多消息多 parts | 转 Instant 保留最初纽约时区；格式化重新读系统时区，第一项 03:30、后三项 07:30；不是 ContextFactory 每个 part 重读后得到的 02:30 | 回退后新增测试通过 |

基线：11/11，失败/错误/跳过均 0，XML UTC 时间 `2026-09-10T07:02:35.930Z`。
回退后：12/12，失败/错误/跳过均 0，XML UTC 时间 `2026-09-10T07:32:01.745Z`。
最后一项针对原时区读取位置恢复，未声称在被删 ContextFactory 版本中通过。

```bash
./gradlew :composeApp:jvmTest --tests '*TemplateTransformerContractTest' --console=plain
./gradlew :composeApp:jvmTest :app:testDebugUnitTest \
  :composeApp:compileCommonMainKotlinMetadata :composeApp:compileKotlinIosArm64 \
  :composeApp:linkDebugFrameworkIosSimulatorArm64 :app:assembleDebug \
  :desktopApp:createDistributable --console=plain
```

完整构建成功，477 tasks，61 executed；composeApp JVM 32 类 231 项、app JVM 10 类 63 项，共 **294 项通过**。
common、JVM、Android、iOS Arm64/iOS Simulator Arm64 编译及模拟器 framework 链接通过。
Android APK、桌面分发包通过。iOS 使用 Build iOS Apps 的 XcodeBuildMCP `build_sim`，Debug、
`iosApp.xcodeproj`/`iosApp`、Max UDID `03C090DA-107B-4F9F-BCCD-8D5265D32820`、
`CODE_SIGNING_ALLOWED=NO`，8.6 秒构建成功。已有 deprecated/native-access/header 映射警告不计为失败。

代码测试覆盖固定 JVM Locale/时区和真实引擎行为，不能证明 iOS 的本地 formatter、三个宿主的 Koin/缓存接线
以及实际 Compose 预览更新，所以本项需要 GUI。

## GUI 计划与构建标识

三端均由 `gpt-5.6-terra / high` 子 agent 执行，启动及恢复后已检查实际 session 的 `turn_context`：
实际模型记录见 [gui-agent-models.json](gui-agent-models.json)，包含桌面解锁后新 agent 各恢复轮次的核验。

| 平台 | Agent | 工具 |
|---|---|---|
| Android | `01a08a2e-df09-7a30-b483-c0cde138b719` | android-cli，既有 Pixel_10_Pro_XL |
| iOS | `01a08a2f-7bab-7f91-bc0f-c0aab4adbbbf` | Build iOS Apps，iPhone 17 Pro Max |
| Desktop | `01a08b28-28ab-7410-ac0c-1b3b4ee260b1` | CUA，独立 user.home profile；旧 agent 因锁屏未完成 |

计划：离线模板 `CMP09-A | {{ role }} | {{ message }} | {{ time }} | {{ date }}`，预期两条预览正确显示；
改 B 即时刷新；无效标签显示错误，改有效模板后恢复；原重置按钮恢复 `{{ message }}` 和两条原文；
离页返回及同数据冷启动后保持；完成清理测试助手/字段。若移动端工具无法可靠替换文本，允许精确夹具准备后
由真实 GUI 重置来验证刷新/错误恢复，但不得把程序写夹具描述为键盘或 GUI 编辑通过。

| 构建产物 | SHA-256 |
|---|---|
| `app-arm64-v8a-debug.apk` | `b174509ae52c96fcd42e8e739888c65991e77ada27e5207339d7ebe83150ec2a` |
| Desktop `composeApp-jvm-21362aa1430b5b2c025c754a456cbaf.jar` | `607d2f1e72c1e313ab2367d7d1012dad51e9336b504bf002552b8032ba408e3c` |
| iOS `RikkaHub` | `484959fc68a738655aa73181d7c9db5a55f9b0eeafe97591f4d89c992f847f2c` |
| iOS `RikkaHub.debug.dylib` | `5fc269d8f404469db6b4aa50e19df226a3a9086f93b8da4425b397efca17d30e` |

## Android 实际结果与清理

最终 APK 已安装到既有 `Pixel_10_Pro_XL`；设备上 base.apk 的 SHA-256 与上表一致。
两条 A 预览显示 user/assistant、原中文、`3:49:44 PM` 和 `Sep 10, 2026`，无未渲染变量；离页再进仍为 A，
预览时间随新建预览消息更新。真实重置立即恢复 `{{ message }}` 与两条原文；停止后冷启动，重置值仍持久化。
无效标签显示实际 Korte 错误，真实重置立即清除错误并恢复两条原文。
父 agent 已逐张复核 6 张截图及其哈希。完整步骤见 [Android GUI 记录](gui-android-verification.md)。

ADB 键盘自动化未可靠完成精确替换，A/invalid 通过应用停止时的单字段夹具准备；
通过键盘连续 A→B 编辑未覆盖，不把夹具当作 GUI 输入通过，也不据此认定产品或 Compose 回归。
一次坐标误触触发 Gboard 麦克风权限提示，已拒绝。

唯一测试助手 `CMP09-Android` 已由 GUI 删除。最终只读复核确认该 ID 不存在，原有两个助手 JSON 完整保持，
43 项无关设置与未知根字段字节相同，只有正常启动计数由 2 增至 6。
清理证明见 [android-fixture-cleanup.json](android-fixture-cleanup.json)。应用已停止，模拟器已关闭，
其原有分辨率与密度设置未改动。

## iOS 实际结果与清理

最终 Max 应用显示两条 A 预览，角色、原文、`15:38:26` 与 `2026年9月10日` 均正确；离开再进后仍为 A。
真实重置按钮立即恢复 `{{ message }}` 及两条原文，A 消失；停止/启动同一应用后，字段保持重置值。
第二次夹具 `{% unsupported_tag %}` 显示实际 Korte 错误，真实重置立即清除错误并恢复两条原文。
父 agent 已逐张复核 5 张原始 JPEG。完整操作和哈希见 [iOS GUI 记录](gui-ios-verification.md)。

精确夹具属于测试准备，不计为 GUI 编辑。工具 `type_text` 的单次尝试追加而未替换原内容；
因此键盘精确替换、通过键盘连续 A→B 编辑不计通过。snapshot 曾把竖线识别为斜杠，
原始截图和存储值均为竖线，没有证据表明应用修改了该字符。

两次夹具都只改现有“GUI 回归助手” `9070a2d6-c80c-46bc-b5fd-386f53412ea8` 的 messageTemplate。
验证后实际读回 `{{ message }}`，其他 45 项设置字节相同，其他助手以及该助手其他属性保持；
私有 Preferences 恢复快照已经删除。夹具和清理证明保留为无凭据的 `ios-fixture-*.json`。
导航准备时未记录原选中助手 ID，现保留“GUI 回归助手”选中，不猜测还原；这项选择状态未恢复，
不声称模拟器全部状态与测试前逐字节相同。

## 桌面实际结果与清理

用户解锁后，新 Terra agent 使用上表同一最终产物和独立 profile 完成 GUI 验证。
首次 `type_text` 输入的花括号与字段实际值不一致，未归因于应用；改用工具文档提供的 `sky.paste` 后，
完整模板经真实 GUI 精确输入。A/B 的两条预览分别显示对应前缀、user/assistant、两条原中文、含秒时间和本地日期。
最终 A 可见截图为 `20:03:24` / `2026 Sep 10`；B 为 `20:04:02`，同 profile 冷启动后仍为 B，预览时间为 `20:05:00`。
无效标签显示实际 Korte `Can't find tag unsupported_tag` 错误；粘贴有效 B 后两条预览立即恢复，
再点击原重置按钮，两条预览恢复原中文。

父 agent 发现首批截图未拍到下方预览/实际 Korte 错误，要求补拍。最终保留的 7 张截图已逐张复核，
直接显示 A 字段、A/B 两条预览、B 冷启动、Korte 错误、有效恢复和重置结果；不再保留首批不充分截图。
操作、预期、实际和 SHA-256 见 [桌面 GUI 记录](gui-desktop-verification.md)。
桌面通过 GUI 粘贴完成编辑，未使用程序写模板夹具；最初 `type_text` 路径仍未覆盖，不能据此声称该工具输入问题已修复。

## 交付前清理

本项 GUI agent 均已关闭。Android/iOS 私有 Preferences 快照、临时夹具脚本、截图源文件、诊断日志、
桌面独立 profile 和第 09 项临时目录均已清理，只保留本目录中的无凭据证据与正式契约测试。
第 09 项没有访问钥匙串、发送模型请求或添加生产临时日志。
用户原有两个 Xcode 文件不属于本项：project.pbxproj 的 SHA-256 为
`f95ff422bdeeba6d3e3b20e4d450a14956f69a4ffbbbc5b35bd19ac12a8c18ab`，iosApp.xcscheme 为
`ff136fbb9c0651e08a605696d5af1346ea9fbcc03909072d60b996a22c5b54f0`，构建及最终清理后均保持原值。
