<div align="center">
<img src="docs/images/b851cbb7f571c6666f1a41377baa778b.jpg" width="104" alt="AM++ 项目图标">

# AM++ Lyrics · 歌词增强版

**Apple Music 7.0.0-beta / 1609 · v1.8 · Android 12+ · arm64**

[![普通版 APK｜推荐](docs/images/downloads/ordinary-apk.svg)](https://github.com/tcrrry/AMPP-Lyrics/releases/download/v1.8/AMPP-Lyrics-AppleMusic-7.0.0-beta-arm64.apk)

[![共存版 APK｜保留官方版](docs/images/downloads/coexist-apk.svg)](https://github.com/tcrrry/AMPP-Lyrics/releases/download/v1.8/AMPP-Lyrics-AppleMusic-7.0.0-beta-arm64-coexist.apk)

[![APKS 分包版](docs/images/downloads/split-apks.svg)](https://github.com/tcrrry/AMPP-Lyrics/releases/download/v1.8/AMPP-Lyrics-AppleMusic-7.0.0-beta-arm64.apks)

[![独立模块｜非主程序](docs/images/downloads/module-apk.svg)](https://github.com/tcrrry/AMPP-Lyrics/releases/download/v1.8/AMPP-Lyrics-module.apk)

**[打开 v1.8 下载页](https://github.com/tcrrry/AMPP-Lyrics/releases/tag/v1.8)** · [历史版本](https://github.com/tcrrry/AMPP-Lyrics/releases) · [独立歌词插件](https://github.com/tcrrry/Tcrrry-Lyrics-Plugin)
</div>

## 怎么选

- **直接安装完整增强版**：本项目普通 APK 或共存 APK，无需 Root、另装 LSPosed、导入歌词 ZIP 或安装离线翻译伴随 APK。离线机翻语言模型仍需首次下载。
- **已使用支持 SDK v1 的 AM++**：[独立插件 1.0.7](https://github.com/tcrrry/Tcrrry-Lyrics-Plugin/releases/tag/plugin-v1.0.7) 导入插件管理器。独立插件的离线机翻需要另外安装其伴随 APK。
- **Android 11 用户**：保留 [v1.6 / 1606](https://github.com/tcrrry/AMPP-Lyrics/releases/tag/v1.6)。旧版 6.5.3 见 [v1.2](https://github.com/tcrrry/AMPP-Lyrics/releases/tag/v1.2)。

## v1.8 更新

- 歌词自然填行、缓存策略修复、0–1024 MB 缓存管理与同曲内容柔和过渡。
- 修复共存设置玻璃、来源滚动重置与播放页灰底；恢复共存路由和 vivo 原生播放器发现声明，纳入最终包验证。
- 共存原生播放器显示有预览实测依据，不承诺原子岛；正式四包仍需各自实机验证。
- 少数无可用歌词歌曲持续转圈未修复，保存[异常证据](docs/NO-LYRICS-SPINNER-1807802486.md)。

原有功能继续保留：

- 升级 **Apple Music 1609**，同步 AM++ 1012 对应的上游宿主适配与 SDK v1 插件管理器。
- 内置 **Tcrrry Lyrics Plugin 1.0.7**，首次启动自动导入并启用。升级保留数据，不降级更新的插件版本，不重新启用用户已经关闭或删除的插件。
- 同步已验收的设置页：二级分类、紧凑小窗、磨砂玻璃、入口收回转场、说明翻页、来源单向滚动和完整详情。播放页入口保留专辑色，主页设置入口沿用宿主底色。
- **Tcrrry 歌词设置 → 更多工具 → 检查更新** 检查主项目新版本。新正式版本首次主动提醒，可忽略此版本、稍后一天提醒或关闭自动提醒；手动检查仍可用，不自动下载或安装。
- 保留内置离线 ML Kit 与语言包管理，不需要伴随 APK。迁移旧歌词偏好、翻译设置和自定义歌词文件；API 密钥在本机重新加密迁移，不上传或记录明文，失败时保留原加密文件供下次启动重试。
- 普通 APK、共存 APK、APKS、模块 APK 同步更新，旧发布与附件不替换。

设计与构建基线见 [整合说明](docs/integration-v1.7.md)，完整更新说明见 [v1.8 发布说明](docs/release-v1.8.md)。

## 使用

1. 普通 APK 和共存 APK 选一种。共存版名称为 **AM++ Lyrics 共存版**，可保留官方版，登录与资料独立。
2. APKS 用 SAI、MT 等分包安装器完整安装，不要改名为 APK 或只取一个分包。
3. 正常登录 Apple Music 并播放；账号、订阅和权限仍按原服务规则使用。
4. 在 Apple Music 设置页点击与 **AM++ 设置** 平行的 **Tcrrry 歌词设置**，或从插件管理器进入；亦可长按播放页歌词菜单中的来源项。
5. 来源项单击换源、双击寻找当前源其他匹配、长按设置。匹配信息、历史和预览继续保留。
6. 离线机翻在 **翻译与注音 → 离线语言包管理** 下载原语言和中文模型，无需伴随 APK。

| 文件 | 包名与用途 |
| --- | --- |
| `AMPP-Lyrics-AppleMusic-7.0.0-beta-arm64.apk` | 普通整合包，`com.apple.android.music` |
| `AMPP-Lyrics-AppleMusic-7.0.0-beta-arm64-coexist.apk` | 共存整合包，`com.tcrrry.ampplyrics.coexist` |
| `AMPP-Lyrics-AppleMusic-7.0.0-beta-arm64.apks` | 普通版分包形式，完整安装 |
| `AMPP-Lyrics-module.apk` | 普通包的模块，非音乐主程序，仅供兼容框架或自行嵌入 |

普通增强版与官方版包名相同、签名不同，不能直接覆盖官方版。本项目旧版可尝试覆盖升级，**不要先卸载音乐应用以免丢失本地数据**。共存版外部链接与第三方认证仍不保证可用。旧云构建独立模块的签名可能不同，模块不能覆盖时需重装模块，不需要卸载音乐应用。

## 功能与边界

支持 QQ、网易云、LRCLIB 等来源匹配、逐字与逐行歌词、平台译文、API 翻译、离线机翻、多语言注音、字词对应发音、辉光及短单元平滑。没有可靠逐字时间时保持逐行，无法可靠对齐的发音保持整行。来源受歌曲、地区、网络与平台策略影响。

基于 [Zennmn/AM-plus-plus](https://github.com/Zennmn/AM-plus-plus)，歌词增强由 [Tcrrry-Lyrics-Plugin](https://github.com/tcrrry/Tcrrry-Lyrics-Plugin) 提供，源于 [Desktop Lyrics](https://github.com/tcrrry/desktop-lyrics)。本项目不包含独立悬浮窗歌词。

上游匹配路径仅在运行时屏蔽，由 Tcrrry 统一负责歌词替换，避免重复挂载；原保存值不重置。移除内置歌词插件不会在下一次普通启动重新安装；恢复请导入独立 ZIP。

## 验证

已执行 1609 接口、歌词扩展、单元测试、Lint、嵌套插件内容、签名、身份、资源、原始 DEX、原生库及分包完整性检查。具体结果、源码提交与附件哈希见发布附件 `v1.8-validation.json` 和 `.sha256`。

**设置预览已由用户验收；共存背景修复有用户确认，vivo 原生播放器显示/绑定有预览实测，控件操作与原子岛未验收。正式四包重新构建，自动检查不替代实际播放与设备兼容性测试。**

## 演示

[B站：Apple Music 安卓端歌词增强演示](https://b23.tv/FsZM26H)

![AM++ Lyrics 歌词效果](docs/images/ampp-lyrics-cover.jpg)

## 源码

使用项目 Gradle Wrapper、Android SDK 37、JDK 17 编译；打包需 JDK 21、固定 APKEditor 与 NPatch。构建步骤见整合说明。完整上游与主项目历史、旧发布均保留。

按 [GPL-3.0](LICENSE) 分发，来源和许可证见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 以及内置插件的许可证资源。
