# 第 02 项回退验证：FolderPersistenceMapper 与 DAO 回调

验证日期：2026-09-09；收尾日期：2026-09-10。起点：`c64962ed`。原始行为基线：tag `2.4.5`。

## 改动及等价依据

生产改动为三个文件，合计增加 28 行、删除 40 行，净减少 12 行：

- `FolderRepository.kt`：原 `createFolder` 方法体和文件级私有 `FolderEntity.toFolder` / `Folder.toEntity`
  扩展归位，恢复直接 `ConversationDAO` 依赖，删除 `FolderPersistenceMapper`、mapper 属性及额外 Clock 构造参数。
- Android `RepositoryModule.kt`：对应的 Koin 绑定恢复 `FolderRepository(get(), get())`。
- `SharedProductModule.kt`：对应绑定直接注入 `folderDAO` / `conversationDAO`。

FolderRepository **整个文件**与 tag 做精确文本比较，只允许如下替换，结果一致：

```sh
python3 - <<'PY'
from pathlib import Path
import subprocess

path = Path('composeApp/src/commonMain/kotlin/me/rerere/rikkahub/data/repository/FolderRepository.kt')
baseline = subprocess.check_output([
    'git', 'show', '2.4.5:app/src/main/java/me/rerere/rikkahub/data/repository/FolderRepository.kt',
], text=True)
expected = baseline.replace('import java.time.Instant', 'import kotlin.time.Clock\nimport kotlin.time.Instant')
expected = expected.replace('Instant.now()', 'Clock.System.now()')
expected = expected.replace('Instant.ofEpochMilli(', 'Instant.fromEpochMilliseconds(')
expected = expected.replace('.toEpochMilli()', '.toEpochMilliseconds()')
assert path.read_text() == expected
print('Full FolderRepository matches 2.4.5 after time API substitutions: PASS')
PY
```

没有改变 DAO SQL/schema、文件夹实体、VM 的输入处理或界面交互，没有给删除的两次 DAO 调用添加外层事务。
生产和测试源集内 `FolderPersistenceMapper` / `clearConversationFolder` 引用为 0。

## 代码测试

新增 `FolderRepositoryPersistenceTest`，直接使用原 Repository 入口、真实 Room 内存数据库与
BundledSQLiteDriver。失败测试通过内存库 trigger 产生特定错误；无生产测试接口、临时日志或测试开关。

先在未回退生产代码时运行：

```sh
./gradlew :composeApp:jvmTest \
  --tests me.rerere.rikkahub.data.repository.FolderRepositoryPersistenceTest \
  --console=plain
```

结果：退出 0，`BUILD SUCCESSFUL in 4s`，7 tests，0 failures/errors/skipped。
回退后只调整测试夹具的构造参数以匹配原 DAO 签名；场景输入及行为断言保持相同。

| 输入 / 操作 | 预期 | 回退前 | 回退后 |
|---|---|---|---|
| 创建 Unicode/换行、空串、空格及同名文件夹，重读 | 不新增 Repository 校验；原名、独立 ID、默认排序和毫秒持久化保持 | 通过 | 通过 |
| 直接写入旧字段格式、不同 sortIndex/createAt/assistantId | 字段恢复、助手隔离、sortIndex 后按 createAt 排序；缺失 ID 返回 null | 通过 | 通过 |
| 重命名、空名称及不存在的 ID | 只改指定名称，其他元数据和其他文件夹不变，不插入新记录 | 通过 | 通过 |
| 删除含两个完整会话的文件夹，同时保留其他文件夹/会话 | 会话与分支/元数据不丢失，归属变空，目标文件夹消失，无关数据保持 | 通过 | 通过 |
| 删除缺失但仍有引用的文件夹，重复删除 | 仍先清空旧引用，保留原无存在性前置判断的行为 | 通过 | 通过 |
| trigger 拒绝清空会话归属 | 对应异常向外传播，文件夹/归属保持，不继续删除 | 通过 | 通过 |
| trigger 拒绝删除文件夹 | 对应异常向外传播，文件夹保留，已经写入的清空归属结果保留 | 通过 | 通过 |

后两项明确验证原有执行顺序和两次独立写入的失败行为；测试预期不用于推动额外事务、锁或异常保护。

## 回归与构建

```sh
./gradlew :composeApp:jvmTest \
  :composeApp:compileCommonMainKotlinMetadata \
  :composeApp:compileKotlinIosArm64 \
  :composeApp:linkDebugFrameworkIosSimulatorArm64 \
  :app:assembleDebug :desktopApp:createDistributable --console=plain
```

退出 0，`BUILD SUCCESSFUL in 1m 36s`。

| 项目 | 结果 / 范围 |
|---|---|
| composeApp:jvmTest | 28 类、187 tests、0 failures/errors/skipped |
| FolderRepositoryPersistenceTest | 7 tests、0 failures/errors/skipped；XML 时间戳 `2026-09-09T14:25:53.149Z` |
| common / JVM / Android / iOS 生产代码 | 对应共同代码、目标编译通过；iOS 模拟器 Debug framework 链接通过 |
| Android GUI 包 | `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` 等 ABI 包及 universal 包已生成 |
| Desktop GUI 包 | `desktopApp/build/compose/binaries/main/app/RikkaHub.app` 已生成 |

iOS 应用额外运行：

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO
```

退出 0，`BUILD SUCCEEDED`。应用路径：
`/Users/liuzhenhui/Library/Developer/Xcode/DerivedData/iosApp-fpbfsgalslastofwurqoaqnyqkdb/Build/Products/Debug-iphonesimulator/RikkaHub.app`。
使用当前工作区已有 Xcode 配置构建，本项没有编辑或暂存用户既有的两项工程/方案改动。
代码测试的真实数据库运行目标为 JVM；Android/iOS 的实际数据库与 UI 接线由下面的 GUI 验证覆盖。

本轮构建文件 SHA-256（用于核对 GUI 所用产物）：

| 文件 | SHA-256 |
|---|---|
| `app-arm64-v8a-debug.apk` | `25f08ea44d9bb27f132f61717aceab38e738bd3b6f637b5e03c13ffa8248c371` |
| 桌面包内 `composeApp-jvm-3a7e8dfb71575f33b1c8af6e76c17d7.jar` | `12e5c6107cd7acf7bde5ca468ff128e8529bb34d5ba31a12cdc848c3a85870d7` |
| iOS 包内 `RikkaHub.debug.dylib` | `d86c319fbdd60fc0ef9044110d001e0437d454a2794c3956fc93c883a99ef526` |

## GUI 验证

本项需要 GUI：构造参数和两个平台 Koin module 的绑定改变，需要验证实际应用能解析依赖且页面操作持久化正常。
Android 由 `Maxwell`（`01a08698-d534-7e73-b29f-44546508f37f`），iOS 由 `Pauli`
（`01a0868b-5f36-7c31-8ae7-f158193c24e1`），桌面由 `Tesla`
（`01a0869e-d85a-73b1-aac9-7d14b94d4545`）验证。
范围：Android 模拟器、iOS 模拟器、桌面，使用上述本轮构建。

模型记录：原始委派均指定 `gpt-5.6-terra`。父 agent 复核本地执行上下文后确认，Android/iOS 各轮均为
`gpt-5.6-terra / high`；桌面锁屏诊断阶段为 `gpt-5.6-terra / medium`，但恢复后的桌面补验两轮实际为
`gpt-6-astra / max`（turn `01a086d2-b9fb-7122-93ae-5b9f8a90f583` 和
`01a086df-6f16-7d92-8627-e7f5c1c1b8ac`）。恢复时没有及时核对实际模型，桌面补验未遵守用户的低成本模型要求，
不能标为 Terra 执行。后续启动/恢复均须先核对实际配置；恢复不能保持指定模型时，显式创建 Terra 子 agent。

预期：创建专用测试文件夹 → 重命名 → 将专用测试会话移入 → 删除文件夹 → 会话完整返回未归类，其他文件夹
与会话不受影响 → 重进页面/重启后仍符合预期。仅测试数据，不需要模型请求，不读取钥匙串。

实际结果：

| 平台 | 操作结果与实际覆盖范围 |
|---|---|
| Android 模拟器 | 创建、精确重命名、目标/对照会话移动、删除后目标会话返回未归类、对照数据保持、冷启动持久化通过；实际 Android DI 链路通过。见 [操作与截图](gui-android-verification.md)。 |
| iOS 模拟器 | 创建、重命名回调、移动、删除后空会话保留并返回未归类、删除前后重启持久化通过；实际 shared DI 页面链路通过。精确名称的纯 GUI 输入重试受工具限制，目标测试记录曾在停止应用后修正；未用该 GUI 用例验证正文/分支及对照数据。见 [操作、限制与截图](gui-ios-validation.md)。 |
| 桌面 | **功能验证通过**。用户解锁后，使用隔离 profile 经 GUI 创建、精确重命名、移动两条空会话、删除目标文件夹；目标会话回到未归类，Keep 对照文件夹/会话保持，冷启动后仍符合预期。停机后辅助核对两条会话全部 13 个字段与对照文件夹 5 个字段，完整性检查通过。见 [操作、截图及模型偏差](gui-desktop-verification.md)。 |

正文/分支及元数据保持由真实 Room JVM 测试验证，不将模拟器中空会话的 GUI 观察扩大为正文验证。
第二项代码与实际平台接线验证已完成；iOS 精确名称输入重试的覆盖限制和桌面执行模型偏差按上述范围保留记录。

## 提交前清理

Android 和 iOS 专用文件夹/会话已清理，回读计数为 0；Android 数据库完整性检查为 `ok`。
桌面验证者自己的 TTY 进程已停止，隔离 profile 和废弃截图已移除。必要截图和验证摘要保存于本目录。
生产代码没有添加临时日志、测试开关或凭据；本轮没有模型请求，也没有读取钥匙串。
准备阶段的另一个桌面 profile 经确认没有关联进程后也已移除，未关闭其他来源的应用进程。
本轮三份临时 Gradle/Xcode 日志在提取构建结果后已删除；iOS 两张证据图原样归档后移除了临时副本。
已再次核对 tag 文件等价性、JUnit XML 的 187 项结果和 `git diff --check`，均符合上述记录。

用户解锁后的桌面补验已完成，三个测试实例正常退出，隔离 profile、临时 GUI 工具、种子与日志均已移除。
最终 diff 和 `git diff --check` 已复查通过，必要截图已查看并与验证步骤核对。
Git 暂存只包含本项生产改动、永久回归测试和本项验证记录，不包含用户原有的两项 Xcode 配置变更。
