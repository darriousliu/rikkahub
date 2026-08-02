# Compose Multiplatform 人工测试矩阵

本表记录无法在当前自动化环境中完成的三端人工验收。`Blocked` 不代表功能失败，也不以编译或单元测试通过代替人工验证；“证据”列同时给出当前已完成的自动化检查和解除阻塞所需条件。

| 编号 | 能力 | 平台 | 无法自动化原因 | 步骤 | 预期 | 结果 | 证据 | 日期 |
|---|---|---|---|---|---|---|---|---|
| M01 | 通知、权限、点击回到会话 | Android | 无 Android 实机/模拟器和通知权限交互环境 | 启动应用，拒绝后再授予通知权限；在前后台触发聊天通知并点击 | 权限流程正确；通知内容正确；点击回到对应会话 | Blocked | Android 编译及单元测试通过；需 Android 13+ 设备复测 | 2026-08-01 |
| M02 | 通知、权限、点击回到会话 | iOS | 无 iOS 实机和系统通知授权环境 | 首次拒绝后在系统设置授权；前后台触发通知并点击 | 授权状态同步；通知送达；点击回到对应会话 | Blocked | iOS simulator 编译/测试通过；需已签名真机和通知权限复测 | 2026-08-01 |
| M03 | SystemTray 通知与点击回到会话 | JVM | 当前会话无桌面托盘和 GUI 交互 | 在支持托盘的桌面启动，触发通知并点击；再在 headless 环境启动 | 托盘通知可点击回到会话；headless 安全 no-op | Blocked | Desktop smoke 通过；需带 SystemTray 的桌面会话复测 | 2026-08-01 |
| M04 | Custom Tabs OAuth | Android | 无浏览器、deep link 和真实 OAuth 凭据 | 发起 OAuth，完成授权；测试取消、state 不匹配和超时 | 回调匹配 state；成功换取 token；取消/异常安全收尾 | Blocked | OAuth 公共状态机与 PKCE 自动测试通过；需 Android 设备及测试 OAuth client | 2026-08-01 |
| M05 | ASWebAuthenticationSession OAuth | iOS | 无签名 iOS 应用和真实 OAuth 凭据 | 发起授权，完成/取消会话；验证 callback URL 与 state | 系统授权会话正确关闭；回调校验后换取 token | Blocked | iOS 编译及 OAuth 公共测试通过；需签名设备及测试 OAuth client | 2026-08-01 |
| M06 | loopback OAuth | JVM | 当前会话不能完成系统浏览器交互，且无真实 OAuth 凭据 | 发起授权，浏览器回调随机本地端口；测试取消、超时和端口释放 | 仅接受正确 state；完成后关闭 server 并释放端口 | Blocked | RFC 7636、授权 URL、state 和 discovery 临时迁移测试通过且已删除；需桌面浏览器及测试 OAuth client | 2026-08-01 |
| M07 | 相机扫码和权限拒绝 | Android | 无带相机的 Android 设备 | 扫描普通、Unicode、URL、低对比度和旋转二维码；拒绝相机权限 | 可识别有效码；损坏码不误报；拒绝权限有明确反馈 | Blocked | 扫码相关源集编译通过；需带相机 Android 设备与固定图片集 | 2026-08-01 |
| M08 | 相机扫码和权限拒绝 | iOS | 无带相机的 iOS 设备 | 扫描普通、Unicode、URL、低对比度和旋转二维码；拒绝相机权限 | 可识别有效码；损坏码不误报；拒绝权限有明确反馈 | Blocked | iOS 扫码源集编译通过；需带相机 iOS 设备与固定图片集 | 2026-08-01 |
| M09 | Media3 播放、暂停、耳机切换 | Android | 无音频输出和耳机路由设备 | 播放 TTS，暂停/继续、调速并插拔耳机 | 状态与进度准确；音频路由切换正常；释放后停止 | Blocked | Android 音频实现编译通过；需 Android 设备、扬声器和耳机 | 2026-08-01 |
| M10 | AVFoundation 播放、暂停、耳机切换 | iOS | 无 iOS 音频设备 | 播放 TTS，暂停/继续、调速并切换耳机/扬声器 | 状态与进度准确；路由切换正常；后台/中断可恢复 | Blocked | iOS 音频实现编译通过；需 iOS 真机和耳机 | 2026-08-01 |
| M11 | JavaFX Media 播放、暂停、耳机切换 | JVM | 当前会话无桌面音频输出和 GUI | 播放 TTS，暂停/继续、调速并切换输出设备 | 状态与进度准确；设备切换与释放正常 | Blocked | Desktop 编译及 smoke 通过；需 JDK 21 图形桌面和音频设备 | 2026-08-01 |
| M12 | uCrop 图片裁剪 | Android | 无 Android 图形交互环境 | 选择图片，执行裁剪、旋转、取消和重试 | 返回正确裁剪图；取消不覆盖原图；临时文件被清理 | Blocked | Android 裁剪实现编译通过；需 Android 设备手工操作 | 2026-08-01 |
| M13 | FloatingX 悬浮窗 | Android | 无悬浮窗权限和系统窗口环境 | 拒绝/授予悬浮窗权限，显示、拖动、关闭 TTS 悬浮窗 | 权限流程正确；窗口可操作；关闭后资源释放 | Blocked | Android 悬浮窗实现编译通过；需支持悬浮窗的 Android 设备 | 2026-08-01 |
| M14 | Firebase 事件与崩溃 | Android | 仓库不含 Firebase 配置，且无控制台访问 | 配置测试项目，发送事件并触发非致命/测试崩溃 | 控制台收到事件和异常；缺配置时应用仍可启动 | Blocked | 无密钥配置下 Android 构建路径可用；需测试 Firebase 项目与控制台权限 | 2026-08-01 |
| M15 | Firebase 事件与崩溃 | iOS | 无 GoogleService-Info.plist、签名和控制台访问 | 配置测试项目，发送事件并触发非致命/测试崩溃 | 控制台收到事件和异常；缺配置时 adapter 禁用且不崩溃 | Blocked | iOS 无配置编译路径通过；需签名应用、测试 Firebase 项目与控制台权限 | 2026-08-01 |
| M16 | Sentry breadcrumb 与 exception | JVM | 无测试 DSN 和 Sentry 控制台访问 | 配置测试 DSN，记录事件并抛出测试异常 | 事件映射为 breadcrumb；异常被 capture 且信息完整 | Blocked | JVM Sentry adapter 编译通过；需测试 DSN 和控制台权限 | 2026-08-01 |
| M17 | JmDNS 发现与注销 | Android | 无真实局域网和第二台设备 | 发布服务，由另一设备发现；停止后确认注销 | 服务可发现且元数据正确；停止后不再出现 | Blocked | Android mDNS 实现编译通过；需同网段 Android 与发现端 | 2026-08-01 |
| M18 | Bonjour 发现与注销 | iOS | 无 iOS 真机、Bonjour entitlement 和局域网对端 | 授予本地网络权限，发布/发现服务并停止 | 权限提示正确；跨设备可发现；停止后注销 | Blocked | iOS Bonjour 实现编译通过；需签名真机、entitlement 与同网段对端 | 2026-08-01 |
| M19 | JmDNS 发现与注销 | JVM | 当前环境无可控局域网对端 | 发布服务，由另一设备发现；停止后确认注销 | 服务可发现且元数据正确；停止后不再出现 | Blocked | JVM mDNS 实现编译通过；需同网段桌面与发现端 | 2026-08-01 |
| M20 | FileKit 文件选择、权限失效、沙箱复制 | Android | 无系统文件选择器和可撤销 URI 权限环境 | 选择文件并导入；撤销权限后重开；检查沙箱副本 | 选中即复制入沙箱；权限失效不影响已导入副本 | Blocked | Android FileKit/备份编译测试通过；需 Android 文件提供器复测 | 2026-08-01 |
| M21 | FileKit 文件选择、权限失效、沙箱复制 | iOS | 无 UIDocumentPicker 和安全作用域资源环境 | 从 Files/iCloud 选择文件，结束授权后重开 | 文件复制入沙箱；安全作用域正确结束；可再次读取 | Blocked | iOS FileKit/备份编译测试通过；需签名 iOS 应用与 Files/iCloud | 2026-08-01 |
| M22 | FileKit 文件选择、权限失效、沙箱复制 | JVM | 当前会话无原生文件选择器 GUI | 选择本地/外接盘文件，移动源文件后重开导入项 | 导入时复制入应用目录；源文件失效不影响副本 | Blocked | JVM FileKit/备份测试通过；需图形桌面和可移动介质 | 2026-08-01 |
| M23 | GIF 首帧与解码回退 | Android/iOS/JVM | 当前会话无法比较三端真实渲染 | 打开正常、损坏和超大 GIF，比较 Android 动画与 iOS/JVM 首帧 | Android 正常播放；iOS/JVM 稳定显示首帧；损坏输入不崩溃 | Blocked | 三端图片管线编译通过；需三端 GUI 与固定 GIF 资产 | 2026-08-01 |
| M24 | RaTeX 视觉与长公式 | Android/iOS/JVM | 无三端截图和视觉基准环境 | 展示行内、块级、长公式、非法公式并缩放窗口 | 尺寸、换行和 fallback 可读且三端一致 | Blocked | RaTeX 与公式拆分自动测试通过；需三端 GUI 截图复核 | 2026-08-01 |
| M25 | Haze/image-viewer 视觉与手势 | Android/iOS/JVM | 无触控/鼠标 GUI 交互环境 | 打开图片，缩放、平移、双击、关闭并观察 Haze 效果 | 手势无冲突；图片清晰；遮罩/模糊符合预期 | Blocked | 三端 UI 编译及 Desktop smoke 通过；需三端 GUI 手工操作 | 2026-08-01 |
| M26 | Web Server 与前台服务 | Android | 无设备、前台服务通知和局域网访问 | 启停服务器，从同网段客户端访问；杀后台后检查状态 | 前台通知正确；路由可用；停止幂等且端口释放 | Blocked | Android web controller/server 自动测试通过；需 Android 设备及局域网客户端 | 2026-08-01 |
| M27 | Web Server 与防火墙 | JVM | 当前环境不能验证桌面防火墙和外部 LAN 访问 | 启停服务器，从本机和同网段设备访问；测试防火墙提示 | 路由可用；防火墙行为明确；停止后端口释放 | Blocked | Controller/server 测试及 Desktop smoke 通过；需 GUI 桌面、防火墙和局域网客户端 | 2026-08-01 |
| M28 | Termux terminal | Android | 无 Termux 环境、PTY 和输入法交互 | 打开 terminal，输入命令、调整窗口、退出并重进 | PTY 输入输出正常；窗口调整正确；退出释放会话 | Blocked | Android-only 路由与依赖编译通过；需安装 Termux 组件的 Android 设备 | 2026-08-01 |
| M29 | 大型 ZIP 流式导入导出 | Android/iOS/JVM | 当前环境未提供足够大的真实备份和三端存储监测 | 使用数 GB 备份导出、导入并监测峰值内存和进度 | 不整包驻留内存；进度推进；结果可互读且数据完整 | Blocked | 1 MiB 流式跨平台临时迁移测试通过且已删除；需大数据集和三端存储监测 | 2026-08-01 |
| M30 | ZIP Unicode 文件名 | Android/iOS/JVM | 无三端真实文件系统与分享链路 | 导入导出含中日韩、emoji、组合字符和长文件名的备份 | 文件名无乱码；三端互读；路径校验不误伤合法名称 | Blocked | Unicode/Zip Slip/symlink 临时迁移测试通过且已删除；需三端文件分享复测 | 2026-08-01 |
| M31 | 低存储空间下 ZIP 失败恢复 | Android/iOS/JVM | 无可控低存储/配额环境 | 将可用空间降至不足，执行导入导出后恢复空间重试 | 明确报错；不留下有效但不完整的备份；临时文件可清理 | Blocked | 错误路径编译通过；需三端可控磁盘配额或低存储设备 | 2026-08-01 |
| M32 | 完整核心聊天流程 | Android | 无 Android GUI、provider 凭据和真实附件/搜索环境 | 恢复设置，新建 Assistant/provider/会话，流式聊天并验证搜索、富文本、附件、历史、收藏、统计，重启 | 全流程可用；工具调用可继续生成；重启后状态一致 | Blocked | Android 单元测试/编译链路通过；需 Android 设备和隔离测试 provider | 2026-08-01 |
| M33 | 完整核心聊天流程 | iOS | 无签名 iOS 应用、GUI 和 provider 凭据 | 恢复设置，新建 Assistant/provider/会话，流式聊天并验证搜索、富文本、附件、历史、收藏、统计，重启 | 全流程可用；不支持能力明确隐藏/降级；重启后状态一致 | Blocked | iOS simulator 编译/测试通过；需签名设备和隔离测试 provider | 2026-08-01 |
| M34 | 完整核心聊天流程 | JVM | 当前会话不能进行桌面 GUI 操作，且无 provider 凭据 | 恢复设置，新建 Assistant/provider/会话，流式聊天并验证搜索、富文本、附件、历史、收藏、统计，重启 | 全流程可用；不支持能力明确隐藏/降级；重启后状态一致 | Blocked | Desktop smoke 通过但未执行真实 provider 聊天；需 JDK 21 GUI 和隔离测试 provider | 2026-08-01 |
