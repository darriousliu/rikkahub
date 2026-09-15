# ComposeWebView 1.0.3 移动端 GUI 验证

日期：2026-09-16

## 实际完成

Android 使用隔离包 `me.rerere.rikkahub.mobilegui.debug` 完成离线 Mermaid 预览路径验证，未读取聊天、模型配置或凭据，也未覆盖用户原有的 `me.rerere.rikkahub.debug`。

| 检查项 | 实际结果 | 证据 |
| --- | --- | --- |
| 离线 HTML / Mermaid | Debug Mode 中内置 `mermaid.min.js` 成功渲染 | `android-23-fixed-debug.png` |
| 全屏 WebView 预览与加载结束 | `about:blank` Mermaid 页面可见，未持续显示加载指示器 | `android-25-fixed-webview-retry.png` |
| WebView 菜单 | 可打开 Browser 与 Console Logs 操作 | `android-26-fixed-menu.png` |
| 返回 | 从 WebView 使用系统返回键回到 Debug Mode | `android-29-fixed-back-final.png` |

Android 的 Console Logs 面板可打开，但 Mermaid fixture 没有输出 `console.log`；非空 JS console 回调和 JS bridge 未验证。

## iOS

此前已使用隔离 bundle `me.rerere.rikkahub.ios.mobilegui` 在 iPhone 17 Pro Max Simulator 构建、安装并启动，初始界面证据为 `ios-02-launch-settled.png`。未进入 WebView 预览路径，因此 iOS 的 HTML 可见性、console、加载状态和返回行为均未验证。

## 停止说明

后续移动端 GUI 验证按用户要求停止。本轮未提交，也未修改生产业务源码。
