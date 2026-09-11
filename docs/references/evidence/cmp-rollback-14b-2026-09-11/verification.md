# 第 14B 项：保留存量附件并恢复删除

起点：`6785ac7999fca830aa705d6db862cc801df3ee88`。用户已明确保留旧附件和旧 fork。
状态：已完成；代码测试、构建及本项所需三端 GUI 均通过，测试资料已清理。

## 范围与必要性

14A 已恢复新附件的原 upload 目录和 fork 独立复制；非 Android 会话、助手清理的两处产品 DI 仍为空。
直接删除会损坏迁移期间已共用同一路径的旧 fork，因此这一项包含必要的存量兼容。

`FileKitFileCleaner` 是两个既有文件 I/O 接口的具体非 Android 实现：会话删除后检查数据库中剩余引用；
助手清理仍在原设置更新之前执行，仅扣除本次移除的引用。其余会话、未选中分支、嵌套工具结果、收藏快照、
助手头像/背景/预设消息、用户头像的引用需要保留。只在本次候选文件不再被这些数据使用时删除。
原 `File.delete()` 忽略删除失败的约定用同步 kotlinx-io 删除及 IOException 处理保留，不吞取消或所有 Throwable。
旧非 Android 头像/背景曾保存裸绝对路径，因此该平台清理实现也接受这种旧值。

这不是持久化引用计数，也没有启动时批量复制；旧 fork 的 IDs、消息、分支和文件位置不改变。
保留原 `Conversation.files` 文件集合规则，不改新 fork、聊天业务、助手复制行为或 Android FilesManager。
接口及原 VM 清理子方法只增加 suspend，等待非 Android 的数据库查询；原调用先后顺序不变。

iOS `LegacyIosFileMigration` 接入现有 Room `platformOnOpen` 和 DataStore migration。
只修复消息和收藏 JSON 的文件 url、设置中助手/用户头像和背景等文件字段；仅映射应用容器 Documents 内
upload、platform-files/attachments、platform-files/images 且当前对应文件真实存在的路径。
每层旧 file: 前缀各解码一次，支持实际旧导入产生的双前缀、双编码以及中文、空格、百分号等文件名。
存量文件本身不移动，数据库版本和设置版本不升级，其他 JSON 字段不丢弃。
无改动时保留原 JSON 字节；当前文件缺失、外部地址、不支持的目录或不可解析的地址保持原值。

## 代码验证步骤与预期

| 验证 | 步骤 | 预期 |
| --- | --- | --- |
| 原 fork 回归 | 原 SharedChatRuntime 实际复制四类附件，经真实产品清理实现删除源会话，再重读 fork | fork 副本、ID/元数据规则不变，源文件删除，fork 可读 |
| 旧 fork 共用 | 两会话引用旧目录或 upload 中同一文件，含非选中分支/嵌套工具；删除轻量源会话 | 独占附件消失，共享内容字节不变，fork 数据不变；删除最后 fork 后共享文件消失 |
| 收藏/设置仍引用 | 删除会话时文件也被收藏快照、助手预设或用户头像使用 | 文件保留，收藏与设置不变 |
| 助手资产 | 原 AssistantVM 删除共享背景的两助手；单独替换共用头像/背景；一次移除两个相同引用 | 先删独占头像，另一助手背景保留；最后删除时清理，其他助手不改 |
| 删除失败约定 | 缺失文件、非空目录、远程/content/data URL | 不删除远程地址，不因普通文件删除失败改变原 VM 行为 |
| iOS 路径修复 | 精确构造旧生成算法的 URI、旧容器、特殊字符路径 | 只改必要文件字段；raw/single/double URI 归于同一实际文件；重复执行无变化 |
| iOS 真正存储入口 | Pro Max 原生测试用真实 createIosAppDatabase 和 createIosSettingsDataStore 重开夹具 | DB/设置已修复，原 IDs、分支选择、文本、无关偏好保持 |

测试优先覆盖实际 I/O 和 Room；没有为了测试添加业务接口或观察器。
初次 JVM 测试遇到同步 SAM 改 suspend 后的增量编译旧测试类，重新编译并将旧夹具接到真实清理实现后消失。
助手 VM 测试等待真实 Room 工作时曾使用虚拟超时，已改为实际调度器等待持久化结果；没有因此修改产品状态逻辑。

## GUI 决策与预期

需要 GUI：恢复平台删除、存储打开时修正 iOS 资源地址，代码测试不足以确认实际应用 DI、图片呈现及冷启动链路。
由三个 `gpt-5.6-terra/high` 子 agent 执行。Android 使用 Android CLI；iOS 使用 Build iOS Apps 的 Pro Max；
桌面使用 JVM user.home 隔离的独立 profile，FileKit 自有目录中仅接触已登记的本轮 UUID 文件。
全程采用合成图片和离线会话，无需 DeepSeek 请求或钥匙串密钥。

每个平台实际通过页面删除源会话，检查源记录与独占文件消失，fork 文件散列不变；冷启动确认保留图片显示，
再删除最后测试 fork 并检查目标文件消失。非 Android 还验证独占头像删除和旧共享背景保留、最后清理；
iOS 另在保留助手的聊天页确认背景实际呈现。
fixture 可通过精确数据库/设置写入建立，但删除和显示验证必须由实际 GUI 完成。

## 实际结果与清理

最终代码验证 **385 次执行**通过：JVM 317、Android host 60、Pro Max 原生 8；失败、错误、跳过均为 0。
common metadata、iOS device 编译、Android APK、桌面 distributable 构建通过。
具体测试套件与时间在 [code-tests.json](code-tests.json)。

最终完整验证命令如下，结果为 `BUILD SUCCESSFUL`。iOS 的 8 项为本项选定的原生测试，
JVM 317 项和 Android host 60 项为对应任务的完整测试集；385 是跨目标执行次数，不是 385 个新增用例。

```bash
./gradlew :composeApp:jvmTest \
  :composeApp:iosSimulatorArm64Test --device 03C090DA-107B-4F9F-BCCD-8D5265D32820 \
  --tests '*LegacyIosFileMigrationTest' --tests '*IosFileCompatibilityWiringTest' \
  :app:testDebugUnitTest :composeApp:compileCommonMainKotlinMetadata \
  :composeApp:compileKotlinIosArm64 :app:assembleDebug :desktopApp:createDistributable --console=plain
```

临时 Kotlin 序列化夹具导出器删除后，又执行 `./gradlew :composeApp:jvmTest --console=plain`，
317 项通过。临时导出器不计入上述正式用例。iOS 应用由 Build iOS Apps 插件构建并安装；
主程序和 debug dylib 的构建、安装散列一致，三端产物记录见 [build-artifacts.json](build-artifacts.json)。

生产代码 13 个文件，增加 196 行、删除 11 行，净增加 185 行。
原会话仓库、两助手 VM 和 Android 文件接线仅增加 suspend；Android FilesManager、SharedChatRuntime、
Conversation 文件集合源码完全不变，机械比对见 [source-equivalence.json](source-equivalence.json)。
新增两个具体实现文件是本轮保留存量数据所需的文件清理和 iOS 地址修复，不新增业务接口。

| 平台 | 实际结果 | 证据 |
| --- | --- | --- |
| Android / Pixel_10_Pro_XL | GUI 删除源会话后独占文件消失；独立 fork 冷启动后图片可见，再删除时副本消失。通过系统选择器设置头像、背景后 GUI 删除助手，两处实际 upload 文件消失。 | [Android 步骤、预期与截图](android/README.md) |
| iOS / iPhone 17 Pro Max | 旧容器及重复 file: URI 自动修复。GUI 删除源会话后共享文件散列不变，保留 fork 冷启动后彩色图片可见；删除最后 fork 后共享文件消失。删除 A 后独占头像消失、B 的背景保留且在聊天页可见；删除 B 后背景和预览会话消失。 | [iOS 步骤、检查点与截图](ios/README.md) |
| macOS 桌面 | 在独立 profile 中，旧 fork 的分支图片在删除源会话及冷启动后可见；最后删除 fork 才清理共享附件。清除 A 的背景、删除 A 时均保留 B 的共享背景，删除 B 后清理。 | [桌面步骤、预期与截图](desktop/README.md) |

三个 GUI 子 agent 均使用 `gpt-5.6-terra/high`，已结束并关闭。父 agent 复核了实际图片呈现截图和最终磁盘/数据库结果。
iOS 初始手写夹具有序列化格式错误，早期小图片也不足以证明显示；均换成原 Kotlin 序列化数据和可辨识彩色图后重测。
这些准备失败不计作产品验证通过；最终三个 iOS 截图对应同一套最终夹具。

Android 原设置按字节恢复，分辨率覆盖恢复为 2560×1600 后关闭 AVD。
iOS 停止应用后按字节恢复原设置，非夹具会话、消息和收藏的行数及内容散列与本轮最终夹具建立前相同，
全部测试会话、助手和文件已清除，Pro Max 保持 Booted，见 [iOS 清理核验](ios/cleanup.json)。
桌面两个测试进程已停止，五个登记文件及整个临时 profile 已清除。
临时日志、夹具源码、设置/数据库备份及辅助工具已清理；未访问钥匙串或发起模型请求。
最终清理和源码核验见 [cleanup.json](cleanup.json)。

## 覆盖边界

- 这是存量保留及恢复删除接线，不改变所有原有文件生命周期：例如收藏删除本身、草稿、用户头像的 ChatRuntime
  直接清理入口没有在本轮扩展。仍被收藏使用的附件保留，移除收藏后的历史孤立文件不在本轮自动扫描清除。
- 路径修复在重新打开数据库/设置时执行；恢复备份的文件尚未落盘时保留旧值，下次打开再处理。
  文件已丢失且没有备份时不会凭空恢复内容。
- 旧共享文件在有引用期间保持共用；14A 后新 fork 仍按原代码复制独立文件。
- 不添加并发锁或改变原生成/删除并发语义。用户已有两份 Xcode 配置改动不属于本项。
- 桌面的收藏专用文件只是隔离对照，真实“删除候选也被收藏引用”的保护由代码测试覆盖；未声称 GUI 操作收藏删除。
  本项桌面 GUI 仅覆盖 macOS，没有 Windows/Linux GUI 结果。

下一项：第 15 项 E08，将本地备份导入导出业务收回原 BackupVM。
