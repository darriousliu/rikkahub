# 第 11 项：MCP Runtime 与闲置资源 DTO 收敛

起点：`f6010b2619ba6b59705aca93d0f359fd42035433`，原始依据 tag `2.4.5`；审计 E04。状态：回退、验证和本轮独立测试资料清理完成；默认桌面额外实例按审批限制保留。

## 范围与判断

McpManager 已经位于 common，且是 McpRuntime 唯一实现。页面、选择器及 SharedChatRuntime 改为直接依赖
McpManager，删除接口转发与仅供页面使用的 hasClient，恢复 tag 中 getClient(config) == null 的判断。
保留现有连接、工具调用和 OAuth 方法体，保留 Ktor、跨平台 MCP SDK、图片落盘及系统 OAuth 回调适配。
McpSessionRegistry 既有并发、重连和状态行为不在本项回退范围内。

全仓 Kotlin 调用点检索表明 McpResource/McpResourceContent 只用于 McpRuntime、McpManager、McpSessionRegistry
之间的 listResources/readResource，无产品或测试调用点；tag 2.4.5 也没有这些资源 API。删除这些闲置方法和镜像
DTO，不新增资源页面。工具调用返回的 SDK 嵌入资源内容仍走原有通用 JSON 分支，纳入代码测试。

生产代码 8 文件，增加 24 行、删除 177 行，净减少 153 行。Manager 保留方法体和 Registry 其余内容通过
逐字节核对，SharedChatRuntime 只有依赖类型及变量名替换。未改变 OAuth、图片落盘、Android ChatService；
核对依据见 [scope-proof.json](scope-proof.json)。没有新增依赖或 expect/actual。

## 验证步骤与预期

使用真实 SettingsStore、McpManager、MCP SDK Client 和 Ktor MockEngine，先运行接口版本基线，再对直接
Manager 使用同一组输入与断言。只替换测试夹具的依赖类型和连接存在性判断。

| 输入/操作 | 预期结果 |
|---|---|
| 初始化连接、tools/list、再次同步、禁用服务器 | 状态和 Client 可见性一致；工具描述更新，原 enable/needsApproval 保持；禁用后 Idle |
| 切换助手、服务器及工具 enable | 可用工具顺序和筛选保持 |
| 调用含嵌套参数的工具，返回文本、图片和嵌入资源 | 方法/参数/自定义请求头不变；图片 bytes/MIME 传给原平台接口；其他内容保留 SDK JSON |
| 工具错误结果、JSON-RPC 错误、取消正在等待的调用 | 原结果/异常/取消语义保持，不新增吞错或保护 |
| 调用不存在的服务器 | 返回原有不可用客户端文字，不发请求 |
| 开始 OAuth 后取消 | 原 Authorizing/NeedsAuthorization 状态传播，回调会话关闭；已有 OAuth 测试继续覆盖交换和刷新 |

实际：新增 McpManagerContractTest 7 项，回退前通过接口、回退后通过 Manager，7 项测试体和断言逐字节保持。
仅夹具的依赖类型与 hasClient 改为 getClient 非空判断。与现有 McpOAuthCoordinatorTest 11 项、
McpConnectionKeyTest 3 项一起，回退前后各 21 项通过。测试使用事件等待，不依赖固定睡眠。
嵌入资源 JSON 保留原 SDK 序列化产生的 annotations/_meta 空字段；未顺带清洗结果格式。

```bash
./gradlew :composeApp:jvmTest --tests '*McpManagerContractTest' \
  --tests '*McpOAuthCoordinatorTest' --tests '*McpConnectionKeyTest' --console=plain
./gradlew :composeApp:jvmTest :app:testDebugUnitTest \
  :composeApp:compileCommonMainKotlinMetadata :composeApp:compileKotlinIosArm64 \
  :composeApp:linkDebugFrameworkIosSimulatorArm64 :app:assembleDebug \
  :desktopApp:createDistributable --console=plain
```

完整检查最终通过，composeApp 34 类 247 项、app 10 类 63 项，共 **310 项**，失败/错误/跳过均 0。
首次完整运行暴露既有 OAuth 测试的异步等待缺口：callbackClosed 只保证回调会话关闭，不保证 SettingsStore
收集器已发布值。测试补上真实调度器下最长 5 秒的 Flow.first 等待，原断言和生产 OAuth 逻辑保持。
修正后完整构建 15 秒成功，477 tasks / 56 executed；iOS build_sim 53.1 秒通过。现有 deprecated/native-access/
Swift header 映射警告未改。测试计数、时间与失败修正记录见 [code-results.json](code-results.json)。
最终三端产物哈希见 [artifacts.json](artifacts.json)，Android 实际安装 base.apk 哈希与最终产物一致。

## GUI 决策

需要三端 GUI：页面/选择器与平台 Koin 注册类型改变，需验证实际应用装配与状态显示。
由 gpt-5.6-terra/high 子 agent 使用本机固定 MCP 服务，验证连接、工具展示/开关、断开/恢复、错误与重试、
离页重进及冷启动持久化，并检查 MCP 选择器。Android 使用 android-cli，iOS 使用 Build iOS Apps 的
iPhone 17 Pro Max，桌面使用独立 profile。资源查看不适用，原本无入口；系统 OAuth callback 实现未变，
本项不把未执行的浏览器授权回调标记通过。临时配置和权限在结束后恢复，生产代码不添加调试日志。

固定服务使用本机端口 18771；Android 经 10.0.2.2 访问，其余端经 127.0.0.1 访问。Good 服务返回两个固定
工具及 schema，Retry 在单独的测试控制请求后由 503 恢复正常。控制请求只改变本机服务响应，随后仍需通过
真实 GUI 触发连接。服务记录平台、固定夹具、RPC 方法、响应状态和固定 header 是否保持，不记录请求正文、
Authorization 或原始偏好。未读取 DeepSeek 密钥、未发送模型请求。

本轮初始夹具使用了含空格的 `CMP11 Good`/`CMP11 Retry`，工具编辑后保存没有生效。核对发现 tag 2.4.5 与
当前页面都通过 `isValidMcpName` 限定名称只能含 ASCII 字母数字，因此保存被原有校验拒绝。父 agent 停止
三端应用后，将仅有的两个测试服务改名为 `CMP11Good`/`CMP11Retry` 并重置测试开关、重启固定服务再验。
这是夹具修正，没有修改生产校验或业务代码。初轮“未保存后重启丢失开关”不能判为持久化回归，也不作为通过
证据。三端修改前的真实设置快照始终保持不动；fixture JSON 单独记录修正原因和修正后的哈希。

选择器按实际入口验证：Android 聊天附件/更多菜单中的 MCP 项，iOS/桌面通过当前助手编辑/详情的 MCP 页。
共享端当前没有聊天 MCP 快捷按钮；AssistantMcpPage 直接使用同一个 McpPicker，不能把未存在的入口列为通过。
服务器启停位于齿轮后的基础设置，修改 Enable 后 Save；列表没有独立 Retry 按钮。错误恢复通过实际重新启用
或应用重启触发重连，具体操作以平台报告为准，不能把仅修改测试服务器响应当作 GUI 重试成功。

桌面曾报告“服务器消失”，父核查本轮独立 Preferences 仍有两台服务，运行日志也正常；当时固定区域截图实际
拍到了 Codex 窗口，不能据此认定列表变空。后续按测试进程激活窗口，并以真实 RikkaHub 窗口截图继续验证，
不修改或重灌偏好来掩盖问题。无关窗口截图仅在私有临时目录，结束时删除。

## 移动端实际结果

Android 与 iPhone 17 Pro Max 均完成连接、工具描述与 `value` 参数展示、工具开关保存/重开、服务器启停、
失败恢复、选择器和冷启动验证。Good 最终保持 echo 审批开启/启用关闭，image 启用；选择器中 Good 为
Connected、已选、`1/2 tools`，Retry 为 Connected、未选、`2/2 tools`。Android 从聊天更多菜单进入，
iOS 从当前助手详情进入。Android 的 Retry 经 OFF/Save/ON/Save 重连，iOS 经实际应用重启重连。

详细步骤、预期和实际结果见 [Android 记录](gui-android-verification.md)、[iOS 记录](gui-ios-verification.md)。
Android 保留 7 张、iOS 保留 6 张原始截图，均经父 agent 目视复核；清理前偏好回读也确认最终开关值。
Android 的 MCP/助手配置已恢复，临时本地网络权限已恢复拒绝，偏好唯一剩余差异为正常 `launch_count`；
模拟器已通过 android-cli 停止。iOS 整个设置文件恢复为原始字节和哈希，应用已停止。
依据见 [Android 清理回验](android-cleanup.json)、[iOS 清理回验](ios-cleanup.json)。

## 桌面实际结果

独立 profile 下的桌面验证全部通过：Good 工具描述/参数展示、开关保存重开、Enable 停用/恢复、
Retry 固定错误及 OFF/Save/ON/Save 恢复、当前 CMP11 Desktop 助手的 MCP 选择器、最终冷启动。
选择器明确显示 Good Connected、已选、`1/2 tools`；Retry Connected、未选、`2/2 tools`。
冷启动后的新进程中 echo 审批开/启用关，image 启用开，父 agent 对设置文件的独立回读也一致。
详细步骤见 [桌面记录](gui-desktop-verification.md)。桌面 9 张、三端合计 22 张原始截图均经父 agent
目视复核，SHA-256 和观察结果见 [截图复核记录](screenshot-review.json)。

桌面自动化的捕获流遇到错误，后续用原生 AX 激活指定测试进程、CGEvent 点击和按窗口 ID 截图完成验证；
保留的截图只包含 RikkaHub 窗口，未按离屏可访问性节点推断操作通过。工具详情的首次“展开”截图实际未展开，
已替换为第二次实际显示 description/value 的原始截图，未编辑或重采样图片。

本机服务证据只统计修正夹具后最后一次启动以来的 JSON-RPC POST，排除初轮无效夹具，
见 [逐条记录](gui-server-events.jsonl) 与 [请求汇总](gui-server-results.json)。三端固定请求头均保持，
均有初始 Retry 失败和控制恢复后真实应用的成功初始化/工具列表请求；没有 GUI 工具调用或模型请求。
未记录的 GET 405 探测不计入请求数，不能据此声称覆盖全部 HTTP 交互。

## 默认桌面实例的处置边界

Sky 按 app 路径读取窗口时额外启动了默认 profile 实例 PID 99566，启动时间与工具调用一致。
关闭操作被自动审批拒绝，理由是无法充分排除用户会话和未保存状态；因此保留该实例，并已向用户说明。
所有测试夹具写入都针对独立 profile。默认 profile 没有启动前快照，不能声称其整个文件保持原始字节；
没有按猜测回退启动计数或删除默认数据。此实例不参与通过证据，详情见
[实例处置记录](desktop-extra-instance-status.json)。

## 清理与最终结论

本项可提交。修正夹具后共记录 **51 次 JSON-RPC POST**：Android 16、iOS 19、桌面 16，
三端固定请求头均保持，失败恢复前后的请求和 GUI 观察相符。已停止固定服务和防休眠进程，
三个 GUI 子 agent 均已结束，Android 模拟器与独立桌面测试进程已停止。Android/iOS 配置恢复已回读；
独立桌面 profile、私有偏好快照、测试服务/原生辅助脚本、临时日志及无效截图均已删除。
只保留无凭据的验证说明、请求摘要及原始截图，未向生产代码添加调试日志。
默认桌面额外实例仍按上述审批限制保留，未将此项标为已关闭。详见
[清理总记录](cleanup.json)、[桌面回读结果](desktop-cleanup.json)。

第 11 项将原调用入口收敛到 McpManager，删除无产品用途的 Runtime/资源镜像和转发，
保留必要的跨平台依赖及文件/OAuth 平台能力。下一项按计划审查 JavaScript executor 中的
Observer、Transport 和镜像 DTO，保留 QuickJS-KT 必需适配及原 fetch、错误、超时和取消语义。
