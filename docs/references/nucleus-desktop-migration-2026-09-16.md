# Nucleus 桌面接入与体积审计（2026-09-16）

## 范围

Windows x64 使用 NSIS，macOS arm64/x64 使用 DMG。框架固定为 Nucleus 2.5.15，WebView 固定为同作者
`dev.nucleusframework:composewebview:1.0.3`。本机为 macOS arm64；Windows 和 macOS x64 已配置手动 CI 矩阵，
本轮未运行这两个目标的 CI 构建或安装验证。

## 大包原因

旧 `main/app/RikkaHub.app` 按文件内容统计为 702,352,101 bytes（669.8 MiB），共 268 个 JAR。
SHA-256 检查未发现字节完全相同的重复 JAR，主要膨胀来自依赖的全平台发行方式：

```text
composeApp → KScan-jvm 0.9.2
  ├─ javacv 1.5.13
  ├─ opencv-platform 4.13.0-1.5.13
  │    ├─ Android / iOS / Windows / Linux / macOS 各架构二进制
  │    └─ openblas-platform → javacpp-platform → 各平台二进制
  └─ material-icons-extended-desktop 1.7.3
```

| 项目 | 旧包内字节 | MiB |
| --- | ---: | ---: |
| JavaCV/OpenCV/OpenBLAS/JavaCPP | 428,781,310 | 408.9 |
| material-icons-extended | 37,946,773 | 36.2 |

`ScannerView` 只由 `mobileMain/KScanQrScanner.kt` 使用。桌面的 `rememberPlatformQrScanner()` 返回 null，
`JvmQrImageDecoder` 只调用 KScan 的 ZXing/ImageIO 图片解码路径。仓库源码没有直接使用 Material 扩展图标。
因此在 composeApp 的 JVM 配置中排除上述传递依赖，Android/iOS 不应用这些排除。
新增测试生成真实二维码 PNG，在确认 OpenCV、扩展图标类不在类路径后仍能准确解码；无效图片仍返回原有失败结果。

## 打包与线程适配

- Nucleus 管理窗口和打包；入口显式指定 Tao，macOS 带 `-XstartOnFirstThread`。
- 保留 Tao `MainDispatcherFactory` 及生命周期反射入口。
- WebView 使用库的正常原生工厂，首次加载前安装平台设置/console hook，进度条使用新 overlay 槽。
  GUI 曾发现 `LocalWebViewFactory` 非空时 1.0.3 会隐藏原生视图（测试分支）；生产接线已移除该 Local。
  Android 兼容层复制原库 onPageStarted 的状态更新，省略原先已过滤的 viewport/selection CSS 注入；
  其余事件仍委托原库 client。三个 internal setter 的调用固定适配 1.0.3，升级时需要复查。
  HTML 参数、JS bridge、console、取消和页面业务逻辑沿用原实现。
- Windows/macOS 剪贴板仍沿用 Compose 的 AWT Transferable 接线；FileKit 使用原生文件选择器。
  Windows 音频已有专门的 JavaFX 线程切换，macOS 使用 AVAudioPlayer。
- 离屏导出在最后一帧前显式通知 Snapshot 观察者，解决 Tao 与 AWT 状态通知时序不同导致的旧帧问题。
  `ChatImageExportTest.waitsForWebViewSnapshotRegisteredAfterMeasurement` 曾捕获红色旧帧，修复后得到预期绿色新帧。
- 图标沿用 Android 兔子标记，生成 ICO/ICNS/窗口 PNG；源图和可重复运行的编码工具位于 `desktopApp/icons`、`desktopApp/tools`。
- Material 3 的标题文字显式使用 Nucleus 标题栏前景色，避免深色窗口中默认黑字对比不足。

## 裁剪边界

Release 启用 ProGuard shrinking、optimization、obfuscation；JAR 分开输出，保留必要的 JNI/JNA、Room、
序列化、JavaFX 和服务发现规则。交互启动曾捕获 Ktor engine provider 被裁剪；现从运行时 JAR 的
META-INF/services 自动生成 provider 与 service 名称保留规则，也覆盖 Coil/ImageIO 等动态注册入口。
ProGuard 7.9.1 的返回类型特化曾将 Okio `buffer(Source)` 的返回类型错误收窄，导致 `VerifyError`；
配置只禁用 `method/specialization/returntype`，其余优化、裁剪和混淆继续启用。
Temurin 21 避免将本机 JBR/JCEF 的额外模块作为打包运行时。
Nucleus 清理目标平台不需要的动态库；jlink 关闭全模块打包；安装包使用 Maximum 压缩。

不能把同名前缀的所有 JAR 当作重复文件删除，例如 JavaFX base/graphics/media、Compose UI/runtime/foundation
分别提供不同功能。字体、分词字典、Mermaid 和 SQLite 扩展仍在使用。
Nucleus 2.5.15 的清理器只处理动态库后缀，JNA 内两个 AIX 静态归档仍保留（压缩后共 323,493 bytes）；
它们不是 OpenCV/OpenBLAS 的全平台包，不参与本机加载。本轮未改写框架内部的清理器。

## 迁移提交的构建记录

代码回归已通过 17 项：二维码图片解码 2、HTML 预览 3、WebView 内容缓存 4、聊天图片导出 4、剪贴板内容处理 4。
导出测试同时补齐了已有主题接线需要的 DataStore、AppScope、BooleanPreferenceStore 测试依赖。
Android 与 iOS simulator 编译通过。

最终 macOS arm64 Release DMG 构建通过，Gradle 配置缓存复用成功。对包内类运行 `-Xverify:all` 和反射方法检查：
28,773 个类通过，0 个 `VerifyError`。另有 7 个类引用未安装的可选 GraalVM、Hot Reload、PDF 签名、日志、
Conscrypt 类型，不属于已启用的发行功能；这项检查不初始化所有类，不能代替 GUI 和功能验证。
包内 16 个 ServiceLoader 描述及 30 个注册实现可以加载，ImageIO PNG 编码成功。
`hdiutil verify` 校验 DMG 通过。

以上结果对应迁移提交时的构建。后续运行行为由用户手动验证。

## 最终包体积

以下统计文件内容字节数，MiB 按 1,048,576 bytes 计算；文件系统分配空间和 Finder 显示值可能不同。

| 产物 | 字节 | MiB |
| --- | ---: | ---: |
| 旧开发 `.app` | 702,352,101 | 669.8 |
| 清理依赖后的开发 `.app` | 214,718,475 | 204.8 |
| 最终优化 `.app` | 177,829,781 | 169.6 |
| 最终 DMG | 98,859,895 | 94.3 |

最终 `.app` 比旧包减少 **74.68%**；其中打包的 Temurin 运行时占 84,966,129 bytes（81.0 MiB）。
230 个 JAR 中没有字节相同的重复 JAR、没有重复类定义，也没有 OpenCV/OpenBLAS/JavaCV/JavaCPP 或
`material-icons-extended` JAR。上文列出的两个小型 JNA AIX 静态归档仍在其 JAR 内。

产物：`desktopApp/build/compose/binaries/main-release/dmg/rikkahub-2.4.5-mac-arm64.dmg`。
SHA-256：`f49527517e7ccc11fcc94753ba718d760993e635c4c66af7422645e06258b4a8`。

## 官方依据

- [Nucleus 安装与版本要求](https://nucleusframework.dev/en/docs/start/install/)
- [Nucleus Gradle DSL](https://nucleusframework.dev/en/docs/reference/gradle-dsl/)
- [Tao 迁移说明](https://nucleusframework.dev/en/docs/tao/migration-from-jbr/)
- [WebView 安装与 API](https://nucleusframework.dev/en/docs/webview/)
