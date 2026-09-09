# 第 06 项回退验证：统计逻辑归回 StatsVM

日期：2026-09-10（Asia/Shanghai）。起点：`c1b49204`。原始结构基线：tag `2.4.5`。

## 改动与保留范围

7 个生产文件增加 72 行、删除 119 行，净减少 47 行。

- 删除 `StatsRepository`、`StatsQueries`、`RoomStatsQueries` 及 Android/共享模块中的对应绑定。
- `AppStats` 从独立 model 文件回到原 `ui/pages/stats/StatsVM.kt`，字段与默认值不变。
- 原 `loadStats` 方法回到 VM，直接依赖 `ConversationDAO`、`MessageNodeDAO`、`SettingsStore`。
- 删除统计专用 `heatmapStartDate`，在 VM 和 StatsPage 原位置保留等价的日期 API 替换。
- 两个 Koin 入口使用 `viewModel { StatsVM(get(), get(), get()) }`，正常调用仍采用默认 Clock/TimeZone。
  显式三个实参避免构造函数引用把有默认值的 Clock/TimeZone 也当作需要解析的 Koin 依赖。

日期采用 kotlinx.datetime 与标准 Clock；沿用现有时钟/时区可注入值，供固定边界测试使用，没有引入新时钟接口。
DAO、SQLite 查询、Room 平台实现、SettingsStore、启动次数写入、统计卡片、格式化、量级阈值均未改变。
恢复原 50ms 延迟、仅每日聚合在 `Dispatchers.IO` 的范围、读取顺序和单次加载快照。
没有新增校验、空结果保护、异常处理、锁、刷新订阅或数据库条件更新。

## 源代码比较

以下脚本已执行通过，可从仓库根目录复核：

```sh
python3 - <<'PY'
from pathlib import Path
import subprocess

current = Path('composeApp/src/commonMain/kotlin/me/rerere/rikkahub/ui/pages/stats/StatsVM.kt').read_text()
tag = subprocess.check_output([
    'git', 'show', '2.4.5:app/src/main/java/me/rerere/rikkahub/ui/pages/stats/StatsVM.kt',
], text=True)

def body(text):
    return text[text.index('    private suspend fun loadStats()'):]

def data_class(text):
    return text[text.index('data class AppStats('):text.index('class StatsVM(')]

expected = body(tag).replace('LocalDate.now()', 'clock.today(timeZone)')
expected = expected.replace(
    '.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))',
    '.minus((today.dayOfWeek.ordinal - DayOfWeek.SUNDAY.ordinal + 7) % 7, DateTimeUnit.DAY)',
).replace('.minusWeeks(52)', '.minus(52 * 7, DateTimeUnit.DAY)')
assert body(current) == expected
assert data_class(current) == data_class(tag)
print('Tag loadStats after date API substitutions, AppStats: PASS')
PY
```

相对本项起点，StatsPage 仅删除旧 model/helper 导入并原位写回日期计算；卡片、分位数色阶、滚动、导航及格式化未改。
源集中的四个被删除符号及旧 AppStats 包引用均已归零。

## 回退前后代码测试

永久测试：[StatsVMPersistenceTest.kt](../../../../composeApp/src/jvmTest/kotlin/me/rerere/rikkahub/ui/pages/stats/StatsVMPersistenceTest.kt)。
使用真实 ViewModel 生命周期、Room 和 BundledSQLite；设置使用真实 SettingsStore 配合内存 Preferences DataStore。
DAO 委托真实数据库，只在夹具中记录调用顺序并在指定查询时更新设置。

先在包装仍存在时运行 7 项测试，全部通过，再归位生产代码；测试只变更导入和 VM 构造夹具。
从 `class StatsVMPersistenceTest {` 到 `private fun fixture(` 之前的测试体、输入与断言逐字相同，
该段 SHA-256：`507ae5a3a3f95bf4a32da6cee6c5244ee30d5c0ae93ffd1d7abfe72e2528ee77`。

| 步骤 / 输入 | 预期结果 | 回退前 / 后 |
|---|---|---|
| 空库，推进虚拟时间 49ms，再推进 1ms | 前 49ms 保持 loading 且无查询；50ms 后返回零统计及设置中的启动次数 37 | 均通过 |
| 多助手、置顶、空会话、空节点、所有角色、已选和未选分支、缺失 usage、64 位 JSON token | 会话 3、消息 6；prompt 4,000,000,025、completion 26、cached 9；热力图只计用户消息 | 均通过 |
| 一周七天、闰日和跨年；52 周前周日的前一天/当天、未来日期和非法日期 | 与原 java.time 周日算法一致；保留边界/未来数据，跳过无法解析日期；总消息仍包含全部 7 条 | 均通过 |
| 同一瞬间在 UTC 与洛杉矶跨周日 | 按本地日期选择起点，不重新解释持久化 LocalDateTime 的日期部分 | 均通过 |
| 记录每日/会话/token 查询顺序，在 token 查询时把启动次数改为 99 | 查询顺序保持，最后读到最新的启动次数 99 | 均通过 |
| 首次加载后改变数据库与设置，再创建新 VM | 旧 VM 保持快照；新 VM 读取新消息和启动次数 100 | 均通过 |
| 在初始延迟完成前清除 ViewModelStore | 协程取消、没有数据库查询，不伪造已加载状态 | 均通过 |

64 位样本是持久化原始 JSON 的 SQL 聚合契约；没有构造超过当前 Int 范围的 UIMessage TokenUsage，
也不将该样本宣称为完整消息反序列化验证。日期比较使用原 Java 算法作独立预期，未复制新 Kotlin 算法当断言。

回退前命令：

```sh
./gradlew :composeApp:jvmTest --tests me.rerere.rikkahub.ui.pages.stats.StatsVMPersistenceTest --console=plain
```

退出 0，`BUILD SUCCESSFUL in 4s`；7 tests，0 failures/errors/skipped。
该次 XML 时间为 UTC `2026-09-09T17:53:09.918Z`。

回退后命令：

```sh
./gradlew :composeApp:jvmTest :composeApp:compileCommonMainKotlinMetadata \
  :composeApp:compileKotlinIosArm64 :composeApp:linkDebugFrameworkIosSimulatorArm64 \
  :app:assembleDebug :desktopApp:createDistributable --console=plain

xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO
```

| 检查 | 实际结果 |
|---|---|
| Gradle | 退出 0，1m29s；459 tasks，68 executed |
| composeApp 全部 JVM 测试 | 30 类、205 tests，0 failures/errors/skipped |
| 本项 VM 测试 | 7 tests 全部通过；XML UTC `2026-09-09T17:55:50.930Z` |
| common metadata / Android / JVM / iOS Arm64 / iOS Simulator Arm64 | 编译通过，模拟器 framework 链接通过 |
| Android APK / Desktop 分发包 | 构建通过 |
| iOS 模拟器应用 | xcodebuild 退出 0，`BUILD SUCCEEDED` |

未因 GUI 再次改变生产代码或增加临时日志，测试与 GUI 使用同一份改动。

## GUI 决策、产物与步骤

**需要三端 GUI**：VM 的构造与两个 Koin 模块改动，必须核对平台实际入口、生命周期和统计显示。
JVM 测试不替代 iOS/Android/桌面的真实应用加载。无需真实模型请求或测试密钥。

| 平台产物 | SHA-256 |
|---|---|
| `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` | `942d57d609d4d5a62e0d40abfcf9da0af70a648abf1375e8fc6cc62263002244` |
| `desktopApp/build/compose/binaries/main/app/RikkaHub.app/Contents/app/composeApp-jvm-8fb8b37f79e5f076c0c9fc5eb77485ea.jar` | `efaac0c35cf62209c8f3efe53da996b96ef2698820cb5cecb36c7f4da648dbc6` |
| Xcode Debug-iphonesimulator `RikkaHub.app/RikkaHub.debug.dylib` | `12de83d310cd23dc390a22b97de3d9b4db8a2aa2c309215fd0c24e6199fda479` |

iOS 包路径为 `/Users/liuzhenhui/Library/Developer/Xcode/DerivedData/iosApp-fpbfsgalslastofwurqoaqnyqkdb/Build/Products/Debug-iphonesimulator/RikkaHub.app`。
源为 `c1b49204` 加本项待提交代码；构建包含用户原有两个 Xcode 工程/方案本地差异，本项未改动或提交它们。

三个 agent 均新建并显式指定模型。父 agent 在任何 GUI 前只读取各任务的 `turn_context` 配置，
确认以下实际模型/effort 后才释放各自开始标记，没有复用可能恢复成默认模型的旧 agent：

| 平台 | agent ID | 实际模型 / effort |
|---|---|---|
| Android | `01a08758-4ac9-73d0-8b0a-ded4ae216eda` | `gpt-5.6-terra / high` |
| iOS | `01a08758-4b59-7df2-921b-27a61a25ce4e` | `gpt-5.6-terra / high` |
| Desktop | `01a08758-4beb-78e0-a378-ae02fcd5b64f` | `gpt-5.6-terra / high` |

统一验证步骤与预期：

1. 保留模拟器已有数据；桌面使用新建的独立 user.home。启动本轮构建，正常导航到统计，检查无永久 loading 或 DI 异常。
2. 停止应用后离线加入 3 条专用会话、5 个节点、6 条消息，包含一个未选分支。
   预期相对基线：会话 +3、消息 +6、输入 token +124、输出 +74、缓存 +28。
   最近两个过去日期的用户消息分别为 2 和 1，热力图对应两格，按原分位数算法呈现不同深浅。
3. 从 GUI 核对各卡片、热力图及持久化设置对应的启动次数；离线 SQL 仅用于基线和辅助核对。
4. 离开后重新进入统计，再完整退出/冷启动进入，预期统计仍一致；Android 原启动计数增加，iOS/桌面沿用原行为。
5. 停止应用，只删除专用 ID；断言无关行指纹不变、全部聚合回到基线、SQLite 完整性和外键正常。
   清理各端临时副本/配置，保留不含账号或凭据的关键截图。

三端固定夹具的两个日期为 2026-09-07（1 条用户消息）和 2026-09-08（2 条用户消息）。
会话 A 有一条用户消息和同一节点内两条助手分支，usage 分别为 `(11, 7, 3)`、`(13, 17, 5)`，选择后一条；
会话 B 有两条用户消息和一条 usage 为 `(100, 50, 20)` 的助手消息；会话 C 为空。
括号顺序为 prompt/completion/cached。这样总数会包含未选分支，而热力图只包含三条用户消息。

GUI 实际结果：**三端通过**。父 agent 已阅读三份报告并逐张查看保留的关键截图。

| 平台 | 实际显示、往返与冷启动 | 启动次数 | 清理与记录 |
|---|---|---|---|
| Android，emulator-5554 | 会话 3、消息 6、输入 124、输出 74、缓存 28；两格热力图深浅不同，重进/冷启动保持 | 基线 5、种子后 6、冷启动 7（与 preferences 一致）、清理后确认 8 | helper 清理通过，GUI 零统计/空热力图/缓存卡片消失；[完整记录](gui-android-verification.md) |
| iOS，iPhone 17 Pro | 同上，页面往返与停止后重新启动保持 | 始终 0，与 preferences 一致 | helper 清理通过，GUI 零统计/缓存卡片消失；[完整记录](gui-ios-validation.md) |
| Desktop，独立 user.home | 同上；正常退出旧 PID 后用新 PID 冷启动，结果保持 | 始终 0，与 preferences 一致 | helper 清理通过，GUI 零统计/缓存卡片消失，独立 profile 删除；[完整记录](gui-desktop-verification.md) |

三端最终实际 `turn_context` 均只有原始一轮，模型保持 `gpt-5.6-terra / high`，没有恢复旧 agent 或执行模型偏差。
三个测试基线均无会话/消息；因此本轮 GUI 的“无关行未变”指纹验证没有非空对照数据，
多助手、多会话和未选分支的 SQL 口径由上述真实 Room 测试补充。
Android/iOS 卡片值由截图和运行时可访问树核对；Desktop 下方缓存/启动次数卡片经滚动和可访问树核对，
保留截图主要展示热力图及上方四项统计。单个热力图格没有可访问日期名称，GUI 确认格位和深浅，
日期边界仍以固定时区/原 Java 算法的代码测试为证据，不声称截图逐格覆盖全部 53 周。

## 清理与覆盖边界

临时离线夹具在应用停止后操作，不替代 GUI 统计显示。三端专用种子和临时副本已清理，
本项真实 Room 测试、验证文档和关键 GUI 截图永久保留；共享 helper、模型开始标记、构建记录、
前后测试夹具临时副本与 Gradle/Xcode 输出已统一删除。三个 GUI agent 均已完成并关闭。
提交前再次核对三个构建产物 SHA-256 及用户两处 Xcode 文件指纹，均未变化；两处用户改动不纳入提交。
没有使用真实模型服务、钥匙串凭据或增加生产临时日志。
代码验证不覆盖真实模型计费、消息生成、应用其他页面或所有系统生命周期场景。

下一项建议：删除 `RikkaHubApp` 的 Status/Capabilities 演示外壳，先核对全部正式调用点，再验证受影响的平台冷启动与导航。
