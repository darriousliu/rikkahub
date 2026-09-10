# CMP 第 10 项桌面 GUI 验证

验证时间：2026-09-10。验证范围仅为桌面端真实 RikkaHub 翻译页；未调用聊天页或任何非 CMP10 模型。

## 环境和边界

- 构建产物：`desktopApp/build/compose/binaries/main/app/RikkaHub.app`（未重建）。
- 已核对的 JAR：`Contents/app/composeApp-jvm-37c8fdb3c834a7e7fa3b0a5e8d544bb.jar`，SHA-256 为
  `d34aacccc6aad97d70276573d79530b419006c51701654c60967188eb0f193c8`。
- 使用独立 profile `/private/tmp/cmp-rollback-10/desktop-home`，只通过其 CMP10 desktop 假提供商操作。
- 父 agent 在开始前核验当前实际会话模型为 `gpt-5.6-terra/high`；模型与请求细节、固定服务记录的对应关系由父 agent 单独核验。
- 通过 Computer Use / `@oai/sky` 操作真实 `me.rerere.rikkahub.desktop` 窗口。每次动作后重新读取 AX 状态；截图均在相关控件实际可见时取得。

## 结果

| 操作与预期 | 实际结果 | 可见证据 |
|---|---|---|
| 选 CMP10 Fast，默认简体中文，输入 `Hello` 后翻译应为 `CMP10 result (zh_CN)`，进度结束后恢复“翻译”按钮。 | 通过。先可见进度和“取消”，完成后可见结果及“翻译”按钮。 | `gui-desktop-fast-zh.png` |
| 通过语言选择器切为 English，再次 Fast 应为 `CMP10 result (en)`。 | 通过。English 在底部可见，结果为 `CMP10 result (en)`，按钮已恢复。 | `gui-desktop-fast-en.png` |
| 切 CMP10 Slow；应先显示 `CMP10 partial (en)`、进度和“取消”。立即取消后应保留部分结果、恢复“翻译”；数秒内不应出现 `COMPLETED_AFTER_WAIT`。 | 通过。部分结果、进度与取消按钮均可见；取消后保留部分结果并恢复“翻译”。随后观察 5 秒，仍无迟到完成文本。 | `gui-desktop-slow-partial.png`、`gui-desktop-slow-cancelled.png` |
| 切 CMP10 Error；应显示包含 `CMP10 simulated provider error` 的错误提示并结束进度。 | 通过。错误 toast 的完整文字可见，页面底部已恢复“翻译”。 | `gui-desktop-error.png` |
| 切回 Fast 重试；应成功且旧错误、Slow 部分结果均不串入。 | 通过。结果仅为 `CMP10 result (en)`，页面未显示旧错误或 `CMP10 partial (en)`。 | `gui-desktop-fast-retry.png` |
| 模型选择应在离页重进后保持 Fast。 | 通过。离页再进翻译页，模型选择器中 `CMP10 Fast` 以粉色选中底色显示。 | `gui-desktop-persist-page.png` |
| 同 profile 停止并冷启动后，模型选择应仍为 Fast；输入、结果和目标语言仅属 VM 内存态，应重置为空输入、空结果和简体中文。 | 通过。冷启动后翻译页显示空输入、空结果和简体中文；打开选择器后 `CMP10 Fast` 仍以粉色选中底色显示。 | `gui-desktop-persist-restart.png` |

## AX、视觉与限制

- AX 可读出输入、翻译结果、进度指示条、取消/翻译按钮和可选模型名称。它没有暴露三项 CMP10 模型的选中值，右上角仅显示相同的 `C` 图标，不能作为模型持久化证据。
- 因此两项持久化结论均以模型选择器处于屏幕可见区域的截图为准；截图中 Fast 的粉色选中底色与 Slow/Error 的未选中白底可直接区分。
- Error 的 AX 仅短暂暴露泛化的 `Error` 文本，完整错误文案以 toast 的可见截图为准。Slow 的取消后状态同时由可见截图和 AX 中“翻译”按钮、保留的部分结果确认。
- 未覆盖 Android/iOS GUI、真实模型或外网请求；这些不在本桌面验证范围。没有把进程启动、构建产物或 AX 的离屏节点单独判为 GUI 通过。

## 原始截图 SHA-256

| 文件 | SHA-256 |
|---|---|
| `gui-desktop-fast-zh.png` | `6cf1fc5b200fe58875ef49b55704bce0da96b3c2aea9e3d6a1ef2f2ece9e3274` |
| `gui-desktop-fast-en.png` | `70d901756be18d50ee860041bd3269d65c33e730746f52052633950fb1d15584` |
| `gui-desktop-slow-partial.png` | `001960f01698e0e4fb8d7c521cdfde8d3887c4506781455b0d7ab332ce603e90` |
| `gui-desktop-slow-cancelled.png` | `c903ceec7438dd20a391b908afb5dd4796dc0ef94e87ab34f313c1e13adaa454` |
| `gui-desktop-error.png` | `3a353a86c166b61a2f90dcf1eaabe897311f3d547e1e570ba67ca03b5000c791` |
| `gui-desktop-fast-retry.png` | `55fcde8f11b11673e029fcd91d56608ba40818a7f11a13b38def8177d98ed1f5` |
| `gui-desktop-persist-page.png` | `62207e4d78d59f4fcc57ac8e6159914737cf4c529c716c593c99365ce2fbd419` |
| `gui-desktop-persist-restart.png` | `bdb249ee6f1524f21bb5611b812c9920aab4e7cd68bce1f21a11630c6f0f1208` |
