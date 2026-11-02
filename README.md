# AM++ Lyrics

基于 [AM++](https://github.com/Zennmn/AM-plus-plus) 的 Apple Music Android 增强版，集成 [Tcrrry 歌词增强](https://github.com/tcrrry/Tcrrry-Lyrics-Plugin)。

当前版本 **v1.11**：AM++ 1.6.8／Apple Music 7.0.0（1614），Android 12+、arm64。

[下载与发布说明](https://github.com/tcrrry/AMPP-Lyrics/releases/tag/v1.11) · [历史版本](https://github.com/tcrrry/AMPP-Lyrics/releases) · [独立歌词插件](https://github.com/tcrrry/Tcrrry-Lyrics-Plugin/releases/tag/plugin-v1.2.1)

## 下载

| 文件 | 用途 |
| --- | --- |
| [普通 APK](https://github.com/tcrrry/AMPP-Lyrics/releases/download/v1.11/AMPP-Lyrics-AppleMusic-7.0.0-arm64.apk) | 完整增强版，使用 Apple Music 原包名 |
| [共存 APK](https://github.com/tcrrry/AMPP-Lyrics/releases/download/v1.11/AMPP-Lyrics-AppleMusic-7.0.0-arm64-coexist.apk) | 与原包名安装共存，包名 `com.tcrrry.ampplyrics.coexist` |
| [APKS 分包](https://github.com/tcrrry/AMPP-Lyrics/releases/download/v1.11/AMPP-Lyrics-AppleMusic-7.0.0-arm64.apks) | 普通版分包形式，需用支持 APKS 的安装器完整安装 |
| [独立模块](https://github.com/tcrrry/AMPP-Lyrics/releases/download/v1.11/AMPP-Lyrics-module.apk) | 供兼容框架使用，不是音乐主程序 |

普通版与官方版包名相同、签名不同，不能直接覆盖官方版本。已有旧版升级前先核对签名与数据，不要为了安装而直接卸载音乐应用。

## 变化

- 同步 AM++ 1.6.8 和 Apple Music 1614 宿主适配，保留共存路由及 vivo 媒体服务发现声明。
- 内置歌词插件 1.2.1 和独立翻译插件 1.0.0；保留歌词来源选择、注音、翻译、辉光及此前预览优化。
- 修复点单曲时插件误改循环方式的问题：插件仅在识别到页面播放／随机按钮的情况下处理对应队列请求。
- 稳定性修订移植受限资源异常恢复和旧玻璃接口，并为普通版、共存版提供可选 vivo 外部媒体服务；需另行导入发布附件中的 vivo 插件。
- 保留宿主原有手动歌词和自动补词设置。宿主与插件来源协作仍需实机回归；不能保证显示的“原生”一定来自苹果官方。
- 离线翻译中文模型内置，其他语言按需下载；使用合并的播放模式功能时请停用独立播放模式插件。

## 验证与边界

构建、主要单元测试、1614 宿主档案、插件 SDK 和包内容校验通过；最终普通 APK、共存 APK、APKS 和模块经过签名、身份及嵌入内容检查。共存包及内嵌原包的 vivo 媒体服务发现声明通过静态检查。

**v1.11 最终包尚未完成实机验收。** 宿主补词与插件并用、歌词显示、循环按钮、共存播放器控制及冷启动需分别验证；不承诺共存版原子岛支持。旧版已验证结果不能直接等同于新包结果。

项目依照 [GPL-3.0](LICENSE) 分发，第三方来源及许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
