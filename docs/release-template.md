# 首发说明模板

v1 首发采用已验证的 r25 安装文件，仅调整发布名称与附件名称；r25 及全部历史版本保持不变。后续发布时，将版本号、下载链接与实际附件名称更新后复制到 Release 描述中。

---

# AM++ Lyrics · 歌词增强版

基于 [Zennmn / AM-plus-plus（AM++）](https://github.com/Zennmn/AM-plus-plus) 制作，加入我的[桌面歌词](https://github.com/tcrrry/desktop-lyrics)项目中的多源歌词匹配与翻译能力。

**普通用户下载 APKS，用 MT 管理器等分包安装器安装即可，无需 Root、无需 LSPosed，也无需另装模块 APK。**

## 视频演示

**B站视频：** 待补充

![AM++ Lyrics：将 QQ 音乐和网易云歌词融入 Apple Music](images/ampp-lyrics-cover.jpg)

<!-- 视频链接预留：填写实际 B站视频地址。 -->

当前正式版本：**v1**。基于 Apple Music 6.5.3 与 AM++ 1.6.2，内容对应已验证的 r25；已安装 r25 的用户无需为了文件改名重新安装。

## 下载哪一个？

- **[APKS 整合包](https://github.com/tcrrry/AMPP-Lyrics/releases/download/v1/AMPP-Lyrics-v1-AppleMusic-6.5.3-arm64.apks)：** 普通用户选择这一项，已包含 Apple Music 和增强模块。
- **[模块 APK](https://github.com/tcrrry/AMPP-Lyrics/releases/download/v1/AMPP-Lyrics-v1-module.apk)：** 供已有兼容模块环境或需要自行嵌入的进阶用户使用，不是独立播放器。
- `.sha256` 文件只用于校验，不需要安装。

## 主要功能

- QQ 音乐、网易云音乐、LRCLIB 多源歌词匹配与来源切换。
- 来源提供的逐字歌词、平台译文与发音；可选离线补译及自定义 API。
- 发音在原文上方，以暗色小字号显示，原文保留逐字高亮。
- 播放页来源项支持单击切换、双击重新匹配、长按歌词设置。
- 歌词设置与 AM++ 设置分开，支持跟随配色和手动日间／夜间模式。

## 安装

1. 下载 `.apks` 文件，用 MT 管理器等支持 APKS 的分包安装器打开并安装，不要修改扩展名。
2. 打开 Apple Music，按正常流程登录使用。Apple Music 订阅及播放权限仍按原服务规则。
3. 歌词功能进入 Tcrrry 歌词设置；原 AM++ 功能进入 AM++ 模块设置。

当前打包基础为 Apple Music 6.5.3，架构为 **arm64-v8a**，屏幕资源分包为 **xxxhdpi**，不是通用架构包。Android 模块最低要求为 8.0；部分增强功能要求更高版本系统。

与官方 Apple Music 签名不同，安装时若提示签名冲突，可能需要先卸载官方版；卸载会删除其本地数据及已下载音乐，请先确认要保留的内容。歌词、译文与发音是否可用取决于歌曲和来源。

本项目是基于 AM++ 的修改整合版，并非 AM++ 原作者发布。原项目及各贡献者署名与许可保留，源码沿用 GPL-3.0。开发期历史版本保留在原开发仓库；公开发布从 v1 开始。
