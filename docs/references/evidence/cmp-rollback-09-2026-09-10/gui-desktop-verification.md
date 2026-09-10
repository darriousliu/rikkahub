# CMP rollback 09 — desktop GUI verification

- 日期：2026-09-10（Asia/Shanghai）
- 平台：macOS desktop；未操作 Android/iOS，未读取凭据或发送模型请求。
- 实际会话模型：`gpt-5.6-terra` / `high`，父 agent 已在 [gui-agent-models.json](gui-agent-models.json) 核验。
- 最终 bundle：`desktopApp/build/compose/binaries/main/app/RikkaHub.app`；其中 JAR 的 SHA-256 为
  `607d2f1e72c1e313ab2367d7d1012dad51e9336b504bf002552b8032ba408e3c`。

## 结论

**通过。** 本次使用独立 `user.home` 的真实 RikkaHub 窗口完成离线模板回归。`sky.paste` 保留了包含
花括号的精确 A/B 模板；A→B 立即更新预览，B 经离页返回和冷启动保持；无效标签显示实际 Korte 错误，
恢复有效模板与原生 Reset 均立即恢复可见预览。

先前 `type_text` 的工具输入和实际字段不一致，未作为通过依据，也不对原因归因。本次所有输入覆盖均使用
本机 `@oai/sky` 文档化的 `sky.paste({ app, text, format: 'text' })`，实际 app ID 为
`me.rerere.rikkahub.desktop`。

## 视觉证据

下列带“截图可见”的结论来自原始 PNG 的实际视觉复核；仅标为“AX”的字段结论来自同一操作后的可访问性快照。

| 场景 | 预期 | 实际 |
|---|---|---|
| A | 字段为 A；两条预览含角色、两条中文、含秒时间和本地日期 | 截图可见两条 A：`user` / `assistant`、`你好啊` / `你好，有什么我可以帮你的吗？`、`20:03:24`、`2026 Sep 10`；字段 A 另见字段截图。 |
| A→B | 两条预览立即更新为 B | 截图可见两条 B，时间 `20:04:02`；AX 同时确认字段精确为 B。 |
| B 冷启动 | 同一 profile 重启后 B 和两条预览保持 | 截图可见冷启动后的两条 B，时间 `20:05:00`；同一轮 AX 快照确认字段仍为精确 B。 |
| 无效标签 | `{% unsupported_tag %}` 显示实际 Korte 错误 | 截图可见 `Can't find tag unsupported_tag with content at 1823a097-4d13-4269-ad3f-ae4e4fbc1044:1:3`。 |
| 有效恢复 | 改回 B 后错误消失且两条预览恢复 | 截图可见两条 B 恢复，时间 `20:05:56`；AX 确认字段为 B。 |
| Reset | 原生 Reset 恢复 `{{ message }}` 和两条原文 | 截图可见两条原中文；AX 确认字段立即恢复为 `{{ message }}`。 |

| 原始截图 | SHA-256 |
|---|---|
| [A 字段](gui-desktop-A-visual-field.png) | `cb713b69508725e7a0ca86bf445faa516c687b27fcd5e23348ed8efe06b67adc` |
| [A 两条预览](gui-desktop-A-visual.png) | `78a76fdc759c2fec1b7deef9b8acceb72d607751ebf24e459cd6a4311955fac2` |
| [B 两条预览](gui-desktop-B-visual.png) | `e924ce8c337eb4c424f18e40c1eeed152cc687f3d0a467b6f0a4c1153d5e9a59` |
| [B 冷启动两条预览](gui-desktop-B-cold-visual-final.png) | `544778a5d1e7b91974a6a81133e1b207bcc007b541238bab886faec7de74900f` |
| [Korte 错误](gui-desktop-invalid-visual.png) | `435306ff92364af6a631194f62f421270f81e6991cc86a6763f24d27d2663350` |
| [有效恢复两条预览](gui-desktop-recovered-visual.png) | `f9f26f1ee80d4522e1df10bfa2d6b4e3d87798bcb2882a7f38df0de522d6d7f4` |
| [Reset 两条原文](gui-desktop-reset-visual.png) | `8bc65d1d11eabfb815f84c165818fa2396b5c5405d26285afe2ce934ec81d9f8` |

## 清理

本轮补证 PTY `48839` / PID `74547`、`17050` / PID `74797`、`85434` / PID `75146` 均以对应 PTY 的
`Ctrl-C` 停止。此前已停止的初轮 PTY 为 `67623` / PID `72490`、`49446` / PID `73038`、
`40202` / PID `73944`、`91739` / PID `74118`。最终已删除
`/private/tmp/cmp-rollback-09-desktop` 的独立 profile、DataStore、数据库及 JavaFX 缓存；只保留本表原始
截图和本记录。

## 简短历史

用户解锁前桌面 CUA 曾被 macOS 锁屏阻塞，未执行 GUI 场景；该旧阻塞结论已由本次完整 GUI 结果取代。
