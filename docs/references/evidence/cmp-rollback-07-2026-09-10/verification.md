# 第 07 项回退验证：删除 RikkaHubApp 演示外壳

日期：2026-09-10（Asia/Shanghai）。起点：`2466dfa8`。审计范围：X01；结构基线为 tag `2.4.5`。

## 判断与最小改动

tag 中同名 `RikkaHubApp.kt` 是 Android `Application`；共享文件新增的是 Compose 演示入口，二者职责不同。
本项撤销已获用户授权的额外脚手架（`ARCHITECTURAL_OPTIMIZATION`），直接恢复原内容调用，
没有新增依赖、平台抽象或业务分层。

修改 4 个生产文件，增加 7 行、删除 180 行，净减少 **173 行**：

| 文件 | 改动 |
|---|---|
| `composeApp/.../shared/RikkaHubApp.kt` | 删除整个 160 行文件：演示根函数、Status/Capabilities 页面、BootstrapDestination、SharedNavigationPresentation 及选择函数 |
| `composeApp/.../shared/PlatformCapabilities.kt` | 仅删除演示专用 SharedEntryTestTags 及注释，共 8 行；平台能力实现原样保留 |
| `app/.../RouteActivity.kt` | 去掉共享根函数导入和外层调用，直接执行原 AppRoutes() |
| `composeApp/.../shared/SharedProductApp.kt` | 去掉外层调用，原 ProductNavigationHost 参数与位置保持，只减少一层缩进 |

全仓库源码搜索确认只有上述两个正式调用点，均传入非空 `productContent`。原包装先调用内容并立即返回，
不会执行后面的演示 MaterialTheme、Scaffold、导航或 rememberSaveable 状态；没有需要迁回的演示业务。
专用标签只有演示页使用，现有测试、脚本和平台入口均没有消费者。

保留 Android 原 `RikkaHubApp : Application`、AndroidManifest、`RikkahubTheme`、Koin 配置与生命周期效果、
ProductNavigationHost、ViewModel/state 保存、路由参数、平台宿主和真正的能力开关。
`capabilityMatrix` 仍供 Desktop headless smoke 使用，`hasCapability`/平台 expect/actual 仍服务产品，因此未删除。
本项不把 headless 能力检查当作实际 GUI 启动证据。

## 源码等价检查

已执行逐文件完整文本比较，白名单仅是删除外壳导入、两个外层调用和专用标签；不是只比较几个关键字。
以下脚本可在仓库根目录复核：

```sh
python3 - <<'PY'
from pathlib import Path
import subprocess

base = '2466dfa8'
def before(path):
    return subprocess.check_output(['git', 'show', f'{base}:{path}'], text=True)

p = 'app/src/main/java/me/rerere/rikkahub/RouteActivity.kt'
expected = before(p).replace('import me.rerere.rikkahub.shared.RikkaHubApp\n', '')
expected = expected.replace(
    '                RikkaHubApp {\n                    AppRoutes()\n                }',
    '                AppRoutes()',
)
assert Path(p).read_text() == expected

p = 'composeApp/src/commonMain/kotlin/me/rerere/rikkahub/shared/SharedProductApp.kt'
old = '''            RikkaHubApp {
                ProductNavigationHost(
                    startScreen = initialScreen,
                    ttsState = ttsState,
                    platformRoutes = platformRoutes,
                    richTextPlatformActions = richTextPlatformActions,
                )
            }'''
direct = '\n'.join(line[4:] for line in old.splitlines()[1:-1])
assert Path(p).read_text() == before(p).replace(old, direct)

p = 'composeApp/src/commonMain/kotlin/me/rerere/rikkahub/shared/PlatformCapabilities.kt'
marker = '/** Stable semantics contract used by all three shell smoke tests. */'
assert Path(p).read_text() == before(p).split(marker)[0].rstrip() + '\n'

p = 'app/src/main/java/me/rerere/rikkahub/RikkaHubApp.kt'
assert Path(p).read_text() == before(p)
print('Wrapper-only changes and retained Android Application: PASS')
PY
```

源集中共享 `RikkaHubApp`、两组演示枚举/函数、SharedEntryTestTags 及其语义标签字符串的引用归零。
检查本次桌面 JAR，演示类已移除、`SharedProductAppKt.class` 保留。
没有修改任何永久测试；没有为已删除的演示 UI 新增包装测试或引入 UI 测试依赖。

## 代码测试和构建

回退前：

```sh
./gradlew :composeApp:jvmTest :app:testDebugUnitTest --console=plain
```

退出 0，`BUILD SUCCESSFUL in 4s`；314 tasks，22 executed。现有测试 205 + 63 项全部通过。

回退后：

```sh
./gradlew :composeApp:jvmTest :app:testDebugUnitTest \
  :composeApp:compileCommonMainKotlinMetadata :composeApp:compileKotlinIosArm64 \
  :composeApp:linkDebugFrameworkIosSimulatorArm64 :app:assembleDebug \
  :desktopApp:createDistributable --console=plain

xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO
```

| 检查 | 预期与实际 |
|---|---|
| composeApp JVM | 30 类、205 tests；回退前后均 0 failures/errors/skipped |
| Android app JVM 单元测试 | 10 类、63 tests；回退前后均 0 failures/errors/skipped |
| Kotlin common / JVM / Android / iOS Arm64 / iOS Simulator Arm64 | 全部编译通过，模拟器 framework 链接通过 |
| Android APK / Desktop 分发包 | 构建通过；Gradle 退出 0，1m27s，477 tasks、72 executed |
| iOS 模拟器应用 | xcodebuild 退出 0，BUILD SUCCEEDED |

回退后的测试 XML UTC 范围：composeApp `2026-09-09T18:28:55.395Z` 至 `18:28:57.046Z`，
app `18:28:56.845Z` 至 `18:28:57.278Z`。268 项为现有业务回归测试，不声称它们直接验证了三端窗口、导航或输入框。

## GUI 决策与可复核产物

**需要三端 GUI**：删除根 Composition 的调用层级，必须检查正式宿主启动、主题、导航返回与 ViewModel 输入状态。
用户授权的 DeepSeek 密钥无需使用；本项只输入未发送的本地草稿，不创建消息或请求模型。

| 产物 | SHA-256 |
|---|---|
| `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` | `f975d92fbf24506356ffa652cf674c97c5e3b0f1ae518b79623890a2d15a8d71` |
| Desktop 包内 `Contents/app/composeApp-jvm-85116b8babcbf7e36033f9f4b86f233f.jar` | `e90eb04991139a4a8f7b54aa10d71931c4c60473edd7ec400cb8f67f76ee3a20` |
| iOS 包内 `RikkaHub.debug.dylib` | `2d43f3dc086e7d2daea8e2f8e4afb024e5aa18a991def48279c33a6564d71526` |

Desktop 原始包为 `desktopApp/build/compose/binaries/main/app/RikkaHub.app`。
iOS 原始包为 `/Users/liuzhenhui/Library/Developer/Xcode/DerivedData/iosApp-fpbfsgalslastofwurqoaqnyqkdb/Build/Products/Debug-iphonesimulator/RikkaHub.app`。
构建来源为 `2466dfa8` 加本项四个生产文件差异；用户原有两个 Xcode 工程/方案差异保持，不纳入本项提交。

各 agent 均新建，父 agent 在开始 GUI 前核对实际 `turn_context`，确认模型和产物后才释放开始标记：

| 平台 | agent ID | 实际模型 / effort |
|---|---|---|
| Android 初次连接（未执行 GUI） | `01a08770-afd0-7d43-8485-83081528f629` | `gpt-5.6-terra / high` |
| Android 实际重试 | `01a08775-b68b-7a43-801d-50c1722f2da9` | `gpt-5.6-terra / high` |
| iOS | `01a08770-b042-77f0-b970-e49c4e502f1c` | `gpt-5.6-terra / high` |
| Desktop | `01a08770-b0be-7041-aeb5-2bfa56934644` | `gpt-5.6-terra / high` |

初次 Android 任务因 ADB 无连接设备而未执行 GUI；该次不计通过。父 agent 随后启动既有 `Pixel_10_Pro_XL`，
使用端口 5554、`-no-window -no-snapshot-save` 保留原用户数据，通过 ADB 观察 Android 实际画面，
不占用 Mac 前台输入。快照因渲染器不同而未加载，虚拟机正常冷启动，`sys.boot_completed=1` 后由新的 Terra agent 重试。
没有恢复可能变更默认模型的已结束 agent；测试结束后由父 agent 关闭本轮启动的模拟器。

统一步骤与预期：

1. 正常冷启动：进入正式聊天/产品界面，主题与原布局正常，无 Status/Capabilities 演示栏、空白或 DI 失败。
2. 通过实际菜单进入统计，返回聊天；进入设置及一个无副作用子页面，逐层返回。预期真实导航和返回层级正确。
3. 在空输入框输入 `CMP07-平台名`，不发送；进入统计后返回同一聊天，预期原 ViewModel 中的草稿保留。
   只清除测试标记，不覆盖用户草稿。草稿属于内存状态，本项不要求它在进程冷启动后持久化。
4. 完全停止自己的实例，再用同一 profile 冷启动；正式入口与导航继续正常，未发送草稿不增加消息或会话统计。
5. 停止测试实例并删除临时截图/工具/专用桌面 profile，保留模拟器已有数据和设置。

实际结果：**三端通过**。父 agent 已逐张查看全部 11 张保留截图，并核对各端报告。

| 平台 | 实际覆盖与结果 | 证据 |
|---|---|---|
| Android | 正式聊天及主题正常；Statistics 往返、Assistant Settings → Default Assistant → Basic Settings 的逐层返回正确；未发送的 `CMP07-android` 草稿在统计往返后保留，清除后同 profile 冷启动输入为空；抽屉无会话，统计会话/消息/输入/输出均为 0 | [Android 报告及 4 张截图](gui-android-verification.md) |
| iOS | 正式聊天正常；统计、设置 → 偏好设置 → 界面偏好设置及返回正确；`CMP07-ios` 草稿在统计往返后保留并清空；停止后同 profile 冷启动正常，统计会话/消息均为 0 | [iOS 报告及 3 张截图](gui-ios-validation.md) |
| Desktop | 原始分发包正常显示正式聊天；统计、设置 → 偏好设置及返回正确；`CMP07-desktop` 草稿在统计往返后保留并清空；同一独立 profile 完整冷启动正常，统计会话/消息均为 0；额外干净 profile 冷启动确认抽屉可开关 | [Desktop 报告及 4 张截图](gui-desktop-verification.md) |

Android 的首次过早返回发生在统计转场尚未结束时，该帧未计通过；等待转场后通过可见返回按钮完成有效往返。
本项 Android 实际检查的是助手设置层级，不将其扩大为全部全局设置覆盖。Android 应用启动计数按原逻辑增长，
保留的统计截图为 10 → 11；没有为还原计数写数据库。

完成时再次检查 4 个 agent 的全部实际 `turn_context`，均仍为 `gpt-5.6-terra / high`；
再次核对三个产物的 SHA-256 与表中一致，两个用户原有 Xcode 文件的指纹保持。

## 清理与覆盖范围

没有添加生产临时日志、演示替代页面或调试开关。三端测试草稿均已清空，测试应用实例全部停止；
Android 设备端临时 XML/PNG、各端未保留截图、Android 临时目录和 Desktop 独立 profile 均已清理。
父 agent 已关闭本轮启动的 `emulator-5554`，模拟器进程退出 0；未关闭用户的 iOS 模拟器或删除原数据。
XcodeBuildMCP 没有删除 named profile 的 API，iOS agent 已清空 `cmp-rollback-07-ios` 的全部默认值并切回全局默认 profile。
父 agent 已删除本项构建/模拟器日志、基线统计 JSON、临时工具目录及模型开始标记；所有验证 agent 已结束。
永久保留源码改动、路线图、验证记录与 11 张关键 GUI 截图。完成代码构建后没有再修改生产代码。
本项不覆盖真实模型服务、分享/深链、通知、所有配置重建场景或所有平台能力；这些生产逻辑和接线未改动。

## 提交状态

验证与清理均已完成，仅暂存本项 4 个生产文件及文档/截图，用户原有两个 Xcode 改动未暂存。
首次执行 `git commit -m 'refactor(cmp): 移除共享入口演示外壳'` 时，现有 1Password SSH 签名程序
`/Applications/1Password.app/Contents/MacOS/op-ssh-sign` 返回 `failed to fill whole buffer`，Git 退出 128，
未创建提交。用户随后确认已解锁，本次沿用原签名设置重试，没有绕过签名设置。

下一项建议：收敛 `BackupRepository` / `BackupSettingsGateway`，把原方法与设置访问归回 `BackupVM`，
保留 WebDAV/S3 及本地文件的实际平台能力；先验证参数、列表排序、备份完成时间和失败传播。
