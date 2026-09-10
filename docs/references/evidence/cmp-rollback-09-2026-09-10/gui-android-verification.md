# CMP 回退第 09 项：Android GUI 验证

验证日期：2026-09-10

平台：Android，既有 `Pixel_10_Pro_XL`（`emulator-5554`）

包名：`me.rerere.rikkahub.debug`

APK：`app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`

APK SHA-256：`b174509ae52c96fcd42e8e739888c65991e77ada27e5207339d7ebe83150ec2a`

执行模型：`gpt-5.6-terra` / `high`（由父 agent 在本地 session context 核对）

## 范围与数据

本次只在 Android GUI 中验证 `AssistantPromptPage` 对原 `TemplateTransformer` 与 Korte 的预览、错误和重置接线；未修改生产代码、未运行 Gradle、未发起模型请求，也未读取凭据。测试使用唯一临时助手 `CMP09-Android`（ID `7ea787a3-d880-43ad-afaa-944c19fd0337`）。父 agent 以 DataStore 夹具只写入该测试助手的 A 与 invalid 模板，并逐次读回核对；夹具准备本身不计作 GUI 通过。

父 agent 在重置后复核 `{{ message }}` 已恢复，并保存了 `android-fixture-reset-proof.json`。删除后最终只读复核确认测试 ID 已移除；两条既有助手 JSON 完整保留，43 项无关设置与未知根字段均保持原始字节，只有正常的 `launch_count` 从 2 增至 6，并保存了 `android-fixture-cleanup.json`。

## 实际 GUI 结果

| 步骤 | 预期 | 实际 | 结果 |
| --- | --- | --- | --- |
| A 模板首次进入 | `CMP09-A \| {{ role }} \| {{ message }} \| {{ time }} \| {{ date }}` 为 user 与 assistant 各显示一条预览；显示原中文消息、角色、本地日期和含秒时间，且无原始花括号 | user 显示 `CMP09-A \| user \| 你好啊 \| 3:49:44 PM \| Sep 10, 2026`；assistant 显示 `CMP09-A \| assistant \| 你好，有什么我可以帮你的吗？ \| 3:49:44 PM \| Sep 10, 2026` | 通过 |
| 离页再进入 | A 模板与预览仍存在 | 返回页面后仍显示 A 模板和重新计算的预览时间（`3:51:06 PM`） | 通过 |
| GUI 真实重置（A） | 点击页面重置后立即变为 `{{ message }}`，两条预览恢复原中文且 A 消失 | 两条预览即时恢复 `你好啊` 与 `你好，有什么我可以帮你的吗？`；无 `CMP09-A` | 通过 |
| 同 profile 冷启动 | 强制停止后重新启动并进入相同页面，仍为 `{{ message }}` | 冷启动后模板和原 user 预览仍为默认消息模板/原文 | 通过 |
| invalid 模板错误 | `{% unsupported_tag %}` 显示实际 Korte 错误 | 页面红色错误提示为 `Can't find tag unsupported_tag with content at 7ea787a3-d880-43ad-afaa-944c19fd0337:1:3` | 通过 |
| GUI 真实错误恢复 | invalid 状态点击重置后恢复默认模板和两条原中文预览 | 重置后显示 `{{ message }}`，两条原中文预览恢复 | 通过 |
| 测试数据清理 | 仅删除本轮 `CMP09-Android`，保留两个既有默认助手 | 在该助手专属 Actions 菜单中选择 Delete 并确认；最终列表仅余两条 `Default Assistant` | 通过 |

## 已知未覆盖范围

设备键盘自动化未能可靠完成“精确键盘输入 A 后改为 B”的替换：`adb shell input` 的选择与文本转义在该输入框中造成了损坏输入，因此没有把它计为产品回归，也没有以夹具冒充该 GUI 输入路径。一次坐标误触触发了 Gboard 麦克风权限弹窗，已选择“不允许”；这同样没有代码证据表明与 Compose 有关。

父 agent 的测试专用 A/invalid 夹具加上本次页面内真实重置、离页重进、同 profile 冷启动和 invalid 后真实恢复，验证了所要求的缓存刷新/恢复接线；精确的键盘 A→B 实时替换仍是上述工具限制下的未覆盖项。

## 证据

| 文件 | 内容 | SHA-256 |
| --- | --- | --- |
| `gui-android-a-two-previews.png` | A 模板下 user 与 assistant 两条预览、角色、原中文、本地日期和含秒时间 | `a5ad77dbc6c94366d260fa5b740e51ecc117c9ad9c079d982cfde36c4b168149` |
| `gui-android-reset-original-previews.png` | A 状态下页面真实重置后，两条原中文预览恢复 | `7a61ecc52aba2d323f46636e33e11ce5d1afc86a817aacc160b63ca28639b211` |
| `gui-android-cold-reset-persisted.png` | 同 profile 冷启动后默认模板仍持久化 | `3647a118091720b22d13972cbdcb2e3bb0412a16892b7b91a49584267cdb2d9f` |
| `gui-android-invalid-korte-error.png` | invalid 模板的 Korte 错误 | `10f44eee2f113a63f133eb5ed2999aff232d1a778169ca4898ced3b70c06ee97` |
| `gui-android-invalid-reset-original-previews.png` | invalid 模板下页面真实重置后，两条原中文预览恢复 | `a802b1af335daac89cb720630878644b17301074b2a54c618c04e68d1b4ac279` |
| `gui-android-final-assistant-list.png` | 删除测试助手后的列表，仅保留两个默认助手 | `c25979cfc373571aa6e6fdb9400d0584bcd647fe3777382b6c25769c94a11824` |

应用已停止；父 agent 的最终只读核对已完成并关闭了 `emulator-5554`。
