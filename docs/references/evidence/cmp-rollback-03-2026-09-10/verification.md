# 第 03 项回退验证：请求日志与计时接口

日期：2026-09-10（Asia/Shanghai）。起点：`3c8a274e`。原始结构基线：tag `2.4.5`。

## 改动与保留范围

生产代码只修改 `app/src/main/java/me/rerere/rikkahub/data/ai/RequestLoggingInterceptor.kt`：
增加 6 行、删除 44 行，净减少 38 行。

- 删除三个专用接口：`RequestLogSink`、`RequestTimeSource`、`RequestTimeMark`。
- 删除两个仅转发到现有 API 的对象：`LoggingRequestLogSink`、`MonotonicRequestTimeSource`。
- 删除注入这些包装的内部构造参数和转发构造函数，恢复原公开无参类结构。
- 原位置直接调用 `Logging.isRequestLoggingEnabled()` / `Logging.logRequest()` 和
  `TimeSource.Monotonic.markNow()` / `elapsedNow().inWholeMilliseconds`；计时变量恢复原名 `startTime`。

保留现有 common `Logging` 包和 `kotlin.time` 时间 API。单调计时来自已有独立提交 `614f3f34`，
目的为避免系统时间调整影响耗时；本次没有改变这一现有选择，也不将其误称为新增抽象的必要条件。
实际 Ktor 替换所需的 `common/.../RequestLoggingPlugin.kt` 保持现状，不纳入本项删除范围。

源集内五个被移除类型的引用均为 0。没有增加日志开关缓存、请求/响应保护、异常捕获范围、重试或状态机。

## 与 tag 的完整文件比较

在仓库根目录运行下列比较，结果通过。除了日志包名和保留的标准库时间 API，整个文件与 tag 一致：

```sh
python3 - <<'PY'
from pathlib import Path
import subprocess

path = 'app/src/main/java/me/rerere/rikkahub/data/ai/RequestLoggingInterceptor.kt'
baseline = subprocess.check_output(['git', 'show', f'2.4.5:{path}'], text=True)
expected = baseline.replace('me.rerere.common.android.', 'me.rerere.common.logging.')
expected = expected.replace('import okio.Buffer\n', 'import okio.Buffer\nimport kotlin.time.TimeSource\n')
expected = expected.replace(
    'val startTime = System.currentTimeMillis()',
    'val startTime = TimeSource.Monotonic.markNow()',
)
expected = expected.replace(
    'System.currentTimeMillis() - startTime',
    'startTime.elapsedNow().inWholeMilliseconds',
)
assert Path(path).read_text() == expected
print('Full-file tag comparison: PASS')
PY
```

## 回退前后契约测试

新增 [RequestLoggingInterceptorTest.kt](../../../../app/src/test/java/me/rerere/rikkahub/data/ai/RequestLoggingInterceptorTest.kt)，
共 10 个 JUnit 测试。测试始终使用生产无参 `RequestLoggingInterceptor()` 和真实 `Logging` 存储。
先写测试并在未回退生产代码上运行，通过后才删除包装；回退后测试文件未作调整。

受控响应/失败通过 OkHttp 原生应用拦截器作为链路末端提供，使用真实 OkHttp Chain，未实现新的生产测试接口。
额外使用 JDK 本地 HTTP 服务和 `.addNetworkInterceptor(RequestLoggingInterceptor())` 完成真实往返，
与生产的注册方式一致。没有新增测试依赖。

| 操作 / 输入 | 预期结果 | 回退前 | 回退后 |
|---|---|---|---|
| 关闭日志，发送带可计数请求体的请求 | 原请求/响应对象保持，仅调用后续链一次；日志不序列化请求体、不访问响应 source，无日志 | 通过 | 通过 |
| 开启日志，使用 Unicode 请求/响应体、重复请求/响应头 | 记录原 URL、method、正文与状态；重复头按原规则取最后值；响应 source 未被访问，调用方仍能读到完整内容；日志页所读的 `getRecentLogs()` 返回同一记录 | 通过 | 通过 |
| GET 返回 400、429、500 | 仍返回原响应，不转成异常；requestBody 为 null，记录状态码，响应体保持 | 通过 | 通过 |
| 后续链抛出有消息/无消息 IOException、CancellationException | 每次仅记录一个失败日志，原异常对象向外抛出；保留请求字段；responseCode/duration 为 null | 通过 | 通过 |
| 后续链抛出 AssertionError | 保留原 `catch (Exception)` 边界，错误对象原样传播，不新增日志 | 通过 | 通过 |
| 请求体序列化先抛出 IOException | 不调用后续链，原异常对象向外抛出；维持原来该阶段不记录失败日志的行为 | 通过 | 通过 |
| 同一个拦截器实例依次切换关闭/开启/关闭/开启 | 每次读取当前开关，无旧值缓存；日志条数依次为 0/1/0/1 | 通过 | 通过 |
| 请求进行期间关闭日志，分别成功/失败结束 | `Logging` 保留写入时的开关判断，不产生最终日志；成功响应与原失败对象保持 | 通过 | 通过 |
| 后续链中实际等待 30 ms | duration 单位为毫秒、非负，位于 25 ms 至外部测量耗时加 1 ms 的区间；不注入假时钟 | 通过 | 通过 |
| 本地 HTTP 服务分别在日志关闭/开启时接收请求 | 服务端收到的 method、路径/参数、header、Unicode 正文一致；调用方收到完整流式格式文本；关闭只做网络写入一次，开启按原行为额外序列化一次用于日志 | 通过 | 通过 |

前九项覆盖原拦截器入口和真实日志存储；最后一项使用本机回环 HTTP 实连。
异常/取消由受控末端触发；流式响应验证内容完整与不提前读取 source，不将其扩大为实际 SSE 分片到达时序验证。

## 命令与结果

回退前：

```sh
./gradlew :app:testDebugUnitTest \
  --tests me.rerere.rikkahub.data.ai.RequestLoggingInterceptorTest --console=plain
```

退出 0，`BUILD SUCCESSFUL in 7s`。10 tests，0 failures/errors/skipped。
测试 XML 时间戳 `2026-09-09T16:45:18.750Z`。

回退后：

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain
```

退出 0，`BUILD SUCCESSFUL in 5s`。

| 检查 | 实际结果 |
|---|---|
| 全部 app JVM 单元测试 | 10 类、63 tests、0 failures/errors/skipped |
| 新增拦截器契约测试 | 10 tests、0 failures/errors/skipped，XML 时间戳 `2026-09-09T16:46:42.154Z` |
| Android Debug | Kotlin 编译、DEX 和 APK 打包通过，`assembleDebug` 完成 |
| tag 完整文件比较 | 仅允许上述日志包名与时间 API 替换，通过 |

Gradle 报告既有的 `ExperimentalNavigation3Api` opt-in marker 无法解析警告；未导致编译或测试失败。
本项没有改共同代码或 iOS/桌面代码，不重复运行其他目标的测试和构建，也不借用第 02 项结果作为本轮测试数。

## GUI 决策

本项 **免 GUI**，依据为：

1. 生产公开无参构造入口保持原样，Android `DataSourceModule.kt` 中
   `.addNetworkInterceptor(RequestLoggingInterceptor())` 的实际注册位置和方式均未改动。
2. 删除的是仅供该文件转发的内部接口/对象；原请求、异常、头转换和日志记录方法体归位。
   common `Logging` 存储、Ktor 插件和 `LogPage` 的读日志/切换开关接线均未改动。
3. 测试通过真实 `Logging.getRecentLogs()` 验证日志页的数据入口，并覆盖同实例开关变化；
   本地 HTTP 测试另覆盖真实 network interceptor 注册与请求往返。
4. UI、导航、生命周期、平台能力、资源和打包配置均未改变。

此结论不声称实际操作过日志页面；本轮没有启动 GUI 或子 agent，也没有模型请求或钥匙串访问。

## 清理与提交范围

每个测试结束后关闭日志开关并清空测试进程中的日志；HTTP 服务、客户端执行器和连接池在 finally 中关闭。
没有添加生产临时日志、调试开关或新的依赖。
两份临时 Gradle 输出已在提取上述结果后移除；diff、测试文件格式、证据链接和 `git diff --check` 已复核通过。

提交仅包含拦截器、永久测试、本记录和回退路线图；用户已有的两个 Xcode 工程/方案变更不纳入本项。

下一项建议：把 `McpTokenPolicy.needsRefresh/computeExpiry` 放回原 `McpOAuthCoordinator`，
保留 `Clock` 和必要的 HTTP/平台授权实现；先通过 Coordinator 入口验证刷新前置条件、60 秒边界、
请求参数及 expiresAt，保持原刷新流程。
