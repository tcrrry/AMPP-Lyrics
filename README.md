<div align="center">
  <img src="docs/images/b851cbb7f571c6666f1a41377baa778b.jpg" width="104" alt="AM++ 项目图标">

# AM++ Lyrics · 歌词增强版

**为 Apple Music 补充多源歌词、逐字歌词、译文与发音**

**单 APK / APKS 整合安装包 · 无需 Root · 无需另装 LSPosed 或模块**

**[下载 APK（推荐）](https://github.com/tcrrry/AMPP-Lyrics/releases/latest/download/AMPP-Lyrics-AppleMusic-6.5.3-arm64.apk)** · [下载 APKS（备用）](https://github.com/tcrrry/AMPP-Lyrics/releases/latest/download/AMPP-Lyrics-AppleMusic-6.5.3-arm64.apks) · [模块 APK（进阶用户）](https://github.com/tcrrry/AMPP-Lyrics/releases/latest/download/AMPP-Lyrics-module.apk) · [共存 APK（测试备用）](https://github.com/tcrrry/AMPP-Lyrics/releases/latest/download/AMPP-Lyrics-AppleMusic-6.5.3-arm64-coexist-test-r2.apk) · [全部版本](https://github.com/tcrrry/AMPP-Lyrics/releases) · [视频演示](#视频演示)

</div>

## 这是什么

本项目基于 **[Zennmn / AM-plus-plus（AM++）](https://github.com/Zennmn/AM-plus-plus)** 制作，在 AM++ 的 Apple Music 增强功能上，融入我的 **[桌面歌词 / Desktop Lyrics](https://github.com/tcrrry/desktop-lyrics)** 项目中的歌词匹配与翻译能力。

面向普通用户优先推荐已嵌入模块的 Apple Music **单 APK 整合包**，直接打开即可交给系统安装器安装，已有用户实机安装验证通过。另提供 **APKS 整合包**，通过 MT 管理器、SAI 等分包安装器安装。两者均不需要 Root、LSPosed 或另装模块 APK。

歌词功能在 Apple Music 内部显示；桌面歌词项目中的独立悬浮窗不包含在这个整合包里。Apple Music 的账号、订阅和播放权限仍按原服务规则使用。

## 视频演示

**B站视频：** 待补充

![AM++ Lyrics：将 QQ 音乐和网易云歌词融入 Apple Music](docs/images/ampp-lyrics-cover.jpg)

<!-- B站视频链接预留：发布后填入实际视频地址。 -->

## 核心功能

- 在 Apple Music 中匹配 **QQ 音乐、网易云音乐、LRCLIB** 的歌词，并结合歌曲信息选择结果。
- 支持来源提供的逐字歌词、逐行歌词和平台译文；没有平台译文时，可选择离线补译或自定义 API。
- 支持来源提供的日语等发音轨道；发音显示在原文上方，使用暗色小字号文字，不参加原文逐字高亮。
- 播放页常驻歌词菜单，可查看当前来源；**单击来源切换、双击重新匹配当前来源、长按进入歌词设置**。
- 歌词设置与 AM++ 设置分开，支持来源管理、匹配输入查看、匹配历史与预览。
- 歌词设置支持跟随原软件配色，以及手动选择日间、夜间模式。
- 保留 AM++ 原有的歌词模糊、字体、自定义歌词、平板双栏和液态玻璃等增强功能；具体条件见[原作者的功能介绍](https://github.com/Zennmn/AM-plus-plus#readme)。

逐字歌词、译文与发音取决于歌曲和来源是否提供，并非每首歌都有。来源可用性也可能随地区、网络和平台策略变化。

## 下载

当前正式版本：**v1.1**（发音优先级修正版），基于 **Apple Music 6.5.3** 与 **AM++ 1.6.2**。

| 安装文件 | 适合谁 | 使用方式 |
| --- | --- | --- |
| **[APK 整合包（推荐）](https://github.com/tcrrry/AMPP-Lyrics/releases/latest/download/AMPP-Lyrics-AppleMusic-6.5.3-arm64.apk)** | **普通用户，优先选择** | 直接打开 APK 安装，无需分包安装器；已有用户实机安装验证通过。 |
| [APKS 整合包（备用）](https://github.com/tcrrry/AMPP-Lyrics/releases/latest/download/AMPP-Lyrics-AppleMusic-6.5.3-arm64.apks) | 需要分包安装方式的用户 | 用 MT 管理器、SAI 等分包安装器安装，无需 Root 或额外模块框架。 |
| [模块 APK](https://github.com/tcrrry/AMPP-Lyrics/releases/latest/download/AMPP-Lyrics-module.apk) | 已有兼容模块环境、或需要自行嵌入的进阶用户 | 独立模块，不是 Apple Music 主程序，不能单独安装后直接播放音乐。 |
| [共存 APK（测试备用）](https://github.com/tcrrry/AMPP-Lyrics/releases/latest/download/AMPP-Lyrics-AppleMusic-6.5.3-arm64-coexist-test-r2.apk) | 希望保留官方版或普通增强版，并愿意实机验证的用户 | 独立包名，直接安装；用户反馈初版可登录，开启歌词替换后匹配正常；第二版修正资源查找和封面 Provider 匹配，显示效果待复测。 |

v1.1 在 v1 / r25 基础上加入发音覆盖率评分，并允许更完整的发音结果刷新播放页。歌曲匹配可信度优先，同档内译文、逐字、发音覆盖率权重依次为 200、100、50；手动指定来源仍然优先。已安装 v1 或 r25 的用户需安装新版整合包才能获得这次优化。旧版 v1 保留在发布列表中。

共存测试 APK 是独立备用选项，显示为 **AM++ Lyrics 共存测试版**，与普通 APK / APKS 使用不同包名和应用数据目录；无需为测试卸载现有应用。请从共存版自己的图标登录，外部音乐链接和第三方音乐 SDK 认证入口暂不支持。[共存实现与验证说明](docs/coexistence-feasibility.md)。默认仍推荐上方已验证安装的普通 APK。第二版可覆盖升级共存初版；首次使用请在“自定义歌词”开启“自定义歌词替换”和“自动实时补全”，保存后完全退出并重开。

发布页附带的 `.sha256` 文件用于校验，不是安装包。开发期 r8～r25 等历史版本保留在原开发仓库；本公开仓库从 v1 开始发布。

## 安装与使用（无需 Root）

1. 优先下载 **APK 整合包**，不用另外下载模块 APK；需要分包安装方式时选择 **APKS 整合包**。
2. APK 直接打开，按系统提示允许安装应用；APKS 使用 MT 管理器等分包安装器安装。
3. 不要把 `.apks` 改成 `.apk`，也不要只安装从压缩包里取出的某一个分包。
4. 安装完成后打开 Apple Music，按正常流程登录并播放音乐；需要订阅的功能仍需要 Apple Music 订阅。
5. 在 Apple Music 设置中打开 **Tcrrry 歌词设置**，或长按播放页歌词菜单中的来源项进入；AM++ 原有功能使用单独的 **AM++ 模块设置**入口。
6. 歌词匹配不合适时，可以切换来源，或双击来源项重新匹配当前来源。

整合包与官方 Apple Music 使用不同签名。如果安装时提示签名冲突，通常需要先卸载官方版；**卸载会删除其本地数据和已下载音乐**。不要为解决冲突直接清除数据，先确认自己需要保留的内容。

单 APK 与当前 APKS 使用相同的 NPatch 测试签名，但从分包切换到单 APK 的覆盖安装仍需实机验证；安装器必须完整替换应用并移除旧分包。不要单独替换 APKS 的 base.apk。切换前确认本地数据和下载音乐的保留需求。

从本项目旧版更新时，可以先尝试覆盖安装；如果同样提示签名冲突，再确认安装包来源与签名。部分设置或模块开关需要完全退出并重新打开 Apple Music 才会生效。

## 兼容性

- 模块最低要求为 **Android 8.0（API 26）**；歌词模糊等原 AM++ 功能还有各自的系统版本要求。
- **当前 APKS 是 arm64-v8a / xxxhdpi 分包组合**，不是包含所有架构和屏幕资源的通用包；不适用于纯 32 位设备或 x86 设备。
- 单 APK 由相同的原始分包合并后重新嵌入 v1.1 模块，也只包含 arm64-v8a 与 xxxhdpi 资源，不是全架构通用包。
- 整合包的 Apple Music 主程序要求 **Android 11（API 30）及以上**；模块最低系统要求不代表整合包最低要求。
- 当前整合包针对 **Apple Music 6.5.3（1599）** 构建；其他版本需要另外适配。
- 单 APK 已有用户实机安装验证通过；登录、播放和歌词显示，以及跨机型和其他屏幕密度尚未完成全面验证，遇到问题请附上手机型号、Android 版本和安装／显示情况。

## 常见问题

### 需要 Root 或安装 LSPosed 吗？

**APK 和 APKS 整合包均不需要。** 模块已嵌入；APK 用系统安装器，APKS 用分包安装器。

### 整合包和模块 APK 都要安装吗？

**不用。** 使用 APKS 或单 APK 整合包时，只安装选中的整合包。独立模块 APK 留给已经配置兼容模块环境或自行打包的用户。

### 这是桌面歌词悬浮窗吗？

不是独立悬浮窗。这是 Apple Music 内部的歌词增强整合版；独立悬浮窗请使用[桌面歌词项目](https://github.com/tcrrry/desktop-lyrics)。

### 为什么某首歌没有发音或译文？

该歌曲在当前来源可能没有这些轨道，可以尝试其他来源。补充翻译可以补译文，但不保证生成平台发音轨道。

## 开发与更新

- [源码构建与打包说明](docs/build-and-release.md)
- [首发说明模板](docs/release-template.md)

## 原项目与致谢

本项目根据 **[Zennmn / AM-plus-plus](https://github.com/Zennmn/AM-plus-plus)** 修改与整合，不是 AM++ 原作者发布的版本。感谢原作者与贡献者提供 Apple Music 增强能力。

歌词匹配与翻译能力来自我的 **[Tcrrry / Desktop Lyrics](https://github.com/tcrrry/desktop-lyrics)**；APKS 嵌入使用 **NPatch**。其他依赖与署名见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

源码沿用仓库中的 **[GPL-3.0 许可证](LICENSE)**。Apple Music、歌词、译文及其他第三方内容的权利归各自权利人所有。
