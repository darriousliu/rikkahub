# CMP 第 10 项 Android GUI 验证

## 范围与环境

- 仅操作 `emulator-5556`；启动前以 `adb emu avd name` 复核为 `Pixel_10_Pro_XL`。
- 验证包：`me.rerere.rikkahub.debug`；未重装、未构建、未改生产代码、测试代码、Git，未直接编辑设置文件。
- 仅从 UI 中选择 `CMP10 android` 提供商下的 `CMP10 Fast`、`CMP10 Slow`、`CMP10 Error`。未调用真实模型。
- 屏幕配置在验证后复核为既有覆盖值 `2560x1600`、`408 dpi`，方向值 `0`，未变更。

## 权限准备（不计为 GUI 通过）

首次 Fast 请求短暂显示 `Cancel` 后恢复为 `Translate` 且无结果；文字树和 `dumpsys package` 都显示
`ACCESS_LOCAL_NETWORK: granted=false`。这是已知 Android 本地网络权限未自动请求导致的阻断，不是翻译功能结论。
在记录原状态后，只对该包临时执行 `pm grant ... ACCESS_LOCAL_NETWORK`。结束时已撤销，最终复核为
`granted=false`。该准备步骤没有修改应用权限申请逻辑。

## 逐步结果

| 步骤 | 预期 | 实际 | 文字树证据 | 可见证据（SHA-256） |
| --- | --- | --- | --- | --- |
| 1. 初始页与简中 Fast | 真实翻译页默认简体中文；输入一次 `Hello` 后得到 `CMP10 result (zh_CN)`，按钮恢复 `Translate` | 通过。树中先有 `🇨🇳 简体中文`、`Hello`，随后为 `CMP10 result (zh_CN)` 和 `Translate` | `android layout` 在完成态显示上述三项 | `gui-android-fast-zh.png` `304941961b8ca58256d58b38db80a046d7a430e4c4b4988be1a4716c97a51d11` |
| 2. 改为 English 的 Fast | 可从语言菜单选择 English，结果为 `CMP10 result (en)` | 通过。语言菜单实际可见 English；完成态显示 `🇺🇸 English` 与正确结果 | 菜单打开后树未完整暴露下拉条目，完成态树明确显示 English 与结果 | `gui-android-language-menu.png` `40d3b246b2a7545f3effc1f84a7ad340751fac2dfed37300fc51dcbd60e72586`；`gui-android-fast-en.png` `c8c28fd3f2808f00efd2827ae76ea761f597e5a7ab1f948038be2ab5f6298746` |
| 3. Slow 流式与取消 | Slow 立即显示 `CMP10 partial (en)`、进度及 `Cancel`；取消后保留 partial、按钮恢复，45 秒后不出现 `COMPLETED_AFTER_WAIT` | 通过。中间态存在 partial、进度条和 Cancel；取消后仅保留 partial；越过 45 秒窗口后仍仅有 partial 和 Translate | 三次 `android layout` 分别显示 `partial + Cancel`、`partial + Translate`、延后仍为 `partial + Translate` | `gui-android-slow-partial.png` `1e99a6df98636ba589557aa4fd2f943a32a160544165d201871e7966fc3c421b`；`gui-android-slow-cancelled.png` `298ff18d054190941585443dd4317fa31f3f35f866278d46210faf2df13756ec`；`gui-android-slow-after-wait.png` `7026638c93ff880f42d27e789dc197263b1d0302225fec77e0c0e987a81ff031` |
| 4. Error | Error 请求显示含 `CMP10 simulated provider error` 的错误，进度结束 | 通过。常规 CLI 截图时提示已自然消失；重发同一固定 Error 后一秒内截取到实际红色错误提示，结果区回到占位且按钮可用 | 完成后树显示结果占位与 `Translate`；瞬态提示以可见截图为主证据 | `gui-android-error-visible.png` `a64e8be4e97cef7735f07011bb1112271f10aad48a89932cc6e11aa448346ac8` |
| 5. Fast 重试 | 切回 Fast 后得到正确新结果，不能混入 partial 或错误内容 | 通过。结果区只显示 `CMP10 result (en)`，按钮为 `Translate` | 树中仅有正确结果，无 `partial`、`error` 或 `COMPLETED_AFTER_WAIT` | `gui-android-fast-retry.png` `d530d95ca9ff9c9d3631828e7911052ffffcb14b1794eab173d3b0f98415f20b` |
| 6. 模型离页重进 | Fast 模型保持选中 | 通过。离页重进翻译页后打开模型选择器，`CMP10 Fast` 仍为蓝色选中项 | 选择器树列出三个 CMP10 模型；选中状态由实际可见高亮确认 | `gui-android-model-reenter-fast.png` `fde12cbeee8cce488f37eb9703da6179ae06ff3d49b5e63703c17e13eb3ead31` |
| 7. 强停冷启动 | Fast 保持；输入和结果为空，目标语言回到简体中文，不能把这三项误判为需要持久化 | 通过。冷启动后的翻译页显示输入占位、结果占位和 `🇨🇳 简体中文`；模型选择器仍将 Fast 高亮 | 冷启动页树显示输入占位、结果占位、简体中文和 Translate；选择器树列出 CMP10 模型 | `gui-android-cold-start.png` `1b893a534fb905025bd0e3bd8d7525bbacd6c99163bf6f929920c7a633632d1b`；`gui-android-cold-model-fast.png` `c3081cbb4790399224af0c30e20a49c54fd00047c77ab52214e2bac95e8422bd` |

## 结论与清理

上述项目均为真实界面操作及可见结果，不将启动、配置、固定服务准备或权限准备计作 GUI 通过。服务请求数与取消请求由父 agent 另行交叉核对。

验证结束后已执行 `am force-stop me.rerere.rikkahub.debug`；`pidof` 无输出，应用保持停止。设备未关闭，供父 agent 读回并恢复三项设置。
