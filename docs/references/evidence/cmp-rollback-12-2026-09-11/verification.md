# 第 12 项：JavaScript HTTP 适配收敛

起点：`28568f63f25e678ec555b20c5c175bd80e9dee57`，对照 tag `2.4.5`，审计 B10。状态：回退、代码/三端 GUI 验证及临时资料清理全部完成。

## 范围判断

删除空 RuntimeObserver 及其构造转发；删除只有 Ktor 一个实现的 HTTP Transport/Call 和镜像请求/响应。
Executor 直接接收现有 HttpClient，Ktor 请求方法体归入原 QuickJSFetch 文件；活动请求继续用原 Job 取消。
同步 fetch polyfill 与传给脚本的响应 DTO 在 tag 中就存在，保留并放回原文件。
QuickJS-KT、结果封装、console 格式、超时/取消与窄平台 dispatcher 保留。
不改变自定义搜索/抓取、本地 JavaScript 工具的业务方法和页面交互。

## 代码验证步骤与预期

使用真实 QuickJS-KT 和 Ktor MockEngine，先对现有 Transport 执行，再仅调整夹具构造为 HttpClient，
保持同一组测试体和断言。预计在 JVM、iOS 模拟器原生测试、Android 设备测试运行；不以 Android host
替代 Android JNI。共享测试源仅加入这些实际支持 QuickJS 运行时的测试编译。

| 场景 | 预期 |
|---|---|
| 返回值、顺序 setup、跨调用隔离、console 和内建对象遮蔽 | 原 Null/Scalar/Json 分类、文本和日志格式保持；新调用无旧全局变量 |
| 同步 fetch 及 URL、方法/头/正文矩阵 | 仍直接返回 Response；GET/HEAD 无正文，必要方法空正文；JSON、字符集与大小写规则保持 |
| HTTP 非 2xx、空正文、错误 JSON、JS/HTTP 异常 | status/ok/text/json 与异常传播保持，无新增兜底 |
| 无限循环超时、HTTP 等待超时、执行取消、并行请求取消 | 原异常/取消类型保持；中止等待中的请求；另一执行及共享 HttpClient 仍可用 |

## GUI 决策

需要 Android、iPhone 17 Pro Max 和桌面验证。自定义搜索的 HttpClient 构造接线改变，且同步 JS/HTTP
与取消依赖实际运行时；代码测试不能单独证明最终打包应用的页面与平台 engine。
由 gpt-5.6-terra 子 agent 操作，Android 使用 android-cli，iOS 使用 Build iOS Apps，桌面独立 profile。
在原搜索设置测试区执行本机固定脚本，核对成功、错误后重试、等待请求时离页取消、离页重入及冷启动。
测试区没有单独取消按钮，使用真实离页生命周期，不虚构控件。原设置精确备份和回读恢复，凭据不进入仓库。
本项不访问上轮保留的默认桌面实例，不发送真实模型请求。

## 实际代码结果与范围证明

删除 7 个额外类型：JavaScriptRuntimeObserver、NoOpJavaScriptRuntimeObserver、JavaScriptHttpRequest、
JavaScriptHttpResponse、JavaScriptHttpCall、JavaScriptHttpTransport、KtorJavaScriptHttpTransport。
生产代码共 5 文件，增加 102 行、删除 191 行，净减少 **89 行**；其中整文件删除 KtorJavaScriptHttpTransport。
原类中的一次性执行保护只针对已删除的 Call 对象，产品原来只调用一次；不为直接请求再创建新的状态对象。
原活动请求状态保存原生 Job，保留安装/清除位置、既有取消标志与并发判断；执行前后取消、两个阻塞调度边界、
QuickJS 创建/关闭、异常和结果格式保持。没有新增锁、空结果保护、请求校验或依赖。

fetch polyfill 与 HEAD 和 tag 2.4.5 的内容逐字节相同；HttpResponseDto 的字段保留原脚本响应契约。
bodyForMethod 除去 DTO 接收者、改用原三个字段作参数外，方法体逐字节一致。它保留现有 Ktor 的 Content-Type
大小写与 charset 适配，不在本次删除包装时重新改写为旧 OkHttp 细节。CustomJsSearchService 除构造参数和
删 import 外逐字节不变；本地 JavascriptTool、平台 dispatcher 未改。见 [范围核对](scope-proof.json)。

新增 JavaScriptExecutorContractTest 共 13 项，在回退前后分别于 JVM、iOS 原生模拟器、Android 设备通过；
测试体和断言逐字节保持，只把夹具构造的 KtorJavaScriptHttpTransport(client) 改为 client。
Android 设备任务另带 1 项既有测试，因此每次设备任务为 14 项。搜索模块 jvmTest 为 NO-SOURCE，不能算作
已运行搜索单元测试；实际产品搜索链路交给本轮三端 GUI 核对。

共享 javaScriptTest 源集只加入 JVM、iOS 和 Android 设备；Android host 无法加载 Android JNI，不把这些测试
加入 host。初次测试接线有跨 source-set tree 警告，随后依据 Android KMP 文档将设备测试树设为 test，警告消失。
这是测试配置变化，生产 source-set 和依赖保持。Android host 原有 1 项测试另外通过。

```bash
./gradlew :common:jvmTest --console=plain
./gradlew :common:iosSimulatorArm64Test :common:assembleAndroidDeviceTest --console=plain
./gradlew :common:connectedAndroidDeviceTest --console=plain
# 回退后同组测试
./gradlew :common:jvmTest :common:iosSimulatorArm64Test \
  :common:connectedAndroidDeviceTest :search:jvmTest --console=plain
# 应用回归、平台编译及最终打包
./gradlew :common:testAndroidHostTest :composeApp:jvmTest :app:testDebugUnitTest \
  :composeApp:compileCommonMainKotlinMetadata :composeApp:compileKotlinIosArm64 \
  :composeApp:linkDebugFrameworkIosSimulatorArm64 :app:assembleDebug \
  :desktopApp:createDistributable --console=plain
```

最终应用构建成功（1 分 42 秒，480 tasks / 128 executed），composeApp 247 项、app 63 项、common host 1 项通过；
加上本轮 common JVM 13、iOS 13、Android 设备 14 项，共 **351 次测试执行**，失败/错误/跳过均 0。
其中跨平台共用的 13 项按每个实际运行目标分别计数，不声称为 351 个不同测试逻辑。
既有 deprecated、expect/actual beta 等编译警告未改。见 [代码结果](code-results.json) 与 [最终产物](artifacts.json)。

## GUI 固定服务与数据边界

三名子 agent 的实际 turn_context 均为 gpt-5.6-terra/high，见 [模型记录](agent-models.json)。
本机服务监听 127.0.0.1:18772，Android 经 10.0.2.2 访问。每端只追加一个 CMP12 JS 自定义搜索配置，
保持原 search_services 列表及选中位置；原偏好以不输出内容的私有文件备份。桌面使用全新独立 profile。
脚本用同步 fetch 发送 POST、固定平台 header、query 和 resultSize，返回固定 answer/items。
本机控制请求仅选择 ok/error/wait 响应；每次功能验证仍须由真实 GUI 点击执行，不能用控制请求充当应用请求。
错误响应为 503，脚本按原 throw 路径显示 CMP12_HTTP_503；wait 等待 45 秒，服务观察客户端提前断连。
等待期间离开详情后，预期在脚本默认 30 秒超时前取消；重入后重新输入并执行，最后冷启动再执行。
日志只保存固定平台、请求状态、头是否匹配、query 是否非空和 resultSize，不记录用户查询正文或凭据。
本项没有读取 DeepSeek 密钥或发送模型请求，也未向生产代码加入临时日志。

### GUI 工具问题的处理

桌面初轮 CG/AX 查询在 sandbox 中返回空列表，最初误以为应用没有创建窗口。随后同一原生查询在
require_escalated 下可看到窗口且 AX trusted 为 true，确认是工具访问环境问题；没有据此修改生产代码。
初轮误判及其进程不计入通过证据。最终仅对明确属于本轮独立 profile 的 PID/CGWindowID 操作和截图，
不通过 app 路径自动启动默认 profile，不操作上轮保留的默认实例。

iOS 使用 Build iOS Apps 的 XcodeBuildMCP 独立 HID；普通 tap 在当前 Compose/runtime 上未触发点击，
同插件 touch 可用。Android 使用 android-cli 检查/截图并通过实际设备输入；截图缩放需还原到真实像素坐标。
这些是自动化接线调整，不属于应用行为修改。

### 已完成的 iOS 结果

iPhone 17 Pro Max 的成功、503 错误、重试、离页取消、重入及冷启动均通过；等待请求 4 在 10.149 秒取消，
早于默认 30 秒超时。4 张原始截图经父 agent 复核，冷启动图完整显示固定条目标题、URL 和正文。
[逐项记录](gui-ios-verification.md) 保留操作、预期和结果。原始设置已恢复，整个偏好文件哈希一致，
见 [iOS 回读](ios-cleanup.json)。

XcodeBuildMCP 使用其专用 DerivedData 路径，不能将普通 Xcode DerivedData 的同名产物作为本次证据。
父 agent 已独立核对插件产物与实际安装的 RikkaHub 和 RikkaHub.debug.dylib，二者哈希分别完全一致；
最终 dylib 中不再有被删除的 KtorJavaScriptHttpTransport/NoOpJavaScriptRuntimeObserver 类型名。

本地 JavascriptTool 的方法与入口未改；本轮用真实 QuickJS 测试覆盖它依赖的无 fetch 执行、console、
返回值和错误契约，不额外声称执行了聊天中的模型工具调用或抓取脚本 GUI。实际 GUI 覆盖本次改变构造接线的
自定义搜索页面，以及随页面销毁产生的真实平台请求取消。

### 已完成的 Android 结果

Pixel_10_Pro_XL / Android 17 的六项 GUI 场景均通过。服务请求 10 在离开详情后 10.926 秒取消，
重入与冷启动后再运行均成功。8 张保留的原始截图经父 agent 复核，首次成功图完整显示固定结果，
冷启动图分别证明配置/脚本留存和再次执行成功。仅保留对应验证场景截图，启动与误点的中间截图已删除。
见 [Android 逐项记录](gui-android-verification.md)。

原 search_services 配置已恢复且回读一致，测试配置不存在；偏好中唯一其余差异为正常 launch_count。
临时 ACCESS_LOCAL_NETWORK 已恢复 granted=false，app 与本轮启动的模拟器已停止，
见 [Android 清理回读](android-cleanup.json)。最终 ARM64 APK 与已安装 base.apk 哈希一致。

### 桌面自动化接线补充

原生 helper 的截图阴影导致像素与逻辑坐标偏移；过早截图又捕获了 Compose 导航的中途画面。
后续按精确 PID 激活、验证 frontmost PID、等待界面稳定，并用不含阴影的原始窗口截图重新定位。
滚动事件补上鼠标移动通知，文本输入使用逐字符事件；均仅修改私有临时 helper。
这些问题解决后，桌面实际请求开始通过；没有为便于测试向产品加入按钮、默认查询或日志。

## 桌面与最终结果

桌面六项 GUI 全部通过。有效验证使用独立 profile 的 PID 36676 / CGWindowID 2500，冷启动使用 PID 40632 /
CGWindowID 2572；原生窗口尺寸均为 800×600 逻辑点。请求 13–18 对应首次成功、503、重试、离页取消、重入和
冷启动。请求 16 在 1.279 秒取消，早于默认 30 秒超时。冷启动图完整显示固定 answer、item 标题、URL 和正文；
父 agent 在清理前另行回读，脚本内容与夹具一致。见 [桌面逐项记录](gui-desktop-verification.md)。

三端合计 **18 次真实 GUI 搜索请求**，每端 6 次。固定平台 header、非空 query、resultSize=10 和 POST 方法均符合
预期，每端有 4 次 HTTP 200、1 次 HTTP 503 和 1 次提前取消。完整记录与汇总见
[服务事件](gui-server-events.jsonl)、[服务结果](gui-server-results.json)。GUI 没有单独覆盖 GET/HEAD 等方法矩阵，
这些由三平台真实 QuickJS + Ktor MockEngine 合约测试覆盖；不能把本轮 POST GUI 扩展称为所有网络场景回归。

保留 Android 8 张、iOS 4 张、桌面 6 张，共 **18 张原始截图**，均经父 agent 目视复核及哈希核对。
部分成功截图只显示 answer 或标题上半部分；每端另有实际截图完整显示固定条目，不对屏外内容作推断。
见 [截图复核](screenshot-review.json)。未编辑图片或用测试 UI 替代真实应用链路。

本项可提交。Android/iOS 设置和临时权限已恢复并回读；Android 模拟器、iOS app、所有本轮独立桌面进程、
固定 HTTP 服务与防休眠进程均已停止，三个 GUI 子 agent 已关闭。私有原始偏好、独立桌面 profile、临时 helper、
线程栈、日志和中间截图已从 /private/tmp/cmp-rollback-12 全部删除。仓库保留无凭据说明、结果、产物哈希和原始
有效截图；没有生产临时日志。既有两份用户 Xcode 文件的工作区修改保持原哈希，未纳入本次提交。
见 [清理总记录](cleanup.json) 与 [桌面清理](desktop-cleanup.json)。上轮默认桌面实例未被本轮操作。

下一项按计划审查第 13 项 E09/E11：SkillManager、SkillStore 与目录镜像，恢复原 SkillManager 的业务归属，
只在不可共享的文件系统能力处保留平台实现。先用临时目录测试原有导入/覆盖/删除/失败行为，再验证三端实际入口。
