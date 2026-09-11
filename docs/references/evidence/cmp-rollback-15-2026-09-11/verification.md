# 第 15 项：本地备份业务归回原 BackupVM

起点：`0e8f36b8582661bdc1bca56d374787a097c34e44`；依据 tag `2.4.5`，审计 E08。
状态：本项回退、385 次代码测试、最终构建及所需三端 GUI 均完成；数据恢复与测试文件清理已核对。

## 改动与判断

删除 `BackupLocalFileService`、`AndroidBackupLocalFileService`、`FileKitBackupLocalFileService` 三个文件及其 DI。
导入去重、助手设置更新、导出完成时间都能直接使用 common 中已有的 Repository 和 SettingsStore，
不需要再由两套平台服务承载。原 `ChatboxRestoreResult` 回到 BackupVM 文件，去掉 repository DTO 与 typealias。

原 `exportToFile`、`restoreFromLocalFile`、`restoreFromChatBox`、`restoreFromCherryStudio` 方法归回 BackupVM；
前三个方法只把 File 换成 PlatformFile，方法体与 tag 相同。Cherry 方法因已有 FileKit/ZIP 读取为 suspend 而相应适配，
保留原空列表异常和调用 `updateSettings` 启动更新的时机；旧日志可能包含完整提供商密钥，因此只保留数量日志。
机械比对见 [source-equivalence.json](source-equivalence.json)。

同时撤销三处可确认的业务偏移：Chatbox 不再把导入开始时整份设置快照写回，使用原方法的实时读取位置；
Cherry 不再改成等待设置写入后返回；Android 导出在更新时间失败时不再额外删除已生成归档，恢复原 VM 的顺序。
没有新增并发控制、异常保护或提供商去重规则。

FileKit 已能直接读取系统选择器返回的 Chatbox/Cherry 文件，取消 Android 对这两种输入的额外临时复制。
Android 原生恢复仍调用原 Java File 方法，因此只在 WebDavSync 的 PlatformFile 重载中保留系统 URI 复制与 finally 清理，
复制循环来自被移除服务。WebDavSync 原归档方法仅将输出包为 PlatformFile，原 Android 云备份调用处转回其文件路径。
既有 WebDavBackupTransport 暂时暴露原有两个本地归档入口；没有增加新接口。Shared 实现继续调用已有 BackupArchiveService。
归档算法、S3、两种 importer 均未改动，第 16 项继续统一 Sync/Archive。

ImportExportTab 仅改三个 VM 方法调用名称，系统选择器、导出 finally 清理和页面交互保持。
生产代码共 **10 个文件，增加 139 行、删除 268 行，净减少 129 行**；没有新增生产文件。

## 代码验证步骤、预期与结果

改动前原 BackupVMContractTest 的 14 项通过，见 [baseline-tests.json](baseline-tests.json)。
改动后保留相同断言，通过真实 VM、Room、FileKit 和 ZIP 补充 8 项，合计 22 项通过。

| 验证步骤 | 预期结果 |
| --- | --- |
| VM 导出和本地恢复，配置故意不选择备份项 | 强制使用原全部项目；导出成功后记录时间，恢复不改时间；异常/取消原样传播 |
| 固定 Chatbox 文件含系统提示词、中文正文、图片缺省、空消息、模型与用量 | 助手映射、稳定 IDs、时间、正文、模型映射、丢弃计数和系统提示词设置符合原方法 |
| 改已导入对话标题后重复导入相同文件 | 跳过已存在对话，保留消息与标题；提供商仍按原方法追加 |
| 实际 SQLite 查询暂停导入，期间切换助手并修改启动计数，再继续 | 已导入对话使用开始时助手，完成时原设置读取保留期间变化，并按原方法启用当时选中助手的系统提示词 |
| 无效 Chatbox、Cherry 缺失 data.json、Cherry 无可导入提供商 | 保留对应异常，不写设置或制造会话 |
| Cherry 样本包含重复提供商 | 仅执行原 importer 内部去重；VM 返回后由原 viewModelScope 更新设置，其他助手与备份时间不变 |
| 标准 ZIP 原生样本导入、导出后独立 ZipFile 读取，再恢复 | 文件内容一致、源文件保留、导出含 settings.json 和上传文件 |
| 原 14 项网络/设置/真实归档测试 | 配置、排序、并发、取消、失败、完成时间和设置写入失败语义继续保持 |

初次 Android 编译使用了 FileKit 未提供的 `.file` 属性，改为现有 `toKotlinxIoPath` 后编译通过。
测试夹具初次加载使用了错误的资源 classloader，且遗漏内置提供商数量；已只修正测试代码，正式 22 项均通过。
并发测试仅在测试 SQLite driver 上设置闸口，没有给产品增加观察器或接口。

## GUI 决策与预期

本轮需要三端 GUI：VM 构造和本地归档接线变化，Android 又直接读取选择器返回的 Chatbox/Cherry 文件，
代码测试不能替代系统 URI、权限和实际应用 DI 的验证。三端均使用 `gpt-5.6-terra/high`。
Android 使用 Android CLI；iOS 由 Build iOS Apps 插件在指定 iPhone 17 Pro Max 执行临时 XCTest 驱动；桌面仅运行独立 user.home profile。

每端步骤：取消打开/保存选择器 → 导入固定 Chatbox 并读取中文会话 → 再导入确认无重复会话 →
导入 Cherry 查看测试提供商 → 原生 ZIP 导入精确 upload 标记 → GUI 导出并私下读取 ZIP 的设置、数据库和标记条目。
预期取消不触发处理，导入数据可见且按原规则去重，导出可读并包含对应文件。
所有输入均为离线合成数据，无真实模型请求；原生 GUI 输入只含本轮标记文件，避免覆盖用户数据库/设置。
GUI 导出含实际设置，因此只保存在私有临时目录或设备本轮目录，核验后删除，证据不包含 ZIP 或设置原文。

## 最终结果与清理

最终 Gradle 验证 **385 次执行**通过：JVM 325、Android host 60；失败、错误和跳过均为 0。
其中 BackupVMContractTest 为 22 项（保留原 14 项，新增 8 项）。明细见 [code-tests.json](code-tests.json)。

```bash
./gradlew :composeApp:jvmTest :app:testDebugUnitTest \
  :composeApp:compileCommonMainKotlinMetadata :composeApp:compileKotlinIosArm64 \
  :composeApp:compileKotlinIosSimulatorArm64 :app:assembleDebug :desktopApp:createDistributable --console=plain
```

结果：`BUILD SUCCESSFUL in 1m 7s`。GUI 使用的最终源码散列已冻结，构建产物见 [build-artifacts.json](build-artifacts.json)。
三位 GUI 子 agent 的首次及续跑实际配置均核对为 `gpt-5.6-terra/high`，见 [gui-models.json](gui-models.json)。

| 平台 | 实际结果与证据 | 清理与边界 |
| --- | --- | --- |
| Android | 取消打开/保存、Chatbox 导入及重复导入、Cherry 提供商、原生标记文件、GUI 导出通过；导出独立读取确认唯一会话、两条消息及中文/固定回复。[记录](android/verification.md)、[父 agent 复核](android/export-content-check.json) | 设备夹具/导出/标记按精确路径删除，原数据库/设置/用户文件备份恢复命令成功；未做逐文件恢复后散列比对。AVD 停止，保留原 2560×1600 override；私有备份与导出已删除。GUI 看到了会话标题，正文由导出 SQLite 读取核对。 |
| macOS | 同一最终包通过完整选择器流程；实际显示中文会话及重复导入后唯一条目、Cherry 提供商，原生标记落盘，导出含设置、DB、WAL、标记与完整消息。[记录](desktop/README.md)、[导出检查](desktop/export-content-check.json) | 独立测试 PID、profile、日志、工具、夹具和导出均已删除；只清理登记的 `cmp15-desktop-native.txt`，父 agent 确认其不存在。 |
| iOS | Pro Max 上取消打开/保存、Chatbox 导入与重复导入、中文正文/固定回复显示、Cherry 提供商、原生标记 ZIP 和 GUI 导出通过；父 agent 独立读取导出，核对唯一会话、两条消息、WAL、标记及 Cherry 字段。[记录](ios/verification.md)、[父 agent 复核](ios/parent-export-check.json) | GUI 前设置/数据库及辅助文件恢复后散列匹配 5/5；专用 Files 目录、Documents 夹具、标记、ZIP 和私有备份已删除。父 agent 复查容器无本轮命名残留、最终包散列仍匹配；独立 XCTest 工程、runner、155 项临时工具产物已清理，Pro Max 保持 Booted。 |

Android 首轮正文核验只打开了 ZIP 中的 `rikka_hub.db`，没有按原恢复规则配对 `rikka_hub-wal`，
这次查询不能作为完整归档的数据丢失判断。独立 SQLite 负对照确认：只读主库为 0 条，按原文件名组合主库与 WAL 后为 1 条，
见 [核验器检查](zip-reader-check.json)（不计入 385 次产品测试）。同一最终 APK 补做 Chatbox GUI 导入和 GUI 导出后，
父 agent 重新运行修正的检查，确认唯一会话、两条消息和中文/固定回复完全匹配。桌面使用同一修正后的读取规则通过。

iOS 的 Device Hub 窗口定位及镜像触摸未能稳定工作，正式 GUI 改由独立临时 XCTest 工程驱动已安装应用，
经 Build iOS Apps 插件执行并直接取得设备截图；未修改产品或用户 Xcode 工程。控件错导航的尝试未计作通过。
临时驱动的执行次数不计入上述 385 次产品代码测试。

## 覆盖边界

- 本轮不重写 importer。Chatbox 当前使用 kotlinx.serialization 读取完整 JSON，尚不等同于 tag 的 Android JsonReader
  流式读取；大文件内存峰值、损坏文件的渐进导入行为未在本项声称等价。这里收敛的是原 VM 业务和 E08 的重复服务。
- 原生归档读写算法未变；GUI 验证系统选择器和标记文件/导出条目，完整“覆写数据库后重启恢复”归第 16 项验证。
- 原版 Cherry 设置更新是异步的，本轮按原样保留。导出完成时间仍在复制到用户保存位置之前记录。
- 用户原有 Xcode project/scheme 改动不属于本项，不纳入提交；桌面 GUI 仅覆盖 macOS。

下一项：第 16 项 E07，统一原 S3Sync/WebDavSync 和两套归档编排。
