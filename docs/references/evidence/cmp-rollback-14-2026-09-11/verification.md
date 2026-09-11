# 第 14 项 B09：附件目录、fork 与备份文件集合

起点：`6211dd359e055d157292330bb6832180501ff1c9`。原行为基线：tag `2.4.5`。

状态：14A 的代码、构建、代码测试及所需三端 GUI 验证已完成；清理见 `cleanup.json`。第 14 项分为两步：

- **14A**：恢复新文件目录与命名、fork 独立复制、文件操作失败约定，补齐旧目录的归档范围。
- **14B**：处理旧版本已经共享附件路径的 fork 数据，再接通非 Android 会话/助手文件删除。
  已询问用户这些存量数据是否需要保留，尚待答复；不能把 14A 的完成写成整个 B09 已完成。

## 原实现与本次变化

| 部分 | tag 2.4.5 | 本项之前 | 14A 的实现 |
|---|---|---|---|
| 聊天附件、头像、背景、角色卡背景 | `upload/UUID.扩展名`，文件 URI | 多个 `platform-files` 子目录，`UUID-原文件名`；头像/背景保存裸路径 | 恢复 `upload`、UUID 扩展名及文件 URI |
| 文件工具结构 | FilesManager / FileUtils 工具方法 | FileStoreArea、PlatformFileStore 接口、StoredPlatformFile DTO、FileKit 实现 | 删除三个额外类型及文件名清洗规则；保留多处复用的 FileKit 复制/写入工具 |
| UUID 文件名 | 文件名扩展名优先，其次 MIME，再回退 bin | UUID 与原名称拼接，另加清洗/截断 | 原 `buildUuidFileName` 方法体移入 common；Android 保留 MimeTypeMap actual，JVM/iOS 使用已有 Ktor MIME 表 |
| 复制 / 字节写入失败 | 每个复制在 runCatching 内，失败跳过；已创建的文件不清理；字节写入异常上抛 | 统一 Result、失败清理目标、复制取消另行重抛 | 恢复两种操作各自的失败与取消约定，保留 FileKit 必需的挂起 I/O |
| fork | 新会话、新 node ID，原 message ID；复制四种本地附件，失败回退原 URL；原元数据规则 | 只生成新 node ID，直接共用附件；复制 title/pin/建议等元数据 | 原 fork 方法体和四类型复制判断恢复；仅文件 I/O 改用 FileKit |
| 备份文件集合 | upload、skills、fonts | 仍只有原目录，遗漏已写入的 platform-files | 新写入回到 upload；两个旧目录继续按原相对路径备份、恢复 |
| 头像裁剪输出生命周期 | 同步保存头像后再清理裁剪临时文件 | 回调只启动异步复制，Android actual 随即删除来源 | 现有回调改为挂起回调，复制返回后才执行原 finally 清理 |
| 会话删除 | Repository 删除会话后调用 FilesManager 清理文件 | 非 Android 的 ConversationFileStore 为空实现 | 14A 保持接线，留待 14B 处理存量共享路径后恢复 |

没有恢复 Java/Android API 到 common。`FileKitPlatformFileStore` 仍是可复用的文件工具；新增的窄
`extensionFromMimeType` expect/actual 只解决 Android MimeTypeMap 不可共享的问题。没有增加业务回调、
锁、引用计数或数据库条件更新来掩盖旧 fork 的错误。

`SharedChatRuntime` 整体收敛仍属于第 24 项。这里仅恢复其 fork 方法，避免先接通删除后伤及其他会话。
`FilesManager` 的 Android 媒体解析、ManagedFile 登记及平台日志没有迁走；14A 不宣称这些链路已经统一。
非 Android 工具的复制失败仍由调用点跳过，不增加 Android 专用日志实现。

生产差异共 18 个文件，增加 189 行、删除 190 行，净减少 1 行。原 fork 完整逻辑的恢复和裁剪回调的平台适配
抵消了包装删除的大部分行数；三个额外类型已删除。新增三个测试文件共 479 行。逐文件统计见 `change-counts.json`。

## URI 平台适配

FileKit 0.15.0 的 Apple actual 将 `absolutePath()` 实现为 `nsUrl.absoluteString`，JVM 返回本地路径。
原迁移工具对两者都再加 `file://` 并编码，iOS 会出现重复前缀/编码。本项先转换回本地路径，再统一编码一次。
读取同时接受原 `file:/...` 与 `file:///...`；裸文件路径原样保留，避免把裸路径中的百分号误作 URI 编码。
这修正了新文件 URI 的生成；已存入数据库的重复前缀 URI 和重装后的旧容器绝对路径仍须在 14B 核对兼容，不计作已恢复。

原 `Conversation.files` 只收集 `file://`，本项没有扩大此业务集合。短 `file:/` 的复制能力通过独立用例验证。
fork 对远程 URL、data URI 和复制失败的本地 URL 保持原回退行为；没有新增下载或缺失文件保护。

## GUI 发现的裁剪生命周期问题

Android 两次真实选择图片并确认裁剪后，头像未更新，目标文件为 0 字节。源码对照确认问题在本项之前已存在：
`AndroidImageCropper` 调用 `onResult` 后立即在 finally 中删除裁剪输出；`UIAvatar` 的回调却只启动协程，
而 FileKit 的 Android 复制会切换到 I/O dispatcher，等到读取来源时文件已经被删。旧 PlatformFileStore 同样使用
这条挂起复制路径，因此问题不由本项恢复目录或 UUID 命名引起。

本项将现有 `onResult` 改为挂起回调，让 Android actual 等待保存完成再执行原清理；iOS/JVM 对应将原本在
UIAvatar 中的协程启动移到 actual。该修改恢复 tag 中先保存后清理的顺序，属于替换同步 Android I/O 为
挂起 FileKit I/O 后必须补齐的平台生命周期适配，没有增加复制重试、延时清理、业务状态或新包装类型。
这条系统裁剪回调链由 Android GUI 复验，普通文件测试不能替代其验证。

## 代码验证

基线先运行 11 项附件/真实 Runtime 测试，5 项通过、6 项失败。失败分别覆盖两个目录差异、短 URI、
fork 元数据、未知消息异常，以及删除源文件导致 fork 文件丢失。见 `baseline-tests.json`。
随后补充命名、归档、失败与取消用例；这些新增用例不计入上述基线数量。

| 操作 / 输入 | 预期 | 验证层级 |
|---|---|---|
| 同一个中文 PDF 复制两次、写 PNG 字节 | upload 中产生不同 UUID 文件；扩展名小写，字节相同 | JVM 与 Pro Max 原生 |
| 文件名/MIME 冲突、复合扩展名、隐藏文件、无扩展名、未知 MIME | 原扩展名优先级和 bin 回退不变 | JVM 与 Pro Max 原生 |
| 图片、视频、音频、中文文本导入 | 消息 part 类型及文档展示名保持 | JVM 与 Pro Max 原生 |
| 中文、空格、加号、百分号、井号，长/短 file URI | 文件路径往返正确 | JVM 与 Pro Max 原生 |
| 缺失来源与正常来源混合，upload 被普通文件占用，取消 I/O | 复制独立失败；已创建目标不额外清理；字节写入错误/取消传播 | JVM 与 Pro Max 原生 |
| 旧 platform-files 目录样本再次导入 | 原文件仍在，新副本可读 | JVM 与 Pro Max 原生 |
| 真实 SharedChatRuntime + Room：含两个分支、四种媒体、尾部节点的会话 fork | 截断位置、分支选择、ID、元数据及所有副本字节符合原实现 | JVM |
| 真实 Repository：删除没有正文的轻量源会话，重新读取 fork | Repository 补读正文再删源文件；fork 有独立文件；随后删 fork 仅删副本 | JVM，测试注入实际文件清理，**不代表产品 DI 已接通** |
| 远程/data URI、缺失本地文件、未知 messageId | 原 URL 回退、原 NotFoundException/message 保持 | JVM |
| 原目录、两个旧迁移目录、新导入文件、无关目录一起备份/恢复 | 正确集合、原相对路径、字节相同；无关目录不被打包 | JVM 与 Pro Max 原生 |
| 备份/恢复的 includeFiles=false | 两类附件目录均不导出/恢复 | JVM 与 Pro Max 原生 |

原方法比对见 `source-equivalence.json`：fork 主方法完整相等；文件复制辅助方法只改挂起声明与文件 I/O 调用；
`buildUuidFileName` 只替换 MimeTypeMap 查询。没有为测试创建新的生产接口或 Observer。

构建及测试命令：

```bash
./gradlew :composeApp:jvmTest \
  :composeApp:iosSimulatorArm64Test --device 03C090DA-107B-4F9F-BCCD-8D5265D32820 \
  --tests '*ChatAttachmentFilesTest' --tests '*AttachmentArchiveTest' \
  :app:testDebugUnitTest :composeApp:compileCommonMainKotlinMetadata :composeApp:compileKotlinIosArm64 \
  :app:assembleDebug :desktopApp:createDistributable --console=plain
```

取消用例最后补入，额外执行了同样的 JVM 和 Pro Max 原生任务；随后因 GUI 暴露裁剪生命周期问题，修复后再次执行
上述测试与构建。通过数量按最终一次结果统计，不将重复运行累加。
最终数量与结果见 `regression-tests.json`，产物指纹见 `build-artifacts.json`。
不把 JVM 的 Room/FTS 测试计作 iOS 数据库测试，也不把文件集合回归计作完整备份、跨设备绝对路径重定位或 ZIP 格式验证。
这些后续工作仍分别属于第 15～17 项。

## GUI 与清理

三端均需 GUI：本项改变了平台文件选择器导入后的落盘 URI、头像/背景接线和真实 fork 的文件读写路径。
`gpt-5.6-terra/high` 子 agent 负责 Android CLI、Build iOS Apps 的指定 Pro Max 和隔离桌面 profile。
实际模型以本轮 turn_context 记录核验，不仅依赖创建参数。

最终验证结果：

| 平台 | 操作与预期 | 实际结果与覆盖范围 |
|---|---|---|
| Android CLI / Pixel 10 Pro XL | 真实选择 PNG/TXT，离线保存消息，fork 后删源并冷启动；设置头像和背景 | 附件、fork、背景在裁剪修复前的冻结包通过；最终 APK 的系统裁剪、非空头像、冷启动呈现通过。头像输出 653 字节，裁剪临时文件已删除。 |
| Build iOS Apps / iPhone 17 Pro Max | Files 导入、PNG 离线保存及 fork 后删源冷启动；最终已安装包重新选择头像/背景并仅停启 | PNG/TXT 导入及 PNG fork 通过；最终包红头像、蓝背景跨冷启动实际显示，两个文件各 136 字节。TXT 离线发送补试被识别为普通发送并发生 TLS 失败，不计为该步骤通过。 |
| macOS / 隔离 profile | 最终包真实选择 PNG/TXT，离线保存，fork 后删源并冷启动；真实选择头像/背景后冷启动 | 附件及 fork 通过；头像、聊天背景跨冷启动实际显示。四个消息附件逐项验证源/副本哈希相同，两个头像/背景文件各 171 字节且与夹具相同。 |

各平台步骤、预期、截图与哈希见 [Android](android/README.md)、[iOS](ios/README.md) 和
[桌面](desktop/README.md)。五位 Terra 子 agent 先后执行本轮 GUI；桌面任务经历自动化排障与接续验证，
最后一张冷启动头像由父代理完成导航与截图，再由 Terra 独立复核。实际模型记录见 `agents.json`。
父代理复核保留的应用截图与文件证据，没有把占位图、仅保存 URI 或隐藏窗口截图当作实际呈现通过。

桌面 FileKit 使用环境 HOME 选择文件目录，与 JVM user.home 的数据库/设置隔离不同。本轮只读取隔离资料
记下的六个确切新 UUID 文件，逐项核验后删除；未扫描默认上传目录，也未访问默认数据库和设置。
临时助手通过移除本轮私有 profile 完成清理，不计作产品 AssistantAssetCleaner 的 GUI 删除验证。

Android 已删除本轮助手、会话、六个上传件和四个选择器源件；显示覆盖恢复为 2560×1600，AVD 返回停止状态。
iOS 原设置逐字节恢复，测试会话、七个上传件及本地 FileProvider staging 已清理；两张合成照片从正常图库普通删除，
系统 Recently Deleted 按正常策略保留，未要求永久擦除或再次认证。iOS 应用停止，指定 Pro Max 保持 Booted。
桌面测试进程均已停止；私有备份、隔离资料、选择器夹具和临时自动化/诊断文件在复核后删除，见 `cleanup.json`。
仓库只保留应用内合成测试证据及验证记录，不包含相册图库截图、数据库备份或凭据。生产代码没有新增临时日志。

本项未读取测试密钥、未取得成功模型响应。非 Android 的产品物理文件删除仍未接通；JVM 测试注入的删除实现
与测试后的手工文件清理均不能代替 14B。旧 iOS 重复前缀 URI、重装后的旧容器绝对路径，以及已经共用文件的旧 fork
仍待 14B 处理；完整备份/恢复、跨设备路径重定位与 ZIP 格式验证仍属于第 15～17 项。
