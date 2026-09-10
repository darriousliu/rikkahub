# CMP12 desktop GUI 验证

验证日期：2026-09-11。所有桌面操作均通过临时 helper
`/private/tmp/cmp-rollback-12/desktop_gui` 对精确 PID 和 CGWindowID 发出；未使用
appPath 自动启动，也未操作默认 profile 的 PID 99566。

## 运行身份与清理

- 初次验证进程：PID 36676，实际目标窗口为 `RikkaHub`、CGWindowID 2500、
  bounds `(221, 81, 800, 600)`。
- 冷启动进程：PID 40632，以
  `JAVA_TOOL_OPTIONS=-Duser.home=/private/tmp/cmp-rollback-12/desktop-home`
  启动同一 distributable binary；实际目标窗口为 `RikkaHub`、CGWindowID 2572、
  bounds `(221, 81, 800, 600)`。
- PID 36676 和 40632 都已发送 `TERM` 并用 `ps` 确认不存在。持久 exec 会话为
  31647；本轮桌面进程 ID 为 36676、40632。

## 结果

服务事件均来自 `http://127.0.0.1:18772`，桌面请求使用 JSON、`queryNonempty=true`
且 `headerMatches=true`。

| 用例 | 预期 | 实际 | 请求 ID / 证据 |
| --- | --- | --- | --- |
| 初次运行 | 显示固定成功 answer 与 item | GUI 显示 `CMP12 desktop success` | 13，`gui-desktop-success.png` |
| 错误 | 服务返回 503，GUI 显示 `CMP12_HTTP_503` | GUI 错误路径触发，服务记录 503 | 14，`gui-desktop-error.png` |
| 重试 | 切回 ok 后通过 GUI 重试成功 | 服务记录 HTTP 200 | 15，`gui-desktop-retry.png` |
| 取消 | wait 请求离页后 30 秒内取消 | 离页后服务记录 `cancelled`，耗时 1.279 秒 | 16，`gui-desktop-cancelled.png` |
| 重入 | 回到 CMP12 JS 详情、重新输入并成功 | 服务记录 HTTP 200 | 17，`gui-desktop-reentry.png` |
| 冷启动 | 独立 profile 保留 provider 与 script，并再次成功 | 冷启动后 GUI 显示 `CMP12 JS` 和原 JavaScript；运行 `CMP12_COLD_QUERY` 后显示 `CMP12 desktop success`、`CMP12 desktop item`、`https://fixture.invalid/cmp12`、`Synchronous fetch result` | 18，`gui-desktop-cold-success.png` |

## Helper 调整

临时 helper 以 PID、AX window 和 CGWindowID 交叉定位唯一窗口；每项输入前激活
`NSRunningApplication` 并 AX raise，确认前台 PID。截图使用 `screencapture -o` 去除阴影，
激活后等待 Compose 绘制稳定；点击包含 down/up 间隔和稳定等待；滚动先移动指针后发送
pixel scroll。未修改生产代码。
