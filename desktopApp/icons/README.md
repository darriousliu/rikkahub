# 桌面图标

沿用 Android launcher 的黑色兔子标记，将白色底板适配为桌面圆角方形，外围保留真实透明通道。
`RikkaHub.png` 是审核后的高分辨率源图；`RikkaHub.icns`、`RikkaHub.ico` 及窗口 PNG 从它生成。

源图使用 imagegen 编辑 Android launcher 参考图生成，提示词要点：保留兔子标记的轮廓、比例、眼睛与表情，
白色圆角方形底板、居中留白、透明外围，不添加文字、颜色、渐变或额外图案。

重新编码（从仓库根目录执行，需要 JDK 21）：

```sh
java -Djava.awt.headless=true desktopApp/tools/ConvertDesktopIcon.java
```

- ICO：16、24、32、48、64、128、256 px，PNG 压缩帧。
- ICNS：16–1024 px，包含 Retina 帧。
- 窗口资源：`desktopApp/src/jvmMain/resources/icons/RikkaHub.png`，256 px。

转换器只负责缩放和容器编码，不重新绘制源图。
