# CMP 第 10 项 iOS GUI 验证

日期：2026-09-10

执行模型：gpt-5.6-terra / high（父 agent 已核验）

设备：iPhone 17 Pro Max（`03C090DA-107B-4F9F-BCCD-8D5265D32820`）

应用：`me.rerere.rikkahub.ios`，已安装 Debug 包；未重新安装、构建或修改 Xcode 工程。

## 已完成的可见准备

1. 使用非持久化 `cmp10-ios-gui` profile 核对项目
   `/Users/liuzhenhui/AndroidStudioProjects/rikkahub/iosApp/iosApp.xcodeproj`、scheme `iosApp`、Debug、指定
   UDID 与 bundle ID。
2. 启动既有安装包，进入抽屉菜单的“AI翻译”页面。
3. 在模型选择器中可见，测试仅选择了 `CMP10 Fast`、`CMP10 Slow`、`CMP10 Error` 三个 CMP10 iOS 假模型；显式选回
   `CMP10 Fast`。
4. 翻译页初始可见目标语言“简体中文”、空输入和“翻译”按钮；已在空输入框中输入 ASCII 文本 `Hello`。

## 结果：通过（见键盘限制）

以下操作均发生在真实 TranslatorPage，使用父 agent 准备的 CMP10 iOS 假提供商和本机 `127.0.0.1:18770` 固定服务：

1. 输入 `Hello`，默认简体中文以 CMP10 Fast 翻译，页面可见 `CMP10 result (zh_CN)`，请求结束后“翻译”按钮恢复。
2. 目标语言改为 English 后，CMP10 Fast 返回 `CMP10 result (en)`。
3. 改选 CMP10 Slow 后，页面可见 `CMP10 partial (en)`、进度状态和“取消”按钮；立即取消后，运行时快照显示“翻译”按钮恢复且“复制翻译结果”仍可用，随后持续 5 秒未出现 `COMPLETED_AFTER_WAIT`。取消后的部分结果保留及上游断开同时由该快照和父 agent 的固定服务断开记录核对；没有取消后状态的原始截图。
4. 改选 CMP10 Error 后，页面实际显示 `CMP10 simulated provider error` 错误 toast，且翻译按钮恢复。
5. 切回 CMP10 Fast 重试，页面仅显示新的 `CMP10 result (en)`，未混入 Slow 的 partial 或 Error 文案。
6. 离开翻译页再进入后，输入和结果重置、目标语言回到简体中文；模型选择器中 CMP10 Fast 保持选中。
7. 在同一模拟器数据容器停止并重新启动应用后，翻译页仍为空输入、无结果、简体中文；模型选择器仍将 CMP10 Fast 高亮为当前模型。

## 第二轮补充尝试

父 agent 再次核验执行会话 `01a08b58-3918-7e21-8afc-241e05ab0541` 为 `gpt-5.6-terra / high` 后，使用同一未持久化
profile 和同一已安装包再次启动应用。为收起软键盘，先多次通过 Build iOS Apps 的 `open_sim` 请求打开已核验的 iPhone 17 Pro Max
Simulator 宿主前端，再用 `@oai/sky` 枚举运行应用；结果列表中始终没有 Simulator，
`com.apple.iphonesimulator` 也不是可用目标，因此未进入 Device Hub，无法读取或临时切换 I/O > Keyboard > Toggle Software Keyboard。

随后按授权仅作输入焦点清理尝试：

1. 调试会话 `49bd3607-da60-4ccd-bf46-f323544d187e` 附加到 PID `87362`，此时执行暂停。
2. 尝试执行 `UIApplication sendAction:@selector(resignFirstResponder)`；DAP 后端明确返回“不支持 LLDB command evaluation”，并提示只有
   `lldb-cli` 后端才可执行表达式。命令未执行。
3. 立即 `debug_continue` 并 `debug_detach`，确认恢复执行和断开调试器。没有调用翻译方法、没有修改 VM、设置、DataStore 或业务状态。

第二轮未产生可计为通过的 GUI 用例；该问题随后通过下述受限原生 LLDB 焦点操作处理，未影响前两轮失败记录的事实。

## 最终键盘辅助与证据

`type_text` 将 `Hello` 写入 TranslatorPage 后，软键盘仍会遮挡底部 FAB。为只清除输入焦点，先以插件启动返回的 PID 分别核验
`88710` 和 `92095` 的可执行文件都属于指定 UDID 下的 `RikkaHub.app/RikkaHub`，再使用
`xcrun lldb --batch` 对当前进程执行 `@import UIKit` 与 `keyWindow endEditing:YES`，并在同一批处理命令中 `detach`、`quit`。
该表达式没有读取用户对象、VM、密钥或设置，也没有调用翻译方法；之后键盘节点从运行时快照消失，才使用 XcodeBuildMCP 触发真实翻译。

这验证的是“借助工具收起键盘后的翻译流程”。普通用户在软键盘遮挡 FAB 时的收起路径仍**未覆盖**。

| 原始截图 | SHA-256 | 可见证据 |
| --- | --- | --- |
| `gui-ios-fast-zh.jpg` | `342b15fa928b76971f5bca10c9a8b514c66db1e1dc892f6717323d48725c1431` | `CMP10 result (zh_CN)` 与恢复的翻译按钮 |
| `gui-ios-slow-partial-cancel.jpg` | `76cc0123d353f14034b93fafe03c665b5e633654e6f4dfc4421769fe2abe5329` | 取消前的 `CMP10 partial (en)`、进度和取消按钮 |
| `gui-ios-error.jpg` | `8dcb247b35854ad564dbba56b7c28facb4988e00b8985c73d190d2f30ab2c6e1` | 完整 `CMP10 simulated provider error` toast |
| `gui-ios-fast-retry-en.jpg` | `1a8230e70c94da692f2d92c39900786044b39cab8e523c3ce062342556603972` | Fast 重试后的 `CMP10 result (en)` |
| `gui-ios-model-fast-persisted.jpg` | `8e57423aa484340052f3f214541657ccccc80e021bd980b4fb42c87a364e09ab` | 离页重进后 CMP10 Fast 高亮 |
| `gui-ios-cold-fast-persisted.jpg` | `08a8afca7c787c9d10bf4c7afc932ec6c92f300bfbf90e4d6ded82e2bec22ff8` | 冷启动后 CMP10 Fast 高亮 |

## 校验值与限制

- 父 agent 提供的已安装 `RikkaHub.debug.dylib` SHA-256：
  `909001374802376b84fe310dad6035aefb445d490d60b57f1189c5a89942bf57`。
- 未请求真实模型、未读取钥匙串或真实密钥、未直接编辑 DataStore 文件、未切换或删除任何既有助手/会话。
- 上表中的截图均为 XcodeBuildMCP 返回的原始 JPEG，直接复制为 `.jpg`，没有转码。此前没有错误 toast 的旧
  `gui-ios-error.jpg` 已被本轮有效的 toast 原图替换，不作为错误文案可见证据。
- 最后一轮已切回 CMP10 Fast 并通过 XcodeBuildMCP 停止应用，供父 agent 恢复原三项设置和模拟器剪贴板。
- 父 agent 随后已核验 iOS Preferences 整文件 SHA-256 与本轮开始前一致，并确认模拟器剪贴板已恢复原值。
