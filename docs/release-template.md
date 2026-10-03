# 发布说明模板

以下为 v1.1 三种安装包的发布说明；未来发布请更新版本、验证范围与实际 SHA-256。

---

# AM++ Lyrics · 歌词增强版 v1.1

基于 [Zennmn / AM-plus-plus](https://github.com/Zennmn/AM-plus-plus) 制作，融合我的[桌面歌词项目](https://github.com/tcrrry/desktop-lyrics)的多源歌词与翻译功能。

![AM++ Lyrics 封面](https://raw.githubusercontent.com/tcrrry/AMPP-Lyrics/v1.1/docs/images/ampp-lyrics-cover.jpg)

**B站视频：** 待补充

## 这次更新

- 自动匹配加入发音覆盖率评分：歌曲匹配可信度优先，同档内译文、逐字、发音覆盖率权重为 200、100、50。
- 更完整的发音结果能刷新先返回的歌词；不会仅因新增发音就切换到综合质量更低的歌词。
- 升级后重新建立自动歌词缓存，让新评分生效；保留手动指定来源。

## 下载与安装

提供三种安装包：

| 安装包 | 下载与使用 |
| --- | --- |
| **APK 整合包（推荐，优先下载）** | [下载 APK](https://github.com/tcrrry/AMPP-Lyrics/releases/download/v1.1/AMPP-Lyrics-AppleMusic-6.5.3-arm64.apk)，直接交给系统安装器安装，无需分包安装器；已有用户实机安装验证通过。 |
| **APKS 整合包（备用）** | [下载 APKS](https://github.com/tcrrry/AMPP-Lyrics/releases/download/v1.1/AMPP-Lyrics-AppleMusic-6.5.3-arm64.apks)，使用 MT 管理器 / SAI 等分包安装器安装。 |
| **模块 APK（进阶用户）** | [下载模块 APK](https://github.com/tcrrry/AMPP-Lyrics/releases/download/v1.1/AMPP-Lyrics-module.apk)，供已有兼容模块环境或自行嵌入的进阶用户使用，不能单独作为 Apple Music 播放器使用。 |

APK 和 APKS 都已嵌入模块，**无需 Root、LSPosed 或另装模块 APK**；选择其中一种即可。`.sha256` 文件只用于校验。

### 单 APK 安装反馈（2026-10-03）

从 v1.1 APKS 保留的原始分包合并后重新嵌入相同模块，沿用 NPatch 1.0.7（741）、现有配置及测试签名。只改变打包形态，模块功能与 v1.1 一致。单 APK 已通过静态校验，**已有用户反馈实机安装正常，现优先推荐 APK**。登录、播放和歌词显示仍需进一步验证，不将安装成功等同于全部功能验证通过。

两种整合包仍基于 Apple Music 6.5.3（1599），要求 Android 11 及以上，只包含 arm64-v8a / xxxhdpi 变体，并非全架构通用包；模块内部版本为 1.6.3（113）。账号、订阅和播放权限仍按原服务规则。

单 APK 与当前 APKS 签名一致，但分包切换到单 APK 的覆盖安装仍需实机验证，安装器必须完整替换应用并移除旧分包；不要仅替换 base.apk。与官方 Apple Music 签名不同，不能正常覆盖官方版。卸载会删除本地数据和下载音乐，请在切换前确认保留需求。安装后完全退出并重启 Apple Music。

原 v1 及开发期历史版本保留。云端未连接手机，此次排序与刷新改动仍需实际播放验收。源码沿用 GPL-3.0；第三方内容权利归各自权利人。

## 验证

924 项单元测试、lint 与 APK 构建通过；三分包签名、内嵌模块与 APKS 归档完整性校验通过。

单 APK 静态校验：APK v2 签名有效，签名证书与 APKS 一致；内嵌模块 SHA-256 与 v1.1 模块一致；NPatch 配置一致；包名 / 版本正确、无需分包，全部 arm64 原生库内容一致。构建脚本见主分支 `scripts/package-single-apk.py`。

## SHA-256

```text
309062ad1c1987160e493277a396ab784b1c052e6bf96c68b8ba92a5188a5781  AMPP-Lyrics-AppleMusic-6.5.3-arm64.apk
e08466d26ba06c55b8f491a52e4c7bd383602f68cb3ce06af1182c0167123f10  AMPP-Lyrics-AppleMusic-6.5.3-arm64.apks
e14317e4faedd01a9131ff4258a2f0291e8d69a30fa22278962da027e6107873  AMPP-Lyrics-module.apk
```
