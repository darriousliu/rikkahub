# 第 16 项：原 S3Sync / WebDavSync 归位

起点 `056166f83b2a46dda9a4c81679041b7b6044fc8b`，对照 tag `2.4.5`，审计 E07。
当前状态：本项回退、400 次代码测试、三端构建与所需 GUI 验证完成，测试资料已清理。
配置文本输入、真实云厂商和部分设置断言的覆盖边界，以及 iOS 基线的物理散列限制，见下文。

## 回退范围与判断

将原 Android `S3Sync.kt`、`WebDavSync.kt` 移至 common，BackupVM 和两端 DI 直接使用原类。
删除 `SharedS3BackupTransport`、`SharedWebDavBackupTransport`、`BackupArchiveService`、
`S3BackupTransport`、`WebDavBackupTransport`；两种 BackupItem DTO 回到原 Sync 文件。
原来 S3/WebDAV 各自拥有的归档方法继续留在原类中，未把这部分原有重复再次抽成业务服务。

同时删除只剩重复实现的 Android `JdkSha256Crypto`，改用已有 `PlatformSha256Crypto`。
文件摘要循环留在 S3Sync 私有方法中，按原 8192 字节读取；Ktor 客户端、平台 engine、请求签名算法均未改写。

生产范围为 **23 个路径，增加 1026 行、删除 1406 行，净减少 380 行**；统计含四个原文件从 app 移至 common，
实际文件数净减少五个。完整范围见 [production-scope.json](production-scope.json)。

保留的必要适配：

- `BackupFileLayout` 仅保留文件、缓存和数据库实际路径。Android 主库名 `rikka_hub`，iOS/JVM 主库名 `rikka_hub.db`；
  ZIP 内仍是原 `rikka_hub.db`、`rikka_hub-wal`、`rikka_hub-shm`，恢复按各平台物理 basename 映射。
- 原 Java File/流改用已有 kotlinx-io Path、文件 util、ZIP I/O；读取权限和符号链接判断用 `expect/actual`。
  原 ZIP 源目录/目标路径检查一起迁入 common，手写 ZIP 内部实现留给第 17 项。
- 保留第 15 项 FileKit 选择器输入的临时文件复制和 finally 清理，公共入口仍为 PlatformFile；
  内部 Path 重载保留原存在性、可读性和异常包装。iOS/JVM 同样经过该复制，不再拥有第二套本地恢复业务。
- 第 14 项已约定保留的 `platform-files/attachments`、`platform-files/images` 继续备份和恢复，
  新文件仍使用原 upload。兼容分支直接放入原方法，两端保留同一目录约定。

## 撤销的行为偏移

| Shared 版本额外行为 | 归回原 Sync 后 |
| --- | --- |
| 上传失败/取消也在 finally 删除已准备归档 | 仅上传成功后删除；失败包保留，VM 不记录成功时间 |
| 创建 ZIP 失败时额外删除未完成归档 | 保留原创建顺序和失败残留，不增加重试/清理策略 |
| 下载恢复统一使用 UUID 临时名 | 按原 `item.displayName` 下载到缓存，finally 清理 |
| 设置恢复异常直接传播 | 保留原 `Failed to restore settings: …` 包装，本地恢复再保留 `Restore failed: …` |
| 独立归档服务使用另一套目录遍历、文件条件和恢复判断 | 原 Sync 方法和已有路径安全工具共用，移除独立服务业务 |

原代码在设置恢复时会把 CancellationException 包成普通 Exception；本轮有明确测试保留该行为。
备份文件名仍精确到秒，并发请求仍独立执行；没有顺带添加锁、唯一文件名或数据库状态机。
整个 BackupVM 除构造类型和 import 外未改业务，成功时间仍由其原方法记录。

六个协议测试/列表/删除方法体经明确的空白、注释、Instant.EPOCH 替换后与 tag 相同；
两个上传方法在替换 Ktor 文件请求体后、原本地恢复方法在替换路径日志后也通过机械对照，见
[source-equivalence.json](source-equivalence.json)。归档和网络流方法包含上面的依赖适配，不宣称整文件逐字相同。

## 代码验证步骤、预期和结果

改动前定向基线 26 项通过：BackupVMContractTest 22、AttachmentArchiveTest 4。
改动后使用真实 Sync、SettingsStore、Ktor MockEngine 和实际 ZIP 读写；不为测试保留生产接口。

| 操作/输入 | 预期结果 | 结果 |
| --- | --- | --- |
| 两协议 × DATABASE/FILES 四种组合，独立 Java ZipFile 读取 | settings 始终第一项；主库/WAL/SHM 顺序及文件集合与原约定一致 | 通过 |
| 两种物理数据库名，删除文件再恢复 | 主库及侧文件写回正确物理路径，upload/fonts/skills/旧附件字节一致 | 通过 |
| 真实 SQLite 开启 WAL，写入尚未 checkpoint 的中文行再归档 | 单独主库负对照为 0 行；按归档主库和 WAL 恢复后为唯一一行、正文一致 | 两协议 × 两种 basename 通过 |
| 恢复时四种选项组合 | settings 始终恢复；未选中数据库/附件保持原磁盘值 | 通过 |
| 上传大于 1 MiB 的文件；检查实际请求体、内容头与 URL | 内容完整，成功后缓存删除；S3 payload SHA 与独立 JDK HMAC 验签一致 | 通过 |
| WebDAV collection 不存在 / MKCOL 失败 | PROPFIND → MKCOL → PUT；创建失败时不上传、不删已准备归档 | 通过 |
| 列表含非备份、目录、相同日期及无效日期 | 原过滤、稳定降序、epoch 回退、prefix/max-keys/depth/item 选择规则保持 | 通过 |
| 上传/下载异常、抛取消、真实挂起请求被取消 | 上传失败包保留；下载临时包清理；无额外上传重试或成功时间 | 通过 |
| 无效 settings、设置持久化失败/取消、不可读数据库源 | 保留原异常层次和处理顺序；后续文件不写入；创建失败不静默跳过 | 通过 |
| 路径穿越、目录循环/文件符号链接、嵌套 upload/font | 原路径工具阻止越界，技能递归且不跟随循环，upload/font 只含直接文件 | 通过 |
| VM 并发、完成时间、设置变动、初始列表/清除、本地强制全部选项 | 原 VM 意图保留，断言实际请求/文件/持久化结果 | 22 项通过 |
| 第 14/15 项已保存附件和本地导入样本 | 旧附件路径、原生 ZIP、Chatbox/Cherry 已覆盖行为保持 | 通过 |

正式统计 **400 次测试执行：JVM 340，Android host 60；失败、错误、跳过均为 0**。
其中新增 BackupSyncContractTest 15 项，方法内部循环核对两种协议和平台路径组合。
见 [基线](baseline-tests.json)、[最终测试明细](code-tests.json)。

首轮测试修正只涉及夹具：Ktor 将内容头放在 OutgoingContent、协程 debug 会复制异常并附带原异常、
ZIP 层可先于 Sync 拒绝穿越路径，以及测试初始化设置已经落盘。未据此改变产品处理流程。

```bash
./gradlew :composeApp:jvmTest :app:testDebugUnitTest \
  :composeApp:compileCommonMainKotlinMetadata :composeApp:compileKotlinIosArm64 \
  :composeApp:compileKotlinIosSimulatorArm64 :app:assembleDebug :desktopApp:createDistributable --console=plain
```

完整构建通过；追加 WAL 用例后重新执行全部 JVM 测试通过。
iOS 最终应用由 Build iOS Apps / XcodeBuildMCP 构建通过，源码与产物散列分别见
[production-files.json](production-files.json)、[build-artifacts.json](build-artifacts.json)。

## GUI 决策与实际结果

本轮必须三端 GUI：Sync 和归档接线统一且包含实际数据库覆盖、平台文件路径和重启链路，代码测试不能替代这些验证。
三端均使用 Terra/high，28 个启动/续跑回合的实际模型均已核对，见 [gui-models.json](gui-models.json)。

Android 使用 Android CLI；iOS 使用 Build iOS Apps 插件驱动 iPhone 17 Pro Max 上的独立 XCTest；
macOS 使用独立测试 profile。输入为离线合成会话和文件，服务只监听本机，使用测试账号，不读取钥匙串密钥。

各端每种协议的步骤：GUI 上传备份 → 删除合成会话并确认消失、移除对应测试文件 → GUI 远端恢复 →
确认原重启操作 → 核对会话及两条正文、设置和文件 → GUI 删除远端测试备份。
另通过本地“备份文件导入”系统选择器导入仅含本轮 upload 标记的小 ZIP，验证 FileKit 输入复制、
源文件保留及临时文件清理。该入口也随 Sync 迁入 common，不能用 Chatbox 导入代替覆盖。
上传归档另由父 agent 使用独立 ZIP/SQLite 读取核对；检查器自身已通过 WAL 正例和错误正文负例，
见 [archive-checker-control.json](archive-checker-control.json)。正式记录区分 GUI 观察、数据读取和未覆盖范围。

离线服务器独立客户端自测已通过，见 [server-selftest.json](server-selftest.json)。
它校验实际 Basic/SigV4 请求，不等同于真实云厂商服务认证兼容验证。
六份正式上传归档的 CRC、数据库/WAL、唯一会话、两条正文、两份附件和所选配置均通过父端独立读取。
三端两协议均有真实 PUT、GET ZIP、DELETE；iOS S3 因恢复选项复测多一次 GET。
最终远端归档为零，见 [协议结果](protocol-results.json) 和 [无凭据的请求记录](protocol-requests.jsonl)。

验证边界与夹具修正：

- Compose 表单输入出现焦点错位/字符乱序后，改为停机预置两项离线配置；临时工具只改两项 protobuf entry，
  其他 entry 原始字节保留。实际备份、列表、删除、恢复、重启和本地选择器仍由 GUI 操作，不宣称配置文本输入完整通过。
- iOS 子 agent 为读取 schema 打开了唯一私有基线，触发 SQLite checkpoint，导致主库/WAL/SHM 物理布局改变。
  父 agent 在另一个完整副本中冻结了该测试前逻辑基线的 16 张表及 schema 摘要，确认检查未改变源副本；
  见 [checkpointed-baseline.json](ios/checkpointed-baseline.json)。结束后已独立确认 16 张表及 schema 的逻辑摘要、
  原 settings 散列一致，且检查未改源文件；见 [最终基线核对](ios/parent-final-baseline-check.json)。
  不能声称最初物理文件散列全部匹配。
- iOS 首份 expected 的会话 UUID 是占位值。父 agent 依据固定 Chatbox session id 和原 MD5/nameUUID 规则重算后，
  与独立读取的归档唯一会话、两条正文、附件和配置全部匹配；只修正预期数据，没有修改产品。
- iOS 首次 S3 恢复前取消了 FILES，原逻辑按传入选项跳过附件；归档设置恢复后 FILES 又显示为选中。
  这次不能作为完整附件恢复通过。重新保持 DATABASE+FILES 恢复，同一归档的两份附件在重启前后均匹配，
  没有为此修改产品。它也说明恢复范围使用操作开始时的 config，而非已恢复的设置。

Android 两协议的实际备份、删除会话、恢复重启、两条正文和附件、远端删除及本地 ZIP 系统选择器已完成。
S3 的 Dynamic Color 从备份时启用，改为禁用，再恢复为启用；WebDAV 首选的 Color Mode 位于归档之外的存储，
因此该协议的可见设置回滚没有单独断言，不计作通过。两协议设置恢复的代码测试及上传设置内容核对均通过。
Android 测试前 41 个文件的散列已逐项恢复一致，应用停止，设备临时文件和本轮端口转发已清理。
见 [Android 验证](android/verification.md)、[清理记录](android/cleanup-evidence.json)。

iOS 的 WebDAV 恢复与 S3 的 DATABASE 恢复均有两条正文的 GUI 断言；S3 的 DATABASE+FILES 复测另外核对重启前后附件。
本地 ZIP 选择器、两协议远端删除均完成，来源文件保留至校验结束，临时恢复文件清零。
详见 [iOS 验证](ios/verification.md)；其中保留了临时 XCTest 实际返回摘要，定位失败不计为业务通过。

桌面两协议均完成同样的实际恢复和重启链路，父端额外核对 S3 重启后的两附件字节。
颜色模式未作为 Settings 归档断言；改由已有截图核对 `backupReminderConfig.lastBackupTime`：
上传后显示本次时间，恢复后均回到归档的 0（界面显示“未记录备份时间”）。
本地 ZIP 导入、来源 SHA、marker SHA 和临时副本清理均通过；见 [桌面验证](desktop/README.md)。

## 收尾

Android/iOS 应用已停止并恢复测试前数据；桌面独立 profile、三个精确 FileKit 标记及测试进程已清理。
用户要求保留的旧附件不变。六个远端测试备份均经 GUI 删除，三个 Terra agent 已关闭；
父端确认离线服务进程和 18776 监听均不存在。私有基线、归档、配置、检查器、临时驱动和日志均已删除，
见 [统一清理记录](cleanup.json)、[iOS 工具资料清理](ios/parent-private-cleanup.json)。没有新增生产临时日志。

生产源码在构建和 GUI 期间保持冻结。原有两份 Xcode 工程/方案改动保持初始散列，未纳入本项。

下一项建议：第 17 项 B06，先评估成熟 ZIP 库/平台 ZIP 实现与原归档的双向兼容，再决定替换手写内部实现。
