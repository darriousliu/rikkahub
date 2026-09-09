# 第 05 项回退验证：Vertex token HTTP 包装

日期：2026-09-10（Asia/Shanghai）。起点：`b4314f7f`。原始结构基线：tag `2.4.5`。

## 改动与保留范围

生产代码仅修改 `ai/src/commonMain/kotlin/me/rerere/ai/provider/providers/vertex/ServiceAccountTokenProvider.kt`。
增加 19 行、删除 45 行，净减少 26 行。

- 删除 `ServiceAccountTokenTransport`、`ServiceAccountTokenHttpResponse`、`KtorServiceAccountTokenTransport`。
- 删除包装用的构造转发，Provider 直接持有 `HttpClient`，保留标准 `Clock` 和 RSA 签名接口的默认值。
- 把原包装中的 Ktor POST/form 方法体放回 `fetchAccessToken`，直接读响应并保留原 `resp` / `body` 名称。
- 移除仅属于包装、没有生产自定义调用的端点参数；继续请求原固定 Google token URL。

Ktor 是必要依赖替换，RSA 是真实平台能力边界，均保留；Android/JVM 的 JDK 签名与 iOS Security actual 未改动。
本项保留现有 `Clock`、Duration 常量、Base64、Dispatchers.Default 和 MutableStateFlow 缓存实现，
不借删除 HTTP 包装改变其他迁移选择。没有改参数校验、空响应处理、取消、重试、锁、并发或缓存规则。

源集中三个被删除类型的引用归零。唯一生产构造点仍为 `GoogleProvider` 中的
`ServiceAccountTokenProvider(client)`；默认时钟和默认签名实现保持。
审计 E15 原方法名由误记的 `getAccessToken` 更正为 tag 中实际的 `fetchAccessToken`。

## 源代码比较

已验证以下事实：

1. 相对 tag，`generateCacheKey`、JWT header/claim JSON 文本及 `TokenResponse` 定义完全一致。
2. 相对回退起点，HTTP 表单构造与 `http.post` 方法体原样归位，仅将 `response` 改回 `resp`、
   `tokenEndpoint` 改为同值的固定 `TOKEN_ENDPOINT`。
3. 相对回退起点，`fetchAccessToken` 中除该 HTTP 片段外的全部语句完全一致。

以下脚本可在仓库根目录复核，不声称整个文件已经与 tag 完全一致：

```sh
python3 - <<'PY'
from pathlib import Path
import subprocess

p = 'ai/src/commonMain/kotlin/me/rerere/ai/provider/providers/vertex/ServiceAccountTokenProvider.kt'
current = Path(p).read_text()
before = subprocess.check_output(['git', 'show', f'b4314f7f:{p}'], text=True)
tag = subprocess.check_output([
    'git', 'show', '2.4.5:ai/src/main/java/me/rerere/ai/provider/providers/vertex/ServiceAccountTokenProvider.kt',
], text=True)

def section(text, start, end):
    offset = text.index(start)
    return text[offset:text.index(end, offset)]

for start, end in [
    ('    private fun generateCacheKey(', '    /**'),
    ('        val headerJson = ', '        val headerB64 = '),
    ('    @Serializable\n    private data class TokenResponse', '    private fun base64UrlNoPad'),
]:
    assert section(current, start, end) == section(tag, start, end)

old_request = section(before, '        val form = Parameters.build {', '        return ServiceAccountTokenHttpResponse(')
expected = old_request.replace('val response = http.post(tokenEndpoint)', 'val resp = http.post(TOKEN_ENDPOINT)')
assert section(current, '        val form = Parameters.build {', '        val body = resp.bodyAsText()') == expected

old_http = section(before, '        val response = transport.exchange(assertion)', '        val accessToken = ')
new_http = section(current, '        val form = Parameters.build {', '        val accessToken = ')
start = '    suspend fun fetchAccessToken('
end = '    @Serializable\n    private data class TokenResponse'
assert section(before, start, end).replace(old_http, new_http) == section(current, start, end)
print('Tag business blocks and HTTP move comparison: PASS')
PY
```

## 回退前后契约测试

永久测试文件：

- [ServiceAccountTokenProviderTest.kt](../../../../ai/src/commonTest/kotlin/me/rerere/ai/provider/providers/vertex/ServiceAccountTokenProviderTest.kt)：12 项 Provider / 调用链测试。
- [VertexRsaSha256SignerTest.kt](../../../../ai/src/commonTest/kotlin/me/rerere/ai/provider/providers/vertex/VertexRsaSha256SignerTest.kt)：2 项平台签名契约测试。
- [VertexTokenTestData.kt](../../../../ai/src/commonTest/kotlin/me/rerere/ai/provider/providers/vertex/VertexTokenTestData.kt)：专用测试密钥与独立签名向量。

先在原生产代码上运行并通过，再回退；回退后只把夹具中的
`transport = KtorServiceAccountTokenTransport(http)` 改为 `http = http`，输入和断言不变。
归一化该构造参数后的三个文件 SHA-256 前后相同：

| 文件 | SHA-256 |
|---|---|
| ServiceAccountTokenProviderTest.kt | `156999e73a326b5775aa17b0034e1a3d82c0499d6194dc0c6e26f940579e6201` |
| VertexRsaSha256SignerTest.kt | `39cbf440168db4b69daa03a9d65b8c582a4ebeed49d338cc19fe406261c1389a` |
| VertexTokenTestData.kt | `06a8bd8c54a0772dc025d0655f4413e969e7f9a1c949ce7a2048d6f0c1ca7289` |

| 操作 / 输入 | 预期结果 | 回退前 | 回退后 |
|---|---|---|---|
| 固定时钟，以专用 PKCS#8 私钥调用真实 Provider | POST 到固定 token URL，form 只有 grant_type/assertion，Content-Type 保持，不额外带 Authorization | 三目标通过 | 三目标通过 |
| 解码 JWT，检查真实签名 | RS256/JWT header、iss/scope/aud/iat/exp 与原值一致，1 小时有效期，无 padding；签名等于独立 OpenSSL 向量 | 三目标通过 | 三目标通过 |
| scope 换顺序/保留重复项、改邮箱、有效缓存期间换无效私钥 | 缓存键排序但不去重，JWT scope 保留输入顺序；邮箱独立；缓存命中不重新读私钥或签名 | 三目标通过 | 三目标通过 |
| 默认 3600 秒 token；推进 3299 秒，再推进 1 秒 | 3299 秒仍用缓存，3300 秒精确刷新；之后读取新缓存 | 三目标通过 | 三目标通过 |
| 缺失 expires_in 或返回 0/-1/300/301 | 缺失默认 1 小时；0/-1/300 立即不满足缓冲条件，301 当下仍可缓存；无新增归一化 | 三目标通过 | 三目标通过 |
| 请求期间时钟推进 3300 秒 | 仍按请求开始时刻计算 token 到期，紧接着的请求会刷新 | 三目标通过 | 三目标通过 |
| HTTP 200/201/299，空 access token，null expiry 和未知字段 | 空 token 原样返回并缓存，null expiry 取默认值，未知字段忽略 | 三目标通过 | 三目标通过 |
| HTTP 300/400/401/429/500，含换行/Unicode/400 字符正文 | IllegalStateException 保留完整状态与正文，不截断、不缓存、不重试；显式重试可成功 | 三目标通过 | 三目标通过 |
| 缺失/null access_token、非 JSON、错误字段类型 | 缺 token 仍为原错误信息，解析失败仍为序列化异常；失败不缓存，后续调用可恢复 | 三目标通过 | 三目标通过 |
| 传输异常与 CancellationException | 异常类型/消息向调用者传播，不重试、不缓存；后续调用仍可成功 | 三目标通过 | 三目标通过 |
| 平台签名边界抛出错误，随后恢复 | HTTP 尚未发送；错误类型/消息及原始异常或其 cause 保持，后续调用成功 | 三目标通过 | 三目标通过 |
| 同一缓存键两次并发未命中，完成后第三次读取 | 两次分别发送请求，无新增合并/锁；第三次命中缓存 | 三目标通过 | 三目标通过 |
| 真实 GoogleProvider 使用默认 Provider 构造，调用两次模型列表入口 | 换 token 一次；邮箱去空格和 PEM 反转义保持；两次 Vertex URL 和 Bearer 头正确，不使用 API key | 三目标通过 | 三目标通过 |
| 同一测试私钥使用 LF/CRLF，及损坏 PKCS#8 输入 | 每个平台 actual 签名均等于 OpenSSL 已知结果；损坏输入失败，不返回签名 | 三目标通过 | 三目标通过 |

部分行拆开同一个测试内的断言，测试数仍为 14。“三目标”是 JVM、Android host、iOS Simulator Arm64。
Android actual 在主机 JVM 执行，未验证 Android 设备自身的加密提供方；iOS 则执行模拟器中的 Security actual。

HTTP 使用 Ktor MockEngine；ai 的 commonTest 新增一行依赖，版本来自仓库现有版本表。
没有新增生产测试接口或测试 Transport。绝大部分场景使用真实默认 RSA signer，只有失败注入测试使用既有签名边界。
GoogleProvider 调用链的下游模型端点特意 mock 为 503，以检查授权请求与 token 复用，不将其表述为模型解析或真实 Vertex 服务成功。

测试专用 2048-bit RSA 密钥由 `openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048` 生成，
从未关联真实服务账号；两份预期签名由 `openssl dgst -sha256 -sign` 独立生成。
所有请求由 MockEngine 接管，无真实 Google/Vertex 或模型请求，也没有读取用户钥匙串。

## 命令与结果

回退前：

```sh
./gradlew :ai:jvmTest --tests 'me.rerere.ai.provider.providers.vertex.*' \
  :ai:testAndroidHostTest --tests 'me.rerere.ai.provider.providers.vertex.*' \
  :ai:iosSimulatorArm64Test --tests 'me.rerere.ai.provider.providers.vertex.*' \
  --device 469B364C-382C-4068-A012-3CD026296BAE --console=plain
```

退出 0，`BUILD SUCCESSFUL in 8s`。三个目标各 14 tests，均为 0 failures/errors/skipped。
Provider 测试 XML 的 UTC 时间戳分别为 JVM `2026-09-09T17:22:31.244Z`、
Android host `2026-09-09T17:22:31.709Z`、iOS `2026-09-09T17:22:38.432Z`。
模拟器为已存在的 iPhone 17 Pro / iOS 27.0，命令复跑时应使用本机可用的设备 ID。

建立基线时发现 JVM 协程栈恢复会复制签名异常，因此把最初的对象同一性断言改为检查类型、消息和原异常 cause 链；
相应泛型标注也在生产回退前修正。没有为适配回退而调整断言。

回退后：

```sh
./gradlew :ai:jvmTest :ai:testAndroidHostTest \
  :ai:iosSimulatorArm64Test --device 469B364C-382C-4068-A012-3CD026296BAE \
  :ai:compileCommonMainKotlinMetadata :ai:compileKotlinIosArm64 \
  :app:assembleDebug :desktopApp:compileKotlinJvm --console=plain
```

退出 0，`BUILD SUCCESSFUL in 15s`。

| 检查 | 实际结果 |
|---|---|
| ai 全部 JVM 测试 | 9 类、94 tests、0 failures/errors/skipped |
| ai 全部 Android host 测试 | 18 类、144 tests、0 failures/errors/skipped |
| ai 全部 iOS 模拟器测试 | 9 类、94 tests、0 failures/errors/skipped |
| 本项 Provider + RSA 测试 | 各目标仍为 14 tests 全部通过 |
| ai common metadata / JVM / Android / iOS Arm64 / iOS Simulator Arm64 | Kotlin 编译通过；iOS 模拟器测试程序链接与运行通过 |
| Android Debug APK / 桌面 Kotlin | `assembleDebug` / `compileKotlinJvm` 通过 |
| 源码对比与测试输入/断言一致性 | 通过 |

回退后 Provider 测试 XML 的 UTC 时间戳分别为 JVM `2026-09-09T17:24:34.629Z`、
Android host `2026-09-09T17:24:35.028Z`、iOS `2026-09-09T17:24:42.532Z`。
共计 332 次测试执行，包含同一 commonTest 在不同目标的重复执行，不称为 332 个独立场景。
编译有既有 expect/actual Beta、API 弃用等警告，未导致失败。

## GUI 决策与覆盖边界

本项 **免 GUI**，依据为：

1. 只有 POST 方法体归位以及 DTO 读取改为直接响应读取；请求参数和相关行为已在回退前后覆盖。
2. GoogleProvider 默认构造、客户端复用、认证头接线、平台 engine 与 RSA actual 均未改动；
   代码测试另通过真实 GoogleProvider 入口检查默认时钟、PEM 处理及下游 Bearer 头。
3. UI、导航、状态到界面的接线、生命周期、系统资源和打包配置均未改变。
4. RSA 的平台差异已有独立向量与 iOS 模拟器代码运行证据，未将测试向量替换成固定签名桩。

未操作 Android/iOS/桌面 GUI，没有真实 OAuth 服务联通或 Vertex 推理验证。
Android host 不等于 Android 设备测试，桌面编译不等于应用启动，iOS 测试程序不等于应用 GUI 链路。
这些均非本项变化点，本轮没有启动子 agent。

## 清理与提交范围

临时 OpenSSL 密钥文件随生成用临时目录自动删除，仓库中仅保留明确标为合成数据的永久测试向量。
新增测试结束时关闭 HttpClient 与 MockEngine；token 缓存、请求记录和响应均在测试进程内存。
没有增加生产临时日志、调试开关或应用测试配置；临时 Gradle 任务清单与前后输出在提取结果后删除。

提交只包含该 Provider、ai 的测试依赖、三份永久测试/数据文件、本记录、路线图和 E15 方法名修正。
用户已有的两个 Xcode 工程/方案变更保持，不纳入提交。

下一项建议：把 StatsRepository / StatsQueries 的逻辑归回原 StatsVM，先用 Room 和固定日期/时区验证统计，
再由 `gpt-5.6-terra` 子 agent 核对三端统计页的实际数据与切换/返回行为。
