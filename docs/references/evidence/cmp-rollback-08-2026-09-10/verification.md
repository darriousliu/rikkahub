# 第 08 项回退验证：备份转发与设置访问归位

日期：2026-09-10（Asia/Shanghai）。起点：`cbb224b4`；结构基线 tag `2.4.5`。审计 E06。

## 判断与改动范围

本项撤销用户已授权的额外业务分层；直接使用原 SettingsStore、原 VM 和现有平台能力。

| 生产文件 | 改动 |
|---|---|
| `composeApp/.../data/repository/BackupRepository.kt` | 删除 82 行文件及 Repository、Settings Gateway、Gateway 实现三个类型 |
| `composeApp/.../ui/pages/backup/BackupVM.kt` | 直接依赖 SettingsStore 和现有 WebDAV/S3 transport，列表/操作归位，恢复私有 recordBackupTime |
| `composeApp/.../data/sync/BackupArchiveService.kt` | Gateway 的设置读取/更新替换为 SettingsStore；ZIP 方法体原样 |
| `composeApp/.../data/repository/FileKitBackupLocalFileService.kt` | 直接访问 SettingsStore，导出完成时间在原位置原样内联 |
| `app/.../data/repository/AndroidBackupLocalFileService.kt` | 同上；原 IO 范围、try/catch、临时文件生命周期保持 |
| `app/.../di/RepositoryModule.kt` | 删除 Repository/Gateway 注册；保留真实 transport 与本地文件实现 |
| `app/.../di/ViewModelModule.kt`、`composeApp/.../shared/SharedProductModule.kt` | 删除共享包装注册，以原 SettingsStore/transport/本地文件服务直接构造 VM |

共 8 个生产文件，增加 78 行、删除 132 行，净减少 **54 行**。没有新增生产接口、依赖、锁、校验或保护分支。
测试增加一个 `BackupVMContractTest.kt`，使用现有依赖。

必要边界继续保留：WebDAV/S3 的 Ktor/平台 engine、现有 transport、Android Context/缓存文件处理、FileKit 与 ZIP 实现。
本项没有改动 UI、网络请求或归档格式，也没有提前承担第 15/16 项的本地文件业务和双份同步逻辑合并。

本地导出与网络备份原先共用 Repository 的时间记录方法。删除 Repository 后，网络记录归回原 VM；
本地导出暂时将同一段更新表达式内联到两个原调用位置，而不是增加新的业务 util 或回调接口。
原因是现有 Android 在 IO 内记录并在失败时删除已生成临时文件，FileKit 则保留已生成归档；
直接把这一步提前迁出文件服务会改变异常清理或调度。本项保留这些差异，第 15 项连同完整导出流程处理。
标准 `Clock` 从原 Repository 移到实际持有时间更新的方法所属类，默认仍为 `Clock.System`。

## 源码比较

- 已与 tag 同名 `BackupVM.kt` 比较：整个 `backup`、`backupToS3` 和 `recordBackupTime` 方法体，仅忽略格式和
  `System.currentTimeMillis()` → `clock.now().toEpochMilliseconds()` 后完全一致。
- 已逐文件完整文本比较两端本地文件服务：允许差异仅为直接设置访问、Clock 参数/导入，以及原时间更新方法体的内联；
  导入器、设置快照读取位置、文件读写、异常和清理语句无其他变化。
- 已逐文件完整文本比较 BackupArchiveService：只有 Gateway 类型/导入及两处 settings 访问替换。
- 源码中三个已删除类型引用归零。现有 SettingsStore、WebDAV/S3 实现、BackupPage 与各 Tab 没有修改。
- 14 个测试方法的输入与断言回退前后文本一致；只改测试夹具的构造接线。

本项保留已迁移 VM 的 StateFlow 接线、runCatching/fold、Int 返回签名和本地文件委托，不声称整个文件与 tag 完全相同。
例如列表捕获取消后呈现 UiState.Error、SettingsStore 先更新内存再写存储等既有行为均未顺带调整。

## 代码验证步骤、预期与结果

回退前运行：

```sh
./gradlew :composeApp:jvmTest --tests '*BackupVMContractTest' --console=plain
```

退出 0，`BUILD SUCCESSFUL in 5s`，172 tasks、19 executed。
14 tests，0 failures/errors/skipped；XML UTC 时间 `2026-09-09T19:03:40.014Z`。

| 场景 / 步骤 | 预期 | 前 / 后 |
|---|---|---|
| 阻塞两种初始列表请求后释放 | Idle → Loading → Success，各请求一次；WebDAV 按时间稳定降序，S3 不由 VM 再排序 | 通过 / 通过 |
| 列表抛异常/取消，再主动重载 | 同一个异常进入 Error；重载可恢复成功，无自动重试 | 通过 / 通过 |
| VM 更新设置，在调度前后读取；读取原 preferences key 并重新创建 Store | 保留 launch 时机；webdav_config/s3_config 原 key 和字段可重新读出 | 通过 / 通过 |
| 更新当前配置后调用测试连接、恢复、删除 | 每次读当前配置，路径/选项顺序/选中 item 和 Int 返回值原样传递 | 通过 / 通过 |
| 两种备份分别返回 true 和 false | 未抛异常就记录完成时间；不新增 false 防护 | 通过 / 通过 |
| 暂停备份，中途更新其他设置并推进固定 Clock | 开始请求用原 config，完成后读取当前设置和时钟，不覆盖中途修改 | 通过 / 通过 |
| 两种备份抛异常/取消 | 原异常直接传播，没有时间写入或自动重试 | 通过 / 通过 |
| 测试连接、恢复、删除失败 | 精确传播原 transport 异常，不改备份时间 | 通过 / 通过 |
| 并发发起两个备份 | 请求独立执行，各自完成并更新时间，不新增合并/锁 | 通过 / 通过 |
| 初始任务执行前清除 VM | 不启动列表请求，状态仍 Idle | 通过 / 通过 |
| 上传完成后设置持久化失败 | 原错误传播；保留 SettingsStore 先更新内存的现有行为 | 通过 / 通过 |
| 真实 FileKit ZIP 导出、用 JDK ZipFile 读取 settings.json，再恢复设置 | 归档含更新前的备份时间，返回前才记录完成；原配置可以恢复 | 通过 / 通过 |
| 归档目标是普通文件而非目录 | 创建失败，不记录备份时间 | 通过 / 通过 |
| FileKit 归档成功、设置写入失败 | 错误传播，保留原已生成归档行为 | 通过 / 通过 |

回退后运行：

```sh
./gradlew :composeApp:jvmTest :app:testDebugUnitTest \
  :composeApp:compileCommonMainKotlinMetadata :composeApp:compileKotlinIosArm64 \
  :composeApp:linkDebugFrameworkIosSimulatorArm64 :app:assembleDebug \
  :desktopApp:createDistributable --console=plain

xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO
```

- Gradle 退出 0，`BUILD SUCCESSFUL in 1m 27s`；477 tasks、70 executed。
- composeApp JVM：31 类、219 tests；Android app JVM：10 类、63 tests；共 282 项，全部无失败/错误/跳过。
- common / JVM / Android / iOS Arm64 / iOS Simulator Arm64 编译、模拟器 framework 链接、APK、桌面分发包通过。
- Xcode 退出 0，`BUILD SUCCEEDED`。

SettingsStore 测试使用真实序列化和内存 Preferences DataStore，重新构造 Store 验证原 key/值；不是磁盘重启测试。
ZIP 使用真实 JVM 文件/归档实现及独立 JDK reader；Android ContentResolver/系统文件选择器未被这组 JVM 测试执行。
实际持久化与 Koin 接线的 GUI 补充情况如下；各平台边界单独记录。

## GUI 判断、步骤和产物

**需要三端 GUI**：BackupVM 构造和设置访问接线发生变化，不能只凭单元测试声称实际备份页可用。
使用当前应用真实页面和平台客户端，请求本机只读固定 WebDAV/S3 响应。服务仅提供列表、不提供可恢复的 ZIP，PUT/DELETE/MKCOL 返回 405，
不验证真实云端认证/签名；只确认界面 → VM → 当前平台 transport → 响应 → 列表这一实际链路。

统一步骤：

1. 正常冷启动，从设置进入备份，打开四个标签。预期界面可用，无 Koin/构造异常。
2. 确认原远端配置为空，准备测试地址、路径和假凭据。Desktop/Android 通过 GUI 输入；
   iOS 因插件文本输入未生效，用下述独立测试夹具准备，不将准备动作记作 GUI 输入通过。
3. WebDAV、S3 分别测试连接并打开列表。预期连接成功、恰好两项且新文件在旧文件前，大小分别 2KB / 1KB。
4. 离开备份页再进入，预期设置保留，列表可重新加载；完整停止后同 profile 冷启动，再核对持久化与列表。
5. Android 将测试字段通过 GUI 恢复原值并重新进入核对；桌面使用独立 profile，退出后删除。
   iOS 通过 GUI 改变/恢复备份选项并验证持久化，再在应用停止后按原值还原两组测试配置。
   停止自己的应用实例，清理临时文件，父 agent 最后关闭本轮服务和 Android 模拟器。

测试端为主机 loopback `18768`，Android 通过 `10.0.2.2`，iOS/Desktop 通过 `127.0.0.1`。
WebDAV URL `/dav/`、路径 `cmp08-平台名`；S3 bucket `cmp08-平台名`，region auto/pathStyle true。
假用户名/access key 为 `cmp08`，假密码/secret 为 `test-only`，不对应任何真实账号。
固定数据先返回旧后新，混入 `ignore.txt` 和 WebDAV collection；预期最终过滤排序为
`backup_cmp08_new.zip`（2026-09-10，2048 bytes）、`backup_cmp08_old.zip`（2026-09-09，1024 bytes）。

| 产物 | SHA-256 |
|---|---|
| `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` | `84138444aca63564171802656daddf0e7ffe7ca933f11ee0777a326b6ea63819` |
| Desktop `Contents/app/composeApp-jvm-bbfd681646b6cccb6274383a7821977.jar` | `511e9817ccf2210dcb7361694b827c426a7643df8340b844502ec2370d14e16c` |
| iOS `RikkaHub.debug.dylib` | `37df8a167aaba207d30a2f850e8a76a65201fdb6ca90fb5a5b08d81b9deaa5d9` |

Desktop 包：`desktopApp/build/compose/binaries/main/app/RikkaHub.app`。
iOS 包：`/Users/liuzhenhui/Library/Developer/Xcode/DerivedData/iosApp-fpbfsgalslastofwurqoaqnyqkdb/Build/Products/Debug-iphonesimulator/RikkaHub.app`。
产物来自 `cbb224b4` 加上述生产差异；两个用户原有 Xcode 工程/方案改动的指纹保持，未纳入本项。

| 平台 | agent ID | 核对的实际模型 / effort |
|---|---|---|
| Android 首轮（未通过完整 GUI） | `01a08793-16db-7891-844c-86208ececf9d` | `gpt-5.6-terra / high` |
| iOS 首轮（未通过完整 GUI） | `01a08793-174f-7d71-875d-a2da821b418d` | `gpt-5.6-terra / high` |
| Desktop | `01a08793-17c4-7bf1-b788-0e4ace6c1d61` | `gpt-5.6-terra / high` |
| Android 接手重试 | `01a087a7-7fb2-7243-b1c1-e642f54eaaff` | `gpt-5.6-terra / high` |
| iOS 接手重试 | `01a087a7-7f30-7123-948b-ffff28da9fb0` | `gpt-5.6-terra / high` |
| iOS 解锁后续验 / 用户指定 Pro Max | `01a08915-25b8-7be0-b2a9-bdedcb882c4d` | `gpt-5.6-terra / high` |

各 agent 新建并在开始前核对实际 turn_context，不凭创建参数推断；iOS 首轮在标记未出现时结束了只读准备，
继续该未关闭 agent 后再次核对实际新 turn_context，仍为 Terra/high，才执行 GUI。
前五个 agent 的十个实际 turn_context 已核对为 Terra/high；解锁后续验 agent 的五个不同 turn_context
也逐次核对为 Terra/high，包括用户改用 Pro Max 后的 `01a08938-0a2d-7472-b576-4d171ab085d5`。
重复的 compaction 上下文按 turn ID 去重，共六个 agent、十五个不同 turn。
三份构建产物的 SHA-256 与上表一致；两个用户原有 Xcode 文件的指纹仍与本项开始时一致。

Desktop 已完成两协议连接/列表和同 profile 冷启动，父 agent 已查看保留的三张列表截图。
截图覆盖列表正文的文件名、日期与大小，右侧操作按钮未完整入镜，不用于证明恢复/删除功能。
首轮将 placeholder 写成真实值的清理操作已撤回为失败尝试；该独立 profile 已删除。
补充轮次用新独立 profile 完成留证，结束后同样删除。详见 [桌面报告](gui-desktop-verification.md)。

移动端首轮未通过完整 GUI，不计通过。Android 快速输入出现字符重排，首轮点击未观察到请求；
父 agent 用设备 toybox nc 验证了 `10.0.2.2:18768` HTTP 200，只将其作为网络连通诊断，不当作 GUI 通过证据。
iOS 首轮输入法产生全角标点，替换操作追加文本、HID 删除无效，未完成连接/列表及恢复测试字段。
iOS agent 的卸载尝试被自动审批拒绝，理由为会删除整个应用数据、超出字段恢复范围；命令未执行。
原两个移动端 agent 已关闭，分别由新的 Terra agent 从当前状态接手，继续通过 GUI 恢复和验证，不卸载或清空应用数据。

Android 重试通过四个标签、两协议配置持久化、测试连接及两项列表，父 agent 已查看保留的四张截图并核对 SHA-256。
同数据冷启动在授权前完成，配置保持；在该保存配置上手动授予系统权限后，两份列表成功显示。
授权后没有再做第二次独立冷启动，不将其写成已覆盖。详见 [Android 报告](gui-android-verification.md)。
iOS 早期接手时 GUI 工具返回 Mac 已锁定；用户解锁后已恢复真实画面，但 Pro 的输入替换仍出现追加/乱码。
镜像能够显示画面并接收按键，未解决软件键盘/全选问题，不能据此记连接或列表通过。
用户随后指定 iPhone 17 Pro Max（`03C090DA-107B-4F9F-BCCD-8D5265D32820`），并要求使用 Build iOS Apps 插件；
续验 agent 已绑定该设备，使用同一构建产物进行独立 GUI 验证。
旧 Pro 只清理本轮引入的 WebDAV 字段，过程及无关设置比较见 [旧 Pro 清理记录](ios-pro-cleanup.md)。
最新 iOS 覆盖情况见 [iOS 验证记录](gui-ios-verification.md)；不能以构建或标签可见替代尚未完成的验证。

### iOS 测试夹具与 GUI 覆盖分工

Max 同样出现插件 `type_text` 返回成功而字段仍为空的情况。补充 `open_sim`、点按字段、核对焦点后复测仍未生效；
没有继续盲目输入。此次回退未改动文本输入组件，代码测试已覆盖设置更新；因此用本机固定配置准备测试数据，
以真实 GUI 的备份选项切换覆盖修改后的 VM → SettingsStore 写入、状态显示与磁盘持久化。

准备阶段确认 Max 的两组配置与 GUI 记录的空原值完全一致，应用停止后只替换 `webdav_config`、`s3_config`。
沿用本项目 DataStore 1.2.1 protobuf 库，写入前核对文件指纹/原值，写入后回读；其余 **44 项设置及未知字段全部相等**。
保存的两组原配置只有空凭据，不保存整份用户设置。夹具不修改生产代码，不触发界面事件，不作为 GUI 通过依据。

| 夹具设置 | SHA-256 |
|---|---|
| Max 准备前 preferences | `e1a94aedd3e3b4d3810943bba190022603bf1c2018c23af0be1afbdf056c9b93` |
| Max 准备后 preferences | `fae6a0d9c16612772a4dfdd9c2cb8ffec50c75941d7849ea1361d26d02aeabde` |

GUI 实际通过：在两协议真实页面分别取消“文件”备份选项，切换标签及完整停止/冷启动后重新进入仍未勾选；
通过真实按钮测试连接并核对两项 ZIP 列表，冷启动后列表可以再次加载，最后通过 GUI 恢复勾选。
独立的同进程离开备份页再进入没有完成确认，不单独计通过；持久化依据同数据完整冷启动，恢复勾选另有磁盘回读。
文本字段的键盘输入仍明确未覆盖，不将它与代码测试或准备夹具混为一谈。

父任务在 `2026-09-10T03:05:32Z` 独立只读检查实际 preferences：两协议 items 均已从
`[DATABASE, FILES]` 变为 `[DATABASE]`，其他配置字段与夹具完全一致。
这次变更来自 Terra 的实际 GUI 操作，父任务在准备夹具后没有再写入设置；该检查证明选项写入到磁盘，
界面重入/冷启动显示结果另由子 agent 核对。

GUI 完成后，父任务确认磁盘中两组 items 均已恢复 `[DATABASE, FILES]`，其余字段仍准确匹配假配置。
应用停止后精确还原两组原空配置，回读一致，其余 44 项设置和未知 protobuf 字段保持。
恢复前 preferences SHA-256：`75b18dba81613e2d8d065d2ea6ec9cf4f73dc2d2cbea54a6274a5d80bab6e886`；
恢复后：`357153bb41ae984c830803b54ba975608b465830e90e454b9b17b052ff2f3cbe`。
恢复结果与准备前不要求整文件指纹相同，应用启动等原有行为可能更新其他设置；清理仅恢复两组测试配置。

本轮 iOS 请求计数：`GET /cmp08-ios/` 6 次（200）；`PROPFIND /dav/cmp08-ios` Depth 0 共 6 次、
Depth 1 共 5 次（全部 207）。没有写入/删除请求；计数仅补充客户端实际请求证据，不独立代表 GUI 通过。

### Android 本地网络权限诊断

同一安装包的 `dumpsys package` 显示 targetSdk=37，INTERNET 已授予、ACCESS_LOCAL_NETWORK 未授予。
普通 adb shell 的只读 TCP 探针成功，而 `run-as me.rerere.rikkahub.debug` 同 UID 探针连接超时；
GUI 的 S3 请求也对准确的测试 URL 发生 20 秒超时。顶层 Restore 实际打开 Loading 列表，排除了“按钮没有触发”的初步推断。
Android 官方说明：SDK 37 的本地网络默认受运行时权限限制，TCP 被阻止时通常表现为超时。
见 [Android 本地网络权限文档](https://developer.android.com/privacy-and-security/local-network-permission?hl=en)。

现有代码已声明此权限，并在网页服务设置使用 PermissionLocalNetwork，但备份页未调用该权限接线；
本项没有改动 targetSdk、Manifest 或这部分权限代码。这是测试过程中发现的既有平台权限覆盖缺口，
应通过已有平台权限能力处理，不需要新增业务 Repository 或运行时抽象。
本轮 Terra 从 Android 系统 GUI 临时授予“附近设备”，复测通过后从同一 GUI 选择“不允许”。
父 agent 只读复查 ACCESS_LOCAL_NETWORK 与 NEARBY_WIFI_DEVICES 均为 granted=false；
授权状态恢复为未授权，系统仍保留本次用户操作产生的 USER_SET 标记，未声称逐标志完全复原。
这次 GUI 通过以手动授权为前提，备份页本身尚未补上权限请求；该既有缺口没有在本项顺带修改。
诊断中创建的 `tcp:18769 → tcp:18768` ADB reverse 映射未用于通过证据，已经删除；正式验证仍使用 `10.0.2.2:18768`。

## 清理与边界

没有添加生产临时日志或调试开关，未读取钥匙串或调用真实模型。代码测试的临时目录自行清理。
Android 的测试字段已恢复并重新进入页面核对，两项本地网络相关权限的授予状态恢复为 false。
Android 应用已停止，设备上的本轮 UI XML 已删除，本轮启动的 Pixel_10_Pro_XL 模拟器已正常关闭，应用数据保留。
Desktop 两个独立 profile 均已删除，本轮应用进程已停止。
早期测试服务、构建日志、前测副本和临时工具目录已清理；错误命名的 Android 首轮表单截图已删除。
解锁续验重建的只读服务已按精确进程路径停止，旧 Pro 的临时 serve-sim 镜像已按设备范围停止。
旧 Pro 和 Pro Max 应用均已停止，测试字段已恢复，模拟器与原应用数据保留；未卸载或清空应用。
保存两组原空配置的临时副本、续验工具目录和日志均已删除；本轮所有子 agent 已关闭。
父任务已查看四张 Max 成功证据并核对报告中的 SHA-256，两个用户原有 Xcode 文件的指纹仍保持。

状态：代码、桌面及 Android 验证通过；iOS Pro Max 的连接、列表、GUI 选项写入和冷启动后重新进入的持久化通过，
文本输入未覆盖。第 08 项验证和清理已完成，提交包含 28 个文件，用户原有两个 Xcode 文件不纳入提交。
早期沿用 1Password SSH 签名配置执行 `git commit`，返回
`1Password: failed to fill whole buffer` / `fatal: failed to write commit object`（退出 128），未产生提交。
用户随后明确改用原生 ssh-agent；确认本地 ed25519 公钥与既有 Git 签名公钥指纹一致后，将该密钥加载到原生 agent，
本仓库的签名器设为系统 `/usr/bin/ssh-keygen`。提交仍启用 SSH 签名，未更换签名身份或关闭签名要求。

本项不声称验证真实云服务认证、实际远端备份/恢复/删除、系统文件选择器、全部导入格式或生命周期场景。
这些流程分别属于后续本地文件/同步逻辑收敛；现有代码在本项保持。

下一项：提示词预览和模板包装收敛，保留 Korte 替库，验证预览与生成上下文的同一套模板语义。

## 本机只读测试端复现

以下脚本只依赖 Python 标准库，可保存到临时文件运行；测试后停止进程并删除该文件。
只监听 loopback，日志不记录认证头或请求体。Android 通过模拟器主机桥接地址访问。

```python
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from html import escape
from urllib.parse import urlsplit
import json

class FixtureHandler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def reply(self, status, body):
        data = body.encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/xml; charset=utf-8')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)
        print(json.dumps({'method': self.command, 'path': urlsplit(self.path).path,
                          'status': status, 'depth': self.headers.get('Depth')}), flush=True)

    def do_PROPFIND(self):
        self.rfile.read(int(self.headers.get('Content-Length', 0)))
        base = escape(urlsplit(self.path).path.rstrip('/') + '/')
        def entry(name, date, size=1024, collection=False):
            kind = '<D:collection/>' if collection else ''
            return f'''<D:response><D:href>{base}{name}</D:href><D:propstat><D:prop>
            <D:displayname>{name or 'fixture'}</D:displayname><D:getcontentlength>{size}</D:getcontentlength>
            <D:getlastmodified>{date}</D:getlastmodified><D:resourcetype>{kind}</D:resourcetype>
            </D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>'''
        body = entry('', 'Thu, 10 Sep 2026 01:00:00 GMT', collection=True)
        if self.headers.get('Depth') != '0':
            body += entry('backup_cmp08_old.zip', 'Wed, 09 Sep 2026 01:00:00 GMT', 1024)
            body += entry('ignore.txt', 'Fri, 11 Sep 2026 01:00:00 GMT', 7)
            body += entry('backup_cmp08_new.zip', 'Thu, 10 Sep 2026 01:00:00 GMT', 2048)
            body += entry('backup_directory.zip', 'Fri, 11 Sep 2026 01:00:00 GMT', collection=True)
        self.reply(207, '<?xml version="1.0"?><D:multistatus xmlns:D="DAV:">' + body + '</D:multistatus>')

    def do_GET(self):
        body = '''<?xml version="1.0"?><ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
        <Name>cmp08</Name><IsTruncated>false</IsTruncated>
        <Contents><Key>rikkahub_backups/backup_cmp08_old.zip</Key><LastModified>2026-09-09T01:00:00Z</LastModified><Size>1024</Size></Contents>
        <Contents><Key>rikkahub_backups/ignore.txt</Key><LastModified>2026-09-11T01:00:00Z</LastModified><Size>7</Size></Contents>
        <Contents><Key>rikkahub_backups/backup_cmp08_new.zip</Key><LastModified>2026-09-10T01:00:00Z</LastModified><Size>2048</Size></Contents>
        </ListBucketResult>'''
        self.reply(200, body)

    def do_PUT(self):
        self.reply(405, '<error>Read-only fixture</error>')

    do_DELETE = do_PUT
    do_MKCOL = do_PUT

server = ThreadingHTTPServer(('127.0.0.1', 18768), FixtureHandler)
print('CMP08 fixture listening on loopback port 18768', flush=True)
server.serve_forever()
```
