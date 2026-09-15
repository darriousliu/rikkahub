# iOS 应用图标移植

日期：2026-09-16（任务开始于 2026-09-15）。

## 资源与接线

- 来源：[原兔子 SVG](../../composeApp/src/commonMain/composeResources/files/icons/rikkahub.svg)。
  保留原路径、填充与描边，使用 Android 启动图标的黑色兔子、白底。
- 输出：[AppIcon.png](../../iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png)，
  1024×1024、8-bit RGB、无透明通道。SVG 画布缩放至 768×768，四周各留 128 像素，圆角交给 iOS。
- 按 [Apple 的 Single Size 配置](https://developer.apple.com/documentation/xcode/configuring-your-app-icon)
  声明 universal / ios / 1024x1024，由 Xcode 为 iPhone、iPad 和系统使用位置生成图标。
- `Assets.xcassets` 加入主应用 Resources；Debug、Release 均指定
  `ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon`。

## 生成方式

在仓库根目录使用 Node.js 和 sharp 0.35.4（librsvg 2.62.91）执行下列代码。
sharp 只用于一次性生成；PNG 已入库，应用构建直接使用 PNG。

```javascript
const fs = require('node:fs');
const sharp = require('sharp');
const source = 'composeApp/src/commonMain/composeResources/files/icons/rikkahub.svg';
const output = 'iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png';
const rabbit = fs.readFileSync(source, 'utf8')
    .replace('width="256" height="256"', 'x="128" y="128" width="768" height="768"');
const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024"'
    + ' viewBox="0 0 1024 1024" color="#000000">'
    + '<rect width="1024" height="1024" fill="#ffffff"/>' + rabbit + '</svg>';
sharp(Buffer.from(svg)).flatten({ background: '#ffffff' }).removeAlpha().toColourspace('srgb')
    .png().toFile(output).catch(error => { console.error(error); process.exitCode = 1; });
```

## 验证

本项需要 GUI 验证：App Icon 涉及资源打包、SpringBoard 注册与系统裁切，静态检查不能覆盖实际桌面显示。
GUI 操作由 `gpt-5.6-terra` 子 agent 执行。

| 检查 | 预期 | 实际覆盖 |
|---|---|---|
| PNG、JSON、Xcode 工程 | 1024×1024 RGB 无透明；图标文件存在；两种配置与 Resources 接线正确 | Pillow/JSON/plutil 断言通过，四角为不透明白色 |
| 原图对照 | 原兔子形状、黑白配色与留白适合系统图标 | 查看原 Android PNG 和生成 PNG，符合预期 |
| 资源编译 | iPhone、iPad 均生成图标资源与 Info.plist 图标字段 | `actool` 对 iphonesimulator、iphoneos 编译成功，最低版本均为 15.0 |
| iPhone 应用构建 | 完整构建、安装并启动成功 | XcodeBuildMCP `build_run_sim`，scheme `iosApp`，Debug，iPhone 17 Pro Max / iOS 27.0，通过 |
| iPhone 安装包 | Info.plist 指向 AppIcon，PNG 与 car 资源确实进入 `.app` | 两种设备族的图标字段存在；120/152 PNG 存在；car 内 phone 图标为 1024×1024 且不透明 |
| iPad 应用构建 | 针对 iPad 完整构建、安装并启动成功 | 切换目的地为 iPad Pro 11-inch (M5) / iOS 27.0，再执行 `build_run_sim`，通过 |
| iPad 安装包 | iPad 构建保留对应设备族图标 | Info.plist 选择 AppIcon；car 内 pad 图标为 1024×1024 且不透明 |

资源编译使用 `--target-device iphone --target-device ipad --app-icon AppIcon`。
两种平台均输出 `AppIcon60x60@2x.png`（120×120）、`AppIcon76x76@2x~ipad.png`（152×152）与 `Assets.car`；
`assetutil --info` 确认 car 含 phone/pad 的 1024×1024 不透明 AppIcon。
生成的 `CFBundleIcons` 与 `CFBundleIcons~ipad` 均选择 AppIcon。
独立 `actool` 的沙箱运行有 CoreSimulator 服务连接日志，但资源编译退出码为 0。

Xcode 针对选定模拟器的设备族裁剪 car；iPhone 定向构建仅包含 phone 条目，符合目标设备过滤行为。
未裁剪的两平台 `actool` 检查覆盖 phone/pad 两种条目。

### GUI 步骤与证据

1. 使用 `session_show_defaults` 检查工具配置，`discover_projs` 定位 `iosApp/iosApp.xcodeproj`。
2. `session_set_defaults` 选择工程、scheme `iosApp`、Debug、对应模拟器，以及 `iosApp/DerivedData`。
3. `build_run_sim` 构建、安装并启动；iPhone 用时 129.1 秒，iPad 用时 48.5 秒，均成功。
4. 按 Home 回到 SpringBoard，核对 RikkaHub 图标形状、配色、裁切。
5. 根据运行时 UI 快照点击 RikkaHub 图标，核对应用进入新聊天界面。

| 设备 | 预期 | 实际结果与证据 |
|---|---|---|
| iPhone 17 Pro Max / iOS 27.0 | 白底黑色兔子，系统圆角，桌面可启动 | 通过：[桌面](evidence/ios-app-icon-2026-09-16/iphone-home.jpg)、[点击后](evidence/ios-app-icon-2026-09-16/iphone-app-launched.jpg)；进入既有 GUI 回归助手的新聊天界面 |
| iPad Pro 11-inch (M5) / iOS 27.0 | 同一兔子图标，桌面可启动 | 通过：[桌面](evidence/ios-app-icon-2026-09-16/ipad-home.jpg)、[点击后](evidence/ios-app-icon-2026-09-16/ipad-app-launched.jpg)；进入默认助手的新聊天界面 |

复用现有模拟器，保留应用数据；本次未发起模型请求。构建存在已有 Kotlin/Swift 警告，无构建错误。
生成与编译的临时文件清理后，保留以上证据截图。

覆盖范围：GUI 验证为 iOS 27.0 模拟器标准图标；真机、旧版 iOS 运行时、Release Archive 与 App Store 上传未验证。
本次只调整静态资源和 Xcode 接线，使用资源/安装包断言、实际构建与 GUI 验证，未新增业务单元测试。
