# 发布说明模板

当前 v1.1：确认测试、签名与归档校验通过后，附上实际 SHA-256 发布。

---

# AM++ Lyrics · 歌词增强版 v1.1

基于 [Zennmn / AM-plus-plus](https://github.com/Zennmn/AM-plus-plus) 制作，融合我的[桌面歌词项目](https://github.com/tcrrry/desktop-lyrics)的多源歌词与翻译功能。

![AM++ Lyrics 封面](images/ampp-lyrics-cover.jpg)

**B站视频：** 待补充

## 这次更新

- 自动匹配加入发音覆盖率评分：歌曲匹配可信度优先，同档内译文、逐字、发音覆盖率权重为 200、100、50。
- 更完整的发音结果能刷新先返回的歌词；不会仅因新增发音就切换到综合质量更低的歌词。
- 升级后重新建立自动歌词缓存，让新评分生效；保留手动指定来源。

## 下载与安装

普通用户下载 **[APKS 整合包](https://github.com/tcrrry/AMPP-Lyrics/releases/download/v1.1/AMPP-Lyrics-AppleMusic-6.5.3-arm64.apks)**，通过 MT 管理器等分包安装器安装，**无需 Root、LSPosed 或另装模块 APK**。从 v1 / r25 更新可先尝试覆盖安装，安装后完全退出并重启 Apple Music。

[模块 APK](https://github.com/tcrrry/AMPP-Lyrics/releases/download/v1.1/AMPP-Lyrics-module.apk) 供已有兼容模块环境或自行嵌入的进阶用户使用；`.sha256` 文件只用于校验。

仍基于 Apple Music 6.5.3，包含 arm64-v8a / xxxhdpi 分包；模块内部版本为 1.6.3（113）。账号、订阅和播放权限仍按原服务规则。与官方 Apple Music 签名不同；如需卸载官方版，先确认本地数据和下载音乐会被删除。

原 v1 及开发期历史版本保留。云端未连接手机，此次排序与刷新改动仍需实际播放验收。源码沿用 GPL-3.0；第三方内容权利归各自权利人。
