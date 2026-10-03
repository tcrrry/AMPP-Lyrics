# AM++ Lyrics · 歌词增强版 v1.2

基于 [Zennmn / AM-plus-plus](https://github.com/Zennmn/AM-plus-plus)，融合我的[桌面歌词项目](https://github.com/tcrrry/desktop-lyrics)的歌词匹配与翻译能力。

![封面](https://raw.githubusercontent.com/tcrrry/AMPP-Lyrics/v1.2/docs/images/ampp-lyrics-cover.jpg)

**B站视频：** [Apple Music 安卓端歌词增强演示：QQ／网易云逐字歌词、翻译与发音](https://b23.tv/FsZM26H)

## 这次更新

- 普通版本新安装默认开启自定义歌词替换，让多源自动匹配开箱可用；已有用户明确保存的关闭选项继续保留。
- AM++ 设置与 Tcrrry 歌词设置的日夜配色都移到页面最后。
- 没有逐字时间的歌词使用原生逐行模式，不再给整句合成逐字计时；真实逐字歌词继续保留逐字动画。
- 更新自动歌词缓存格式，避免沿用旧版模拟逐字结果。

## 下载

- **[普通单 APK（推荐）](https://github.com/tcrrry/AMPP-Lyrics/releases/download/v1.2/AMPP-Lyrics-AppleMusic-6.5.3-arm64.apk)**：直接打开安装。
- [APKS 整合包](https://github.com/tcrrry/AMPP-Lyrics/releases/download/v1.2/AMPP-Lyrics-AppleMusic-6.5.3-arm64.apks)：通过 MT 管理器或 SAI 等分包安装器安装。
- [模块 APK](https://github.com/tcrrry/AMPP-Lyrics/releases/download/v1.2/AMPP-Lyrics-module.apk)：已有兼容模块环境或自行嵌入的进阶用户使用。

普通 APK 和 APKS 无需 Root、LSPosed 或另外安装模块。基于 Apple Music 6.5.3，arm64-v8a / xxxhdpi，整合包要求 Android 11 及以上；不是全架构通用包。更新后完全退出并重开 Apple Music。

另提供 [共存测试第三版](https://github.com/tcrrry/AMPP-Lyrics/releases/download/v1.2/AMPP-Lyrics-AppleMusic-6.5.3-arm64-coexist-test-r3.apk)：修正资料库“播放列表、歌手、专辑、歌曲”等分类页共用布局的相对类名，避免按共存包名加载不存在的宿主行为类。普通版与共存版测试、Lint、签名和资源校验通过，分类入口的实际效果待手机复测。可覆盖安装旧共存版；全部历史包与附件保留。

整合包沿用之前的 NPatch 公共测试签名；与官方 Apple Music 签名不同。账号、订阅和播放权限仍按原服务规则；若卸载官方版，会删除本地数据和下载音乐。跨安装形式的覆盖升级与实际播放仍需手机验证。

源码沿用 GPL-3.0，原项目及第三方署名保留。
