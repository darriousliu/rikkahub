# Mermaid 原生渲染模块

本模块提供 Android、桌面 JVM、iOS 共用的 `renderMermaidSvg(source, configJson = "{}")` 接口。Rust 负责解析和布局，
返回 `resvg-safe` SVG；Android/JVM 通过 JNA 调用同一个 C ABI，iOS 通过 cinterop 调用。
聊天 Mermaid 代码块已通过本模块生成 SVG，再由 [coil-resvg](https://github.com/hash-sequence/coil-resvg)
1.1.2 解码；SVG 代码块共用原生图片预览。HTML 仅在 Windows 关闭内嵌 WebView，保留全屏网页预览。

## 上游版本与 feature

`merman/` 是 [Latias94/merman](https://github.com/Latias94/merman) 的 Git submodule，固定在
`v0.8.0-alpha.6`，提交 `d529f858ea3d337a1bdc8fe12e44e1403ededf2e`。

```bash
git submodule update --init --recursive mermaid/merman
```

`native/Cargo.toml` 直接使用 merman Rust crate 的默认 feature，不覆盖 `default-features` 或指定额外
feature。alpha.6 的 `default = ["complete-svg"]` 包含 `svg`、`layout-cytoscape` 和 `math`，
因此公式排版默认启用。未额外开启 `analysis`、`ascii`、`layout-elk` 或 PNG/PDF 导出能力。

脚本校验 submodule 提交、使用 Rust 默认 feature 的依赖声明和 `native-distribution` 优化配置，采用上游固定的 Rust
`1.95.0`，使用本模块的 `native/Cargo.lock` 和 `--locked` 构建。升级时需一起更新 submodule、
`native/Cargo.toml` 的版本元数据与锁文件，并重新验证各平台。

## 环境准备

- 通用：Git、Rustup、Python 3.11+。脚本自动查找 Python，也可设置 `MERMAN_PYTHON`。
- Android：Android SDK 和上游固定的 NDK `29.0.14206865`。支持 `ANDROID_HOME`、
  `ANDROID_SDK_ROOT`、仓库根目录 `local.properties` 的 `sdk.dir`，或直接指定 `ANDROID_NDK_HOME`。
  编译使用 API 26，与模块的 minSdk 一致。
- iOS/macOS：macOS 和 Xcode；iOS 最低部署版本 15.0，macOS 默认 11.0。
- Linux/Windows 交叉编译：需要 `cargo-zigbuild` 和 Zig。Windows 使用 GNU 目标；
  Windows 本机使用 MSVC Rust toolchain 时也需这两个工具。
- `--package` 还需要仓库现有的 JDK/Gradle/Android SDK 环境。iOS Gradle 打包必须在 macOS 执行。

```bash
rustup toolchain install 1.95.0 --profile minimal
cargo install cargo-zigbuild --locked  # 仅交叉编译需要；另行安装 Zig
```

## 构建与打包

以下命令均从仓库根目录执行；脚本本身也支持从任意工作目录调用。省略 `--package` 时只构建、整理 Rust 产物。

```bash
# Android：默认 arm64-v8a 与 x86_64，并打包 AAR
./mermaid/prepare-android-rust.sh --package
./mermaid/prepare-android-rust.sh arm64-v8a

# iOS：默认设备 arm64 与模拟器 arm64，并生成两个独立静态 Framework
./mermaid/prepare-ios-rust.sh --package
./mermaid/prepare-ios-rust.sh ios_simulator_arm64

# 桌面：默认当前主机，或显式选择目标
./mermaid/prepare-jvm-rust.sh --package
./mermaid/prepare-jvm-rust.sh windows-x86-64 --package
./mermaid/prepare-jvm-rust.sh linux-x86-64 linux-aarch64
./mermaid/prepare-jvm-rust.sh --all --package

# 只检查固定版本、feature 与目标映射，不编译、不下载工具
./mermaid/prepare-android-rust.sh --check
./mermaid/prepare-ios-rust.sh --check
./mermaid/prepare-jvm-rust.sh --all --check
```

每个脚本支持 `--help`，也接受对应 Rust target triple。`--all` 在 macOS 选择全部桌面目标，
在 Linux/Windows 选择两个 Linux 目标与 Windows x64。iOS 不提供 x86_64 模拟器，Windows 不提供 ARM64。

Windows 可在 Git Bash 中运行 `.sh`，或直接在 PowerShell 调用 Python：

```powershell
python mermaid/scripts/build_native.py jvm --package
python mermaid/scripts/build_native.py android --package
```

| 平台 | Rust 产物目录（相对 `mermaid/`） | Gradle 产物 |
| --- | --- | --- |
| Android | `build/native/android/{arm64-v8a,x86_64}/librikkahub_mermaid.so` | `build/outputs/aar/` |
| macOS | `build/native/jvm/{darwin-aarch64,darwin-x86-64}/librikkahub_mermaid.dylib` | `build/libs/mermaid-jvm.jar` |
| Linux | `build/native/jvm/{linux-aarch64,linux-x86-64}/librikkahub_mermaid.so` | 同一个 JVM JAR |
| Windows | `build/native/jvm/win32-x86-64/rikkahub_mermaid.dll` | 同一个 JVM JAR |
| iOS | `build/native/ios/{ios_arm64,ios_simulator_arm64}/librikkahub_mermaid.a` | `build/bin/{iosArm64,iosSimulatorArm64}/releaseFramework/Mermaid.framework` |

JVM JAR 会包含 `build/native/jvm/` 下已准备好的所有平台库，JNA 按运行平台自动加载。
`windows-x86-64` 是命令别名，实际资源前缀是 JNA 所需的 `win32-x86-64`。
如果需要严格限定发布包的平台，先运行 `./gradlew :mermaid:clean` 再构建所需目标。

Gradle 构建会自动调用对应准备脚本。也可直接执行：

```bash
./gradlew :mermaid:assembleAndroidMain
./gradlew :mermaid:jvmJar
./gradlew :mermaid:linkReleaseFrameworkIosSimulatorArm64
```

`-Pmermaid.android.targets=arm64-v8a,x86_64` 与 `-Pmermaid.jvm.targets=darwin-aarch64,windows-x86-64`
可指定 Gradle 要准备的目标；`--package` 自动传递本次选择。Rust 增量缓存和所有二进制产物均位于
`mermaid/build/`，不会修改 submodule 或提交二进制文件。多个平台构建共用 Cargo 缓存，Cargo 会串行处理同一缓存的构建。

## 调用与聊天接入

```kotlin
// 调用模块的 commonMain.dependencies 中添加：implementation(project(":mermaid"))
val svg: String? = renderMermaidSvg("flowchart LR\n A[开始] --> B[完成]")
```

接口是同步 CPU 操作，应放在后台线程执行。解析或渲染失败抛出 `MermaidRenderException`，
包括当前上游对空输入的报错；上游返回无图结果时透传为 `null`。
成功时返回 SVG 文本。可选 `configJson` 提供 site-level 配置（例如主题颜色），无需改写源文本或前置 YAML。
桌面与 Android CI 已补充固定 Rust 工具链；Windows 另安装 cargo-zigbuild 0.22.2 与 Zig 0.16.0，
Android 在 Gradle 启动前安装 NDK 29.0.14206865。

原生返回缓冲区由平台适配层在 `finally` 中释放。聊天层通过 Coil Fetcher 在后台执行本接口，
缓存键包含源码与主题，完成代码围栏后才预览。源码切换、缩放与 PNG 导出均使用原生图片；
聊天图片导出会等待图表渲染完成。应用图标等其他 SVG 请求保持原有解码配置。

## 验证

```bash
python3 -m unittest discover -s mermaid/scripts -p 'test_*.py'
CARGO_TARGET_DIR="$PWD/mermaid/build/cargo" cargo +1.95.0 test \
  --locked --manifest-path mermaid/native/Cargo.toml --profile native-distribution \
  --target aarch64-apple-darwin
./gradlew :mermaid:jvmTest
./gradlew :mermaid:iosSimulatorArm64Test
./gradlew :mermaid:connectedAndroidDeviceTest
```

Rust/JVM/iOS 测试覆盖真实渲染、公式排版、中文和 HTML 标签的 resvg 兼容输出、主题配置、空输入，以及错误后继续渲染。
Android 相同测试放在设备测试 source set，避免 JVM host test 误加载 Android `.so`。
聊天接入后，Rust C ABI、JVM、iOS 模拟器和 Android 设备测试均为 5 项通过；聊天层另有 10 项 JVM 回归和
1 项 Android GUI 测试通过。macOS 已实际保存并检查 PNG。GUI 证据、构建结果及未覆盖项见
[聊天原生图表验证记录](../docs/references/chat-native-diagram-rendering-validation-2026-09-16.md)。

2026-09-16 聊天接入前、使用 Rust crate 默认 feature 的本机验证记录（macOS arm64）：

| 检查 | 结果与覆盖范围 |
| --- | --- |
| 脚本契约与目标映射 | 5 个 Python 测试通过；三个入口的 `--check`、`--help` 和 Shell 语法检查通过 |
| feature 解析 | merman 启用 `default, complete-svg, svg, layout-cytoscape, math`，merman-render 启用 `layout-cytoscape, math` |
| Rust C ABI | 4 个测试通过，覆盖公式、中文 SVG、空/非法输入、非法 UTF-8 和结果释放 |
| JVM | 4 个测试通过；另从打包 JAR 中加载 dylib，确认默认 math 能力可输出公式 SVG 路径 |
| Android | arm64-v8a、x86_64 编译及 AAR 打包通过；在 16 KB 页大小的 Android 17 arm64 模拟器上 4 个测试通过，包含公式用例 |
| iOS | arm64 设备/模拟器静态库与 Release Framework 均成功；iOS 27 模拟器上 4 个测试通过，包含公式用例，两个 Framework 最低系统均为 15.0 |
| Windows/Linux | macOS 上交叉编译 Windows x64 DLL 与 Linux x64 SO 成功；Windows DLL 导入表仅包含系统 DLL |
| KMP 元数据 | `allMetadataJar` 与 `compileIosMainKotlinMetadata` 通过，iOS cinterop commonization 仅在本模块启用 |

未覆盖 Windows/Linux 本机运行、macOS x64 与 Linux arm64 实际编译、iOS 真机运行，
当时尚未覆盖聊天 UI/coil-resvg 显示效果。Android AAR 已检查两个 ABI 的 `.so` 和 JNA 接口混淆保留规则；
JVM JAR 已检查按 JNA 平台前缀组织的原生库资源。
