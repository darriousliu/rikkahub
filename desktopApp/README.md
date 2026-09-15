# Desktop

桌面发行目标为 Windows x64（NSIS）和 macOS arm64/x64（DMG）。使用 Nucleus 2.5.15、Tao 窗口和
`dev.nucleusframework:composewebview:1.0.3`，运行时使用 Temurin 21。

## 构建

从仓库根目录执行，首次构建需要联网下载 Gradle、JDK 和依赖；Web 前端需要 pnpm。

```sh
pnpm --dir web-ui install --frozen-lockfile
./gradlew :desktopApp:run
./gradlew :desktopApp:packageReleaseDistributionForCurrentOS
```

`packageReleaseDmg` 在 macOS 构建 DMG；`packageReleaseNsis` 在 Windows 构建安装 EXE。两种架构的
macOS 安装包分别在对应架构的主机上构建。手动 GitHub Actions 工作流 `Desktop Build` 配置了这三个构建目标。
Windows 的内嵌网页需要系统已安装 [WebView2 Runtime](https://developer.microsoft.com/microsoft-edge/webview2/)；
安装包没有捆绑完整浏览器运行时。

产物位于 `desktopApp/build/compose/binaries/main-release/`，混淆映射位于
`desktopApp/build/reports/proguard/`。发布时应一并保留 mapping，便于恢复崩溃堆栈。
`createDistributable` 生成的 `main/app` 是未混淆开发包；分发使用 `main-release` 产物。
正式签名、公证证书由发布环境另行配置。

## 体积控制

- 桌面 JVM 配置排除 KScan 传递引入的 `org.bytedeco` 和 `material-icons-extended`。
  桌面只使用 ZXing/ImageIO 解码二维码图片，不使用 KScan 的 JavaCV 摄像头界面；Android/iOS 仍保留原依赖。
- Release 启用 ProGuard 裁剪、优化和混淆，保留 JNI、JNA、Room、序列化、JavaFX 及 Tao 主线程所需入口。
- Nucleus 清理不匹配目标平台的动态库，jlink 只包含所需 Java 模块，安装包使用 Maximum 压缩。
- JAR 分开输出以保留资源和服务发现边界，不能仅凭文件名相似删除库。字体、分词字典和 Mermaid 等资源仍是功能依赖。

本机 macOS arm64 优化包约 170 MiB，DMG 约 94 MiB；依赖链和前后统计见
[体积与迁移审计](../docs/references/nucleus-desktop-migration-2026-09-16.md)。

## 主线程与 WebView

`nucleusApplication(backend = NucleusBackend.Tao)` 管理 UI 事件循环，macOS 启用 `-XstartOnFirstThread`。
`Dispatchers.Main` 由 Tao 提供。不要把窗口、导航和生命周期逻辑切回 Swing。
Windows/macOS 的 Compose 剪贴板仍使用 AWT 的 Transferable；文件选择使用 FileKit 原生对话框。
Windows 音频后端显式通过 `Platform.runLater` 切到 JavaFX 线程，macOS 音频沿用 AVAudioPlayer。

WebView 使用正常的原生视图工厂，在首次加载前安装平台设置和 console 回调；加载进度放在 overlay 内容槽中，
使它能显示在 Tao 原生 WebView 表面上方。1.0.3 的 `LocalWebViewFactory` 是隐藏原生视图的测试入口，生产代码不要提供它。
Android 保留原来的 viewport/文本选择行为，兼容层需要在升级 WebView 时复查。

## 隔离验证

`--smoke` 打开历史页面、等待帧并检查 `Dispatchers.Main.immediate` 与 Tao 窗口线程一致，随后退出。
隔离 GUI 配置时同时设置两个 JVM 属性（不要更改系统 HOME）：

```sh
JAVA_TOOL_OPTIONS="-Duser.home=/private/tmp/rikkahub-check/home -Drikkahub.dataDir=/private/tmp/rikkahub-check/files" \
  desktopApp/build/compose/binaries/main-release/app/RikkaHub.app/Contents/MacOS/RikkaHub --smoke
```

`user.home` 隔离偏好和崩溃记录，`rikkahub.dataDir` 隔离 FileKit 文件、数据库和缓存；未设置时仍使用原目录。
去掉 `--smoke` 可进行交互回归。测试结束后退出该进程并删除测试目录。

参考：[Nucleus 安装](https://nucleusframework.dev/en/docs/start/install/)、
[Tao 迁移](https://nucleusframework.dev/en/docs/tao/migration-from-jbr/)、
[WebView](https://nucleusframework.dev/en/docs/webview/)。
