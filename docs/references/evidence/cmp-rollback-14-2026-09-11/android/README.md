# CMP 最小回退第 14 项：Android GUI 验证

日期：2026-09-11。设备为 `Pixel_10_Pro_XL`（`emulator-5554`，arm64）。全程用 ADB 和 Android 系统文件选择器操作；GUI 子 agent 未改动生产、测试或构建文件，提交由主 agent 统一完成。

## 构建与素材

最终验证包为 `app-arm64-v8a-debug.apk`，SHA-256 为
`17ecc9c4fe25c3005cfa1753667a3da7b7efb97fcbdc0615a9cf4c55d886c4af`。

固定非敏感素材及其 SHA-256：

| 素材 | 字节 | SHA-256 |
| --- | ---: | --- |
| 小 PNG | 179 | `bfaa79bbba10c4659c25d505b3813ccb18689923aa593b0bb8f66dc794dc48a5` |
| TXT | 95 | `bd8863259c949cb28657ffc08036500facd91d05f800030ab57fb2cac6fedcb1` |
| 头像裁剪输入 PNG | 1443 | `c8aa66c937da2960675add7b7c670c0e58f6294ebfd754241502f6b35426ecbc` |

## 操作、预期与实际

| 步骤 | 预期 | 实际 |
| --- | --- | --- |
| 真实系统选择器导入 TXT 与 PNG，长按发送 | 本地附件保存，不发模型请求 | DocumentsUI 和系统 Photo Picker 均实际使用；离线用户消息保存成功，见 `47`、`48`。 |
| 用户消息菜单 Fork，删除源会话，冷启动打开 fork | fork 独立可读，附件内容不依赖源会话 | 菜单见 `49`，冷启动打开 fork 见 `58`。只读核验 fork `037e8e0e-8725-45ca-8a88-007c03634b23` 的 PNG 为 `upload/3acede09-f5c1-4a06-9c48-cf86d08d4da0.png`、TXT 为 `upload/dce0b3d4-9afe-4a8f-9f0e-f694dfe264ee.txt`；两者分别与固定 PNG/TXT 哈希一致。 |
| GUI 设置背景 | 背景保存为非空 `upload/UUID.ext` 并跨重启呈现 | URI 为 `file:///data/user/0/me.rerere.rikkahub.debug/files/upload/80afe5bd-b91d-4d57-a70d-f1e57100ff58.png`；179 字节，哈希与固定 PNG 一致，冷启动中保持。 |
| 最终包 GUI 选择、裁剪并保存头像 | 头像保存为非空 `upload/UUID.ext`，跨冷启动呈现，裁剪缓存删除 | 系统裁剪确认见 `87`；保存结果见 `88`，冷启动侧栏见 `90`。URI 为 `file:///data/user/0/me.rerere.rikkahub.debug/files/upload/f7b53ca5-7f6b-453b-afb3-3ab84c16ac93.jpg`；653 字节，SHA-256 `7b884a84581efb51b412e24c1f62b372a297988f5abba3f22e1194caa0e74cc2`。只读检查未发现 `crop_output` 或临时裁剪文件。 |
| 删除本轮数据 | 只删除本轮 fork、助手、上传件与选择器源件 | Assistant Settings 中精确目标 `cmp-rollback-14-android`（`e3fef372-f986-4700-8b35-12f288ff2453`）显示后确认 Delete；删除后只剩默认助手，见 `115`。只读 DB 核验该助手会话数为 0、fork 行数为 0、六个本轮 `managed_files` 行数为 0。六个 `upload/UUID.ext` 和四个 Download/Pictures 选择器源件均逐项确认不存在。 |
| 恢复用户状态 | 默认助手、显示覆盖与 AVD 返回初始状态 | 清理后冷启动显示 `Default Assistant / Auto (RikkaHub)`，见 `117`。自然尺寸清理完成后已恢复 `wm size 2560x1600`（物理尺寸 `1344x2992`），随后停止原先未运行的 AVD。 |

## 版本区分与诊断

附件、fork、背景流程先在冻结包 `c2ebed069ccd9ddf4a244ea5720d84a4cdbcfddd0ff891a8cdf2056e836dea11` 完成。该包的两次头像裁剪确认后生成 0 字节 JPG：`c9aede2e-de45-4d29-93a1-aaa8dc3b05fb.jpg` 和 `6c2e985a-5cde-4e83-87eb-9f5f13edc1c9.jpg`。最终包修复裁剪源文件生命周期后，头像流程按上表通过。私有诊断材料与初始备份在父代理复核后已从 `/private/tmp/cmp-rollback-14/android` 删除；没有凭据写入证据或日志。

## 离线初始状态一致性复核

仅比较既有 `preinstall-state` 与最终只读快照，未启动 AVD，也未输出偏好、助手配置或密钥。布尔结果如下：

| 核对项 | 结果 |
| --- | --- |
| preinstall UUID 集合是最终快照 UUID 集合的子集 | `true` |
| 最终快照相对 preinstall 的新增 UUID 仅为本轮临时助手和两张本轮选择图片 | `true` |
| 去除上述三项本轮 UUID 后，最终快照 UUID 集合与 preinstall 完全相等 | `true` |
| `115` 的两个 `Default Assistant` 均为起点已有助手；删除精确临时助手后助手 ID 集合恢复为初始集合 | `true` |
| 起点与清理后冷启动的选中助手均显示为 `Default Assistant` | `true` |

最后两项由初始/最终快照差集、精确 GUI 删除目标及 `115`、`117` 的已复核画面共同核对。

未覆盖项：未发起模型请求（长按发送走现场离线保存路径）；未测试非本轮用户数据。所有保留截图均已实际人工查看。

## 截图

| 文件 | 说明 | SHA-256 |
| --- | --- | --- |
| `34-temporary-assistant-chat.png` | 本轮助手与背景 | `0207ff3590c2b0ac4095383811e280f2f765fe1ead75db9c05f89715141761ad` |
| `47-ready-for-offline-long-send.png` | 附件待长按发送 | `70c8ae7678e26826c8065b702fd93ea4d81c924f65306db43cfa94b485dedf85` |
| `48-offline-message-saved.png` | 离线消息已保存 | `23e130f69db9ebae2087706f43ff74ed1ff1d53b9801e658fab4f32d4b417c6e` |
| `49-user-message-menu.png` | 用户消息 Fork 菜单 | `1874e7c0c6bfa596028ebf649a378e848878244153b853924e133eea20509781` |
| `58-cold-start-fork-opened.png` | fork 冷启动打开 | `8c0810428e7e15906fd83850a8339d29503c6dd7d1bbbbdd96348bb35c10c2b7` |
| `87-avatar-crop-confirm-fixed.png` | 最终包系统裁剪确认 | `f437b84c3cb263cbe8d11fd25497f8150d604a067b7607865aa7d73f003e109e` |
| `88-avatar-applied-fixed.png` | 最终包头像已保存 | `be4eed862aacd347b3e0ed504167be485e6eb31facfe2c5a7804e2b0f44b48f8` |
| `90-avatar-cold-drawer.png` | 最终包头像冷启动 | `ad62c9bc26389d5eb45adc1b5a20c961de90dce42ad1efac00d30047cac78b9a` |
| `115-temp-assistant-deleted.png` | GUI 删除后的助手列表 | `8a2cdcb0c271bb65fb73e755c29165b90e332ef9186631da4e5cb52b54183828` |
| `117-postcleanup-routeactivity.png` | 清理后默认助手冷启动 | `7f7a96f9fe062f90cb829fbd8a8516a1312ccb37078557acd61deca5e84fffba` |
