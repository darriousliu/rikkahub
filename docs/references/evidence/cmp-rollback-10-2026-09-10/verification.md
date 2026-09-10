# 第 10 项：翻译 Runtime 转发收敛

起点：`90fc09ab`，原始依据 tag `2.4.5`；审计 E03。状态：代码、构建与三端 GUI 验证完成，测试环境已清理。
本项随本次签名提交收敛；iOS 普通软键盘收起路径不在通过范围内，详见下文。

## 范围

移除 TranslationRuntime 及 Android/Shared 两个转发实现，TranslatorVM 直接依赖 SettingsStore 和已有
TextTranslationGenerator。既有 Generator 方法体留待第 24 项归回完整 GenerationHandler；本项不复制或重写翻译业务。
平台 Koin 使用原生 CoroutineDispatcher 保留 Android IO、iOS/桌面 Default。语言枚举属于 Locale 的必要替换，保留。
生产代码增加 25 行、删除 98 行，净减少 73 行。没有新增业务接口、依赖或平台 expect/actual；
CoroutineDispatcher 是已有协程库类型。Settings/目标语言在原调用位置读取一次，输入仍在点击时捕获。
原懒订阅、空白提前返回、任务取消、runCatching/错误流、部分结果及收尾状态保持，未顺带修正取消/并发流程。
TextTranslationGenerator、Android GenerationHandler、TranslationLanguage 与起点逐字节相同。

## 代码验证计划

| 操作/输入 | 预期 |
|---|---|
| 创建 VM，首次订阅设置，再修改提示词/思考预算并重新读存储 | 懒订阅和原 key/持久化保持 |
| 普通模型，点击后改变输入和语言，逐个发出两个片段 | 保留点击时输入和请求时语言，参数/Default 调度保持，实时累计结果 |
| 流式响应中输入空白再点击 | 原请求继续，已显示结果不清空 |
| Qwen MT 和繁体中文 | 非流式请求、Chinese 语言名、temperature/topP/auto 来源与原实现一致 |
| 部分输出后 Provider 抛原错误，再重试 | 错误对象进入原错误流，保留部分结果；重试清空并显示新结果 |
| 选中不存在的模型 | 原错误消息，无 Provider 请求 |
| 部分输出后取消，再发送迟到片段 | 上游停止，保留部分结果，不显示迟到内容 |
| 发起第二次翻译，再清理 VM | 取消旧请求；清理取消当前请求，不改原状态收尾逻辑 |

测试使用真实 SettingsStore、真实翻译生成器和受控 Provider；通过 Channel/Flow 等待事件，不用固定睡眠判断异步完成。
先运行回退前 SharedTranslationRuntime 基线，再调整夹具验证回退后的同一组输入/断言。

实际：上述 8 项基线通过，回退后 8 个场景和断言逐字节保持；另外加入一项原生 IO/Default 调度验证，
由真实 VM/Generator 的 Provider 入口记录上游 CoroutineDispatcher，回退后 9 项全部通过。
已有 TextTranslationGeneratorTest 8 项也通过。结果与 XML 时间见 [code-results.json](code-results.json)。

```bash
./gradlew :composeApp:jvmTest --tests '*TranslatorVMContractTest' --console=plain
./gradlew :composeApp:jvmTest --tests '*TranslatorVMContractTest' \
  --tests '*TextTranslationGeneratorTest' --console=plain
./gradlew :composeApp:jvmTest :app:testDebugUnitTest \
  :composeApp:compileCommonMainKotlinMetadata :composeApp:compileKotlinIosArm64 \
  :composeApp:linkDebugFrameworkIosSimulatorArm64 :app:assembleDebug \
  :desktopApp:createDistributable --console=plain
```

完整构建 1m32s 成功，477 tasks / 70 executed；composeApp JVM 33 类 240 项，app JVM 10 类 63 项，
共 **303 项通过**，失败/错误/跳过均 0。iOS 使用 Build iOS Apps 的 build_sim，Debug、Max 设备、
CODE_SIGNING_ALLOWED=NO，14.2 秒成功。既有 deprecated/native-access/header 映射警告未改动。
Android 设备实际 base.apk 哈希与产物一致；三端产物路径及 SHA-256 见 [artifacts.json](artifacts.json)。

## GUI 决策与预期

需要 Android/iOS/桌面三端 GUI，原因是 VM 构造、Koin 和实际生成入口改变。
预期验证真实翻译页的模型选择、目标语言、开始/流式结果、取消、错误与重试，重进页面及模型设置持久化。
由 gpt-5.6-terra 子 agent 操作，检查实际 session 模型；优先本机固定响应服务，Android 使用 android-cli，
iOS 使用 Build iOS Apps 与 iPhone 17 Pro Max，桌面使用独立 profile。

三端均使用本机固定响应服务 127.0.0.1:18770，Android 经 10.0.2.2 访问。Fast 返回含目标语言码的固定结果，
Slow 先返回部分文本并延迟结束供取消，Error 返回包含 CMP10 标记的 HTTP 500。服务不连接外部模型，
仅记录平台/测试模型/语言/流式状态/结束或断开事件，不记录请求正文、Authorization 或真实配置。

移动端仅临时修改 providers、translate_model、translation_prompt 三个 Preferences；原数据在本机私有快照中备份。
Android 另外 42 项、iOS 另外 43 项在夹具准备时字节相同。桌面为全新独立 profile。
三份 *-fixture.json 记录非敏感 ID、产物配置和哈希；配置准备不是 GUI 通过。
Android 为本轮实际启动的 emulator-5556 / Pixel_10_Pro_XL（已核对 AVD 名称）；不操作其他 Android 模拟器。
实际 GUI 模型已在启动前核对为 gpt-5.6-terra/high，见 [gui-agent-models.json](gui-agent-models.json)。
三端实际操作、预期和结果见 [Android](gui-android-verification.md)、[iOS](gui-ios-verification.md)、
[桌面](gui-desktop-verification.md)。三端均完成 Fast 简中/英文、Slow 流式与取消、Error 可见提示、Fast 重试、
离页重进及冷启动后的模型持久化。输入、结果和目标语言属于 VM 内存态，冷启动为空输入、空结果和简体中文。
共 25 张原始截图经父 agent 逐张视觉复核，哈希见 [screenshot-review.json](screenshot-review.json)。

固定服务收到 17 次请求：桌面 5 次、Android 6 次、iOS 6 次；Android/iOS 各多发一次固定 Error 以捕捉短暂 toast。
三端 Slow 分别在约 14.64、34.05、20.69 秒断开，均早于 45 秒的延迟完成文本，没有向已取消的请求发送完成文本。
服务仅证明测试模型、目标语言、流式参数、提示词前缀及请求终止；界面状态由 GUI 观察核对，
完整参数由代码测试覆盖。脱敏核对结果见 [server-results.json](server-results.json)。

## 验证限制与清理

Android 初次本机请求被原来未授予的 ACCESS_LOCAL_NETWORK 权限阻断；仅测试期间临时授予该包，结束后恢复拒绝。
iOS 通过 Build iOS Apps 在指定 Max 模拟器上操作；输入后软键盘遮住底部按钮，插件 DAP 无表达式能力，
宿主 Simulator 菜单也不可用。最终仅用原生 LLDB 对该应用执行 UIKit `keyWindow endEditing:YES` 并立即 detach，
然后继续使用插件触发真实翻译。没有调用 VM/翻译方法或修改生产文件；普通软键盘收起路径仍未覆盖。
iOS 取消后的判断有实际运行时快照与上游断开记录，没有保留取消后的原图；取消前的截图仅证明 partial、进度和取消按钮。
真实外网模型及所有供应商的 GUI 未覆盖；本轮没有读取或使用 DeepSeek 密钥。

应用停止后恢复移动端原 providers、translate_model、translation_prompt 三项，均完成设备回读。
iOS 整个 Preferences 文件与本轮开始时逐字节相同，剪贴板回读也与原值相同。
Android 除正常 launch_count 外，其余设置均与原值相同；原网络权限已恢复，本轮启动的 5556 模拟器已关闭。
桌面独立 profile 和进程已移除；固定服务已停止，私有设置快照、临时脚本、运行日志和无效截图已清理。
生产代码没有加入临时日志。清理证据见 [Android](android-cleanup.json)、[iOS](ios-cleanup.json)、[汇总](cleanup.json)。
用户原有的两个 Xcode 工程文件改动保持原哈希，未纳入本项提交。
