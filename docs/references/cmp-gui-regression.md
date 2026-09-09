# CMP GUI 回归记录

## 后续测试约定

- 用户指定 GUI 回归统一使用 DeepSeek，尽量减少真实 API 的 token 消耗。
- 本机测试密钥保存在 macOS 钥匙串：service `dev.rikkahub.gui.deepseek`，account `rikkahub-gui`。测试程序应捕获读取结果直接用于请求，不在终端打印，不将密钥写入源码、文档、截图或提交。
- 当前测试模型为 `deepseek-v4-flash`，OpenAI 兼容 base URL 为 `https://api.deepseek.com/v1`。2026-09-09 已通过不生成文本的 `/models` 请求确认可用。
- 使用模型自定义请求体 `thinking: {"type": "disabled"}` 和 `max_tokens: 128`；短输入、短回复、关闭不相关工具。保留默认标题及追问建议提示词，验证真实产品路径。
- 优先复用一轮聊天验证回复、自动标题、追问建议、点击填入和重启保存；点击建议后不继续发送，除非该发送属于必要测试。
- 状态边界和错误矩阵优先用自动化测试；后续子 agent 按用户最新要求使用 `gpt-5.6-terra`，需要时操作 Android/iOS 模拟器或 Desktop。失败时先分析证据，再决定是否追加模型请求。
- Desktop GUI 后续使用“电脑”插件；用户已开启锁屏操作。先检查插件返回的应用状态，普通锁屏不再直接视为阻塞；若工具确实不可用，记录具体错误和已尝试的恢复方式。
- 测试使用独立配置或专用 Provider/助手/会话，保留用户既有数据。临时脚本、测试配置和验证日志不提交。
- 后续优先复用已创建的专用测试 Provider/助手；需要初始化时，可备份后预置 DataStore，减少配置表单操作。发送、建议点击和重启恢复仍须在真实应用界面验证。

## 2026-09-09：自动标题与追问建议

代码基线：`252a0b541`；Android 后续复验包含本轮发现的会话创建自锁修复。
本次验证聊天短回复、AI 自动标题、追问建议显示、建议点击填入输入框和重启后的持久化。

人工/GUI 验证步骤：

1. 在隔离测试配置中，将聊天模型、标题模型及建议模型统一设为 DeepSeek V4 Flash，并开启追问建议。
2. 在真实聊天界面发送 `只回复：你好`，或等义的 ASCII 短输入 `Reply only: hi`。
3. 等待回复结束，检查短回复、非空自动标题和建议列表。
4. 点击一个建议，检查输入框填入对应内容，消息数量不增加，不自动发送。
5. 重启应用，回到该会话，检查标题、消息和建议仍在。

### Desktop JVM

结果：通过。在 macOS 上使用真实 Compose 窗口（800 × 600 逻辑点），通过 AWT Robot 点击和输入；
以独立 `user.home` 启动，不修改日常 Desktop 配置。

| 检查 | 预期 | 实际 |
|---|---|---|
| 发送与回复 | 点击发送后得到短回复 | `只回复：你好` → `你好` |
| 自动标题 | 从默认标题变为模型生成的标题 | `简短问候` |
| 追问建议 | 回复完成后显示建议 | 显示 4 条建议 |
| 点击建议 | 只填入输入框，不自动发送 | 点击 `在吗` 后输入框为 `在吗`，仍只有原来的两条消息 |
| 重启 | 标题、消息和建议保持一致 | 点击窗口关闭按钮退出，再启动同一 profile；三项全部恢复 |

截图：[生成结果](evidence/cmp-gui-2026-09-09/desktop-generated.png)、
[建议点击](evidence/cmp-gui-2026-09-09/desktop-suggestion.png)、
[重启恢复](evidence/cmp-gui-2026-09-09/desktop-restart.png)。主 agent 已逐张复核，均只含聊天窗口。

应用日志记录 3 次请求：主回复 1 次、默认标题提示词 1 次、默认建议提示词 1 次。
点击建议和重启没有追加请求。主回复报告输入 17、输出 1，共 18 tokens；后台两次请求的 usage
未出现在捕获的日志中，不估算总量。临时 profile seed、首次运行及重启运行均 `BUILD SUCCESSFUL`。

### iOS Simulator

结果：通过。设备为 iPhone 17 Pro Max / iOS 27.0，运行真实 `iosApp`。
使用专用测试 Provider、模型及助手，保留原有设置和会话。AXe 输入工具仅支持 ASCII，
因此使用等义英文短输入，未为中文输入增加模型请求。

| 检查 | 预期 | 实际 |
|---|---|---|
| 发送与回复 | 点击发送后得到短回复 | `Reply only: hi` → `hi` |
| 自动标题 | 显示模型生成的标题 | `问候回应` |
| 追问建议 | 回复完成后显示建议 | 生成 5 条建议，在横向列表显示 |
| 点击建议 | 只填入输入框，不自动发送 | 点击 `可以多说点吗` 后输入框填入原文，消息节点仍为 2 |
| 重启 | 标题、消息和建议保持一致 | 停止并重新启动应用，标题、两条消息和 5 条建议仍在 |

截图：[生成结果](evidence/cmp-gui-2026-09-09/ios-generated.jpg)、
[建议点击](evidence/cmp-gui-2026-09-09/ios-suggestion.jpg)、
[重启恢复](evidence/cmp-gui-2026-09-09/ios-restart.jpg)。主 agent 已逐张复核。

iOS 构建和启动成功。测试结束后已恢复原来的默认助手、聊天/快速模型、标题/建议模型的缺省状态、
最近会话及两个原助手的配置；保留专用测试 Provider、助手和会话，应用已停止。
逐键核对 DataStore：除 `providers`、`assistants` 仅追加预期测试对象外，其余键及存在性与备份完全一致；
两个集合中的全部原对象也逐字段一致。
仅发送一轮消息，按业务路径触发主回复、标题和建议三项请求；未取得完整网络计数日志。
数据库中的主回复 usage 为 null，捕获日志也没有 usage 字段，因此不报告该端 token 总量。
本轮 iOS 的 `maxTokens=128` 仅限制主回复；当时模型 custom body 只有关闭思考，后台标题和建议
没有显式输出上限。收尾时已仅为专用测试模型补上 `max_tokens: 128`，供后续回归复用，未追加请求。

### Android

初始 Pixel_5 模拟器受到其他测试切换前台应用/安装卸载的干扰，未将该阶段的点击记作通过证据。
后改用独立 `emulator-5560`（Pixel 10 Pro XL AVD / API 37，2560 × 1600 横屏），
所有 adb 命令指定 serial，通过 `install -r` 保留数据；
使用专用测试模型，主回复、标题和建议统一设置关闭思考及 `max_tokens: 128`。

本轮发现并修复了一个既有会话管理问题：从历史列表打开 `Greeting Exchange` 时出现 ANR。
系统记录输入事件等待超过 5 秒；主线程停在 `AtomicSnapshotMap.Pending.await`，应用占用约 96% user CPU。
`ChatService` 在 `getOrPut` 的创建回调内更新 `_sessionsVersion`，同步唤醒的订阅者读取会话列表，
读到当前尚未完成的 Pending，导致同一主线程等待自身完成。

修复将通知与原有日志移到 `getOrPut` 返回之后，仅由实际创建会话的调用发送通知。
不改变 AtomicSnapshotMap 的公共 API、等待语义或会话存储格式；iOS/Desktop 不使用此路径。
证据：[ANR 截图](evidence/cmp-gui-2026-09-09/android-anr.png)、
[脱敏堆栈与资源数据](evidence/cmp-gui-2026-09-09/android-anr.txt)。

修复后结果：通过。安装新版时保留原会话，未重新生成回复。

| 检查 | 预期 | 实际 |
|---|---|---|
| 发送与回复 | 短回复正常显示 | `Reply only: hi` → `hi` |
| 自动标题 | 显示模型生成的标题 | `Greeting Exchange` |
| 追问建议 | 回复完成后显示建议 | 显示 5 条英文建议 |
| 打开历史 | 会话正常加载、界面可操作 | 修复后正常打开，未再出现 ANR；抽样应用 CPU 为 0% |
| 点击建议 | 只填入输入框，不自动发送 | 点击第一项后填入 `Just say "hi".`，等待后仍只有原来的两条消息 |
| 重启 | 标题、消息和建议保持一致 | force-stop 后启动先到默认新会话，再从历史打开原会话，三项均保留 |

截图：[修复后会话](evidence/cmp-gui-2026-09-09/android-generated.png)、
[建议点击](evidence/cmp-gui-2026-09-09/android-suggestion.png)、
[重启恢复](evidence/cmp-gui-2026-09-09/android-restart.png)。主 agent 已逐张复核。

独立设备的一轮会话产生主回复、标题、建议三项结果；修复复验没有追加请求。
主回复界面和持久化 usage 均为输入 16、输出 1，共 17 tokens；后台 usage 不可得。
早期 Pixel_5 尝试是否到达 API 无法确认，因此不将 Android 全程计数写成“恰好 3 次”，也不估算三端总 tokens。
已恢复原助手/模型选择和最近会话，保留专用测试对象；错误临时 Provider、含密钥的临时设置副本
及临时 GUI instrumentation 源已清理。

### 修复代码验证

新增永久 `app/src/androidTest/java/me/rerere/rikkahub/service/ChatServiceSessionTest.kt`，
通过真实应用 Koin 解析 Android `ChatRuntime` / `ChatService`，持续在 `Main.immediate` 收集 jobs，
再以普通主线程回调创建新会话，使用 5 秒期限验证调用返回及后续快照；重复获取必须复用同一状态，
不重复发送会话创建通知。无生成任务的会话按既有语义不出现在 jobs 结果中。
测试不发送消息、不写测试会话到数据库、不调用模型。

```bash
./gradlew :app:testDebugUnitTest --tests me.rerere.rikkahub.service.ChatServiceTest :app:assembleDebug
./gradlew :app:assembleDebugAndroidTest
adb -s emulator-5560 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5560 shell am instrument -w -r \
  -e class me.rerere.rikkahub.service.ChatServiceSessionTest \
  me.rerere.rikkahub.debug.test/androidx.test.runner.AndroidJUnitRunner
```

预期：构建成功，既有后台请求参数单测通过，新仪器测试正常返回，且没有模型调用。
实际：两个构建成功；`ChatServiceTest` 1 项通过；`ChatServiceSessionTest` 为 `OK (1 test)`，耗时 0.581 秒。
保留[仪器测试输出](evidence/cmp-gui-2026-09-09/android-session-test.txt)。无临时生产日志。

### 覆盖边界

本轮 GUI 验证真实 Provider 的短聊天、自动标题及建议主路径。网络错误、取消、过期响应和字段隔离
沿用该代码基线的自动化测试证据；本轮未重复制造付费请求验证这些分支。
单个 API key 的成功请求不能证明多 key 轮换，不将其记为轮换 GUI 通过。
本轮也不覆盖完整核心聊天矩阵中的附件、搜索、工具、通知或音频。

## 2026-09-09：聊天消息翻译

状态：`Pass`（Desktop 限定范围 GUI 回归，修复原生包启动问题后通过）；代码测试通过，详见
[迁移与验证记录](cmp-migration-progress.md#2026-09-09聊天消息翻译)及
[自动化结果](evidence/cmp-translation-2026-09-09/code-tests.txt)。

使用独立 Desktop profile，预存一条 assistant 消息 `Hello`，设置翻译模型为 `deepseek-v4-flash`，
提示词仅要求翻译；关闭思考，限制输出 128 token，不发送聊天，不触发自动标题/建议。
`gpt-5.6-terra` 子 agent 操作真实窗口，完成翻译、折叠/展开和首次重启；主 agent 完成清空与第二次重启，
并独立复核截图、窗口可访问性文本和 SQLite 数据。

| 验证步骤 | 预期结果 | 本轮结果 |
|---|---|---|
| 启动当前迁移代码的隔离 Desktop 应用 | 正常初始化，加载测试配置 | Pass；修复打包运行时后可读取已保存设置 |
| 对 `Hello` 选择简体中文翻译 | 出现中文译文 | Pass；唯一一次真实请求返回“你好。” |
| 折叠、展开译文 | 译文区域相应隐藏和恢复 | Pass；按钮在“展开翻译”与“折叠翻译”之间切换，正文随之隐藏/恢复 |
| 退出并重新启动同一配置 | 原消息和译文保持一致 | Pass；界面和独立 SQLite 查询均确认 `Hello` / `你好。` |
| 清除译文，再退出并重启 | 译文不再出现，原消息保持 | Pass；旧进程确实退出，重启后只有 `Hello`，SQLite 中 `translation` 为 null |

原生打包回归发现并修复了实际启动缺陷：裁剪后的 JDK 缺少 `jdk.unsupported`，DataStore 的 Protobuf
读取器找不到 `sun.misc.Unsafe`。`desktopApp` 的 `nativeDistributions` 现显式加入该模块；重新构建后，
验证运行时确实包含此类，并用预存设置的原生应用完成上述 GUI 流程。打包命令通过，恢复正常配置后的
共享 JVM 测试 137 项通过；记录见[打包修复与测试](evidence/cmp-translation-2026-09-09/desktop-packaged-startup.txt)。

证据：

- [首次重启后仍有译文](evidence/cmp-translation-2026-09-09/desktop-after-restart.jpg)
- [清空并第二次重启后无译文](evidence/cmp-translation-2026-09-09/desktop-cleared-restart.jpg)
- [各步骤窗口状态](evidence/cmp-translation-2026-09-09/desktop-gui-steps.txt)
- [两次重启后的独立 SQLite 检查](evidence/cmp-translation-2026-09-09/desktop-sqlite-check.txt)

此前电脑插件曾出现 `SCStreamErrorDomain -3811`；本次用户解锁后，主会话恢复，子 agent 重建会话后也可操作。
导航时的 `noWindowsAvailable`、重启后的 `procNotFound` 经重新读取窗口或使用 `.app` 路径恢复。
调用窗口的 `Raise` 可恢复清晰截图；滚动工具未奏效时，以 Tab 导航语言列表找到“清空翻译”。
全程通过电脑插件操作，没有用其他桌面控制方式替代。未专门验证锁屏期间的操作能力。

真实模型请求共 1 次；关闭思考、限制输出 128 token，不额外请求聊天、标题、建议或 Qwen MT。
未记录服务端实际 token 用量，因此不推算精确消耗。空响应、失败、取消与竞争仍由无网络代码测试覆盖。
本轮仅补充 Desktop GUI，不将结果外推为 Android/iOS 的翻译 GUI 已验收。
临时应用、配置、seed 和日志在提交前清理；钥匙串中的测试密钥保留供后续验证。
