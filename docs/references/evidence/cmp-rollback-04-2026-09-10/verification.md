# 第 04 项回退验证：McpTokenPolicy

日期：2026-09-10（Asia/Shanghai）。起点：`e5c363e6`。原始结构基线：tag `2.4.5`。

## 改动与保留范围

生产代码仅涉及 commonMain 的两个文件：

- 删除 `data/ai/mcp/McpTokenPolicy.kt`，移除独立业务 Policy 及单次时间读取包装方法。
- 在 `McpOAuthCoordinator.kt` 的 `ensureFreshToken` 内恢复原刷新判断与 `TOKEN_REFRESH_LEEWAY_MS`，
  在原位置恢复私有 `computeExpiry`；刷新和授权码交换两处直接调用该私有方法。
- 构造参数由 Policy 改为已有标准类型 `Clock`，默认 `Clock.System`。

增加 20 行、删除 34 行，生产代码净减少 14 行。源集内 `McpTokenPolicy` / `tokenPolicy` 引用归零。

纠正审计中一个方法名简写：tag 中没有独立的 `needsRefresh` 方法；该逻辑原本就是
`ensureFreshToken` 内的两段判断。因此本次恢复内联位置，没有再造一个私有策略方法。

保留当前 `Clock.now().toEpochMilliseconds()` 与 `expiresIn.seconds.inWholeMilliseconds` 时间 API，
其中秒转毫秒写法已随原 Policy 存在，本项未改动它的数值转换语义。
`AtomicSnapshotMap`、原刷新锁、`McpAuthorizationCoordinator`、Ktor OAuthClient 和平台回调实现保持现状，
其他抽取按路线图在后续项目处理。没有增加校验、空响应保护、重试、请求合并或持久化保护。

## 与 tag 的方法体比较

在仓库根目录运行下列脚本，结果通过。刷新方法仅允许已有接口修饰、Map 与时钟 API 差异；
过期时间方法仅允许保留的时钟与单位转换 API 差异：

```sh
python3 - <<'PY'
from pathlib import Path
import subprocess

base = subprocess.check_output([
    'git', 'show',
    '2.4.5:app/src/main/java/me/rerere/rikkahub/data/ai/mcp/McpOAuthCoordinator.kt',
], text=True)
current = Path(
    'composeApp/src/commonMain/kotlin/me/rerere/rikkahub/data/ai/mcp/McpOAuthCoordinator.kt'
).read_text()

def section(text, start, end):
    return text[text.index(start):text.index(end)]

expected = section(base, '    suspend fun ensureFreshToken(', '    suspend fun needsAuthorization(')
expected = expected.replace('    suspend fun ensureFreshToken(', '    override suspend fun ensureFreshToken(')
expected = expected.replace('.computeIfAbsent(configInput.id)', '.getOrPut(configInput.id)')
expected = expected.replace('System.currentTimeMillis()', 'clock.now().toEpochMilliseconds()')
assert section(
    current, '    override suspend fun ensureFreshToken(', '    override suspend fun needsAuthorization('
) == expected

expected = section(base, '    private fun computeExpiry(', '    private fun looksUnauthorized(')
expected = expected.replace('System.currentTimeMillis()', 'clock.now().toEpochMilliseconds()')
expected = expected.replace('expiresIn * 1000', 'expiresIn.seconds.inWholeMilliseconds')
assert section(current, '    private fun computeExpiry(', '    private fun looksUnauthorized(') == expected
print('Tag method comparison: PASS')
PY
```

这不是整个 Coordinator 与 tag 完全等同的声明；其他迁移差异仍在审计路线图中。
本次 diff 已核对：授权方法只去掉 `tokenPolicy.` 调用前缀，授权步骤、异常与清理顺序未改。

## 回退前后契约测试

新增 [McpOAuthCoordinatorTest.kt](../../../../composeApp/src/commonTest/kotlin/me/rerere/rikkahub/data/ai/mcp/McpOAuthCoordinatorTest.kt)，
共 11 个测试，通过 `ensureFreshToken` 和 `startAuthorization` 入口执行真实业务代码。

测试先在未回退生产代码上通过，再执行回退。回退后仅将夹具的
`tokenPolicy = McpTokenPolicy(clock)` 改为 `clock = clock`；其余输入、断言和夹具均保持相同。
归一化这一个构造参数后的测试文件 SHA-256 前后相同：
`bd9fe58f84b7b306e8cb23d80c775a08e7c12d7c056084ab45227a4bac069b92`。

| 操作 / 输入 | 预期结果 | 回退前 | 回退后 |
|---|---|---|---|
| 无 OAuth、禁用、refresh token 为 null/空/空格、未到期、未知/负到期时间，或缺端点/clientId | 不请求、不写入；返回设置内当前配置对象 | 通过 | 通过 |
| 距到期 60,001 / 60,000 / 59,999 ms，已到期；access token 为 null/空/空格 | 60,001 ms 不刷新，60,000 ms 起刷新；缺 access token 时具备其他条件即可刷新 | 通过 | 通过 |
| 同一 Coordinator 将时钟从距到期 60,001 ms 推进 1 ms，再次传入旧配置 | 跨边界只发一次刷新；保存新到期时间；之后读到新设置，不重复刷新 | 通过 | 通过 |
| Streamable HTTP / SSE 配置刷新，携带 client secret、scope、混合大小写 host、query、fragment | POST URL、UTF-8 form、Accept 和全部字段正确；resource 保留 query、去 fragment；不额外发 Authorization 头 | 通过 | 通过 |
| 同上响应返回新 access/refresh token 和 scope，保留另一服务器；重建 SettingsStore | 只替换目标 OAuth；传输类型、URL、名称、工具与其他服务器保持；真实序列化后重读相同 | 通过 | 通过 |
| 请求发出后时钟推进 5 秒，expires_in 缺失/0/-1/120 | 缺失或非正保存 0；120 保存响应时刻加 120 秒；缺失 refresh token/scope 沿用旧值 | 通过 | 通过 |
| client secret/scope 为 null/空/空格，响应 access/refresh token/scope 为空串 | 可选 form 字段省略；响应空串原样保存，不新增验证或旧值回填 | 通过 | 通过 |
| 输入持有旧 refresh token，设置内已保存新 refresh token | 请求与缺失刷新 token 的响应回填均使用设置内最新凭据 | 通过 | 通过 |
| 输入服务器已不在设置中 | 仍按输入刷新并返回新配置；持久化不插入该服务器，不影响其他项 | 通过 | 通过 |
| HTTP 400/401/500、非 JSON、缺 access_token、传输失败和取消异常 | 一次请求、不重试、不写入，返回原配置；保留原 runCatching 消耗刷新取消异常的行为 | 通过 | 通过 |
| 元数据发现 → 回调接口夹具 → 授权码交换；同样推进时钟并检查四种 expires_in | 先保存授权元数据，再保存新 token/正确 expiry；授权与 token 请求保留 PKCE/state/redirect/resource；会话创建/关闭各一次 | 通过 | 通过 |

表中拆开展示部分同一测试内的断言，JUnit 测试数仍为 11。
授权流程同时检查 scope 回退和无 refresh token 的最终保存，保留其与刷新流程不同的行为。

实际使用生产 `McpOAuthCoordinator`、`McpOAuthClient`、`SettingsStore` 和平台 SHA-256 actual。
底层 `DataStore<Preferences>` 为内存实现，记录真实 SettingsStore 写入的序列化 Preferences；
HTTP 为现有依赖中的 Ktor MockEngine，浏览器通过现有 OAuthCallbackSession 接口提供受控回调。
没有新增生产测试接口或依赖。

## 命令与结果

回退前：

```sh
./gradlew :composeApp:jvmTest \
  --tests me.rerere.rikkahub.data.ai.mcp.McpOAuthCoordinatorTest --console=plain
```

退出 0，`BUILD SUCCESSFUL in 3s`；11 tests、0 failures/errors/skipped。
XML 时间戳 `2026-09-09T17:06:51.103Z`。
建立基线时先修正了新增测试的类型标注，以及 Ktor 原有表单 Content-Type 包含 `charset=UTF-8` 的预期；
这两处均在生产代码回退前完成，没有为了让回退通过而改行为断言。

回退后：

```sh
./gradlew :composeApp:jvmTest \
  :composeApp:compileCommonMainKotlinMetadata \
  :composeApp:compileKotlinIosArm64 \
  :composeApp:compileKotlinIosSimulatorArm64 \
  :app:assembleDebug --console=plain
```

退出 0，`BUILD SUCCESSFUL in 50s`。

| 检查 | 实际结果 |
|---|---|
| composeApp 全部 JVM 测试 | 29 类、198 tests、0 failures/errors/skipped |
| 新增 Coordinator 契约测试 | 11 tests 全部通过，XML 时间戳 `2026-09-09T17:08:20.651Z` |
| common metadata / JVM / Android / 两个 iOS Arm64 目标 | 对应 Kotlin 编译通过 |
| Android Debug APK | `assembleDebug` 通过 |
| tag 方法体比较 / 测试断言前后一致 | 均通过 |

编译保留既有 expect/actual Beta、API 弃用等警告，没有编译或测试失败。
本轮未运行原生 iOS 测试、iOS framework 链接或桌面应用打包，不把目标编译等同于运行时验证。

## GUI 决策

本项 **免 GUI**：

1. 刷新判断与到期时间计算归回原方法位置，两处业务入口由回退前后同一组测试覆盖。
2. 唯一生产构造点 `McpManager` 继续省略最后一个默认参数；原 `Clock.System` 行为保持，
   没有修改 DI、UI 状态绑定、导航、浏览器发起或系统授权回调接线。
3. OAuthClient、存储实现、平台生命周期、资源与打包配置均未修改；现有回调接口和其各平台实现保留。

代码验证覆盖所改计算进入 HTTP 请求与序列化保存的链路；内存 Preferences 不代表磁盘耐久性验证，
受控回调不代表 Android/iOS/桌面的真实系统浏览器授权验证。本轮没有启动 GUI 或子 agent。

## 清理与提交范围

测试在结束时取消自己的 scope、关闭 HttpClient 与 MockEngine；数据与虚构凭据仅存在于测试进程内存。
没有添加生产临时日志、测试开关或磁盘种子数据，没有访问 macOS 钥匙串或请求真实模型。
两份临时 Gradle 输出在提取结果后移除；`git diff --check`、测试行宽与本项 diff 已复核。

提交仅包含两个生产文件的归位/删除、永久测试、本记录、路线图及 E16 原方法名修正。
用户已有的两个 Xcode 工程/方案变更保持，不纳入本项。

下一项建议：将 Vertex token Provider 中的二次 HTTP Transport/DTO 包装回退为直接使用 Ktor，
保留平台 RSA 签名，并用 HTTP mock 检查 URL/form/JWT、缓存到期、错误和取消契约。
