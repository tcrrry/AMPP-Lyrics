# Apple Music 7.0 普通单 APK 适配记录

本分支以项目 `9f1985f` 为基线，移植原作者 `Zennmn/AM-plus-plus` 的 `9655ad2012deba48701fc758fada5d2654286501`（v1.6.3），仅交付普通 arm64 单 APK 测试版。生产映射只放行 `com.apple.android.music / 7.0.0-beta / 1606`，其他 7.x 不放行。等待用户验证后再跟进 APKS、独立模块及共存版。

## 移植方式

上游已将核心、宿主 API、反射 hook 及 Apple Music 接入拆成模块。本项目暂时保留现有 app 工程结构，将对应源文件和 profile 归入 app，调整资源命名空间及测试源码路径；版本独立的玻璃参数常量归入 glass。移植上游精确描述符、Fragment 页面族、原生 settings2、播放器玻璃、双栏、元数据接入和最新平板横竖屏修复，保留已有 6.5.x profile。

保留本项目桌面歌词匹配、QQ／网易云查询、翻译、发音、自动歌词优先级、手动换源、缓存 v11、设置界面与普通新安装默认开启自定义歌词。已保存的关闭选择仍保留；没有真实逐字计时的歌词仍为 Line。

额外发音字幕在 1606 中由 `player.i1` 改为 `player.n1`；adapter A 的 subtitle/rebuild/pointer/index/bind 接入分别为 U/b0/y/E/k/l，逐项验证完整描述符。布局和文字颜色通过宿主资源名称查找，不沿用 6.5.3 数值 ID。保留原生 karaoke 字序、发音放在独立字幕上、切换辅助字幕时恢复真实配置。原生 settings2 入口打开本项目完整设置 UI，不向 Compose 设置或抽屉叠加旧 View fallback。

普通模块内部版本为 `1.6.5-700-test / 115`，不是新稳定版版本号；已发布的其他安装形式不修改。

## 原包证据

来自用户指定的上游嵌入版 [103-version](https://github.com/Zennmn/AM-plus-plus/releases/download/embedded-2026.08.10-r1/103-version-com.apple.android.music-npatched.apks)。仅 base 已嵌入 NPatch，arm64/xxhdpi split 为原始包；提取时区分两种情况。

- 输入 APKS SHA-256：`1adae4761b292221189bd47f2f302fe48c7bb45863b2581663ed5efba9146093`
- 原始 base SHA-256：`3d09687ed752e48e73f2c72524e18cffff69c66b523096c2e97c8f9135980603`，与上游 1606 适配记录相同。
- 二进制 Manifest：`com.apple.android.music / 7.0.0-beta / 1606`，最低 API30。
- 原包签名存在 API30–32 Apple / API33+ Play 的签名轮换；逐 split 核验。
- 使用项目既有固定 APKEditor 1.4.9 / NPatch 1.0.7-741 工具，签名 bypass 等级 2 与 103 原输入相同；默认 NPatch 外层证书与本项目稳定版一致。上游 103 的 loader 为 783，此交付重新使用本项目固定 741 loader，合并后的签名模拟采用原包 API30–32 的 Apple 证书；与 103 使用的 Play 证书及 783 loader 不同，需要用户验证启动、登录和播放。

## 验证

本地首次完整通过：app 1,090 项 + glass 22 项，共 **1,112 项，失败/错误/跳过均为 0**；app/glass/glass-lab Debug Lint，普通模块和 glass-lab APK／AndroidTest APK 构建通过。5 个精确 profile、316 个 HLE 目标和旧冻结数据检查通过；32 个渲染参考文件与 2 个声明补丁 hash 通过。最终构建提交 `aca68b0` 已通过 [单 APK Actions](https://github.com/tcrrry/AMPP-Lyrics/actions/runs/37117761851) 和 [标准 Build](https://github.com/tcrrry/AMPP-Lyrics/actions/runs/37117761841)。

宿主静态校验：上游接口 262 项、额外发音／菜单 37 项全部通过。单 APK 原宿主 DEX、原生库字节保持不变，资源 ID／名称完整；嵌入原包与合并输入、嵌入模块与构建模块逐字节 hash 一致；单 APK 去掉 split 必需标记，签名通过。已下载发布附件再次核验 SHA-256 和 v2 签名。最终 APK 为 125,358,625 bytes，SHA-256：`015a20cf308f0b52d4eeb0533c6a57788d58bd1853c24347735c231e3fece7ab`；输入证据见附件 `.validation.json`。

复现：

```bash
python3 scripts/verify-profile-data.py
python3 scripts/verify-glass-reference.py
bash gradlew test :app:lintDebug :glass:lintDebug :glass-lab:lintDebug :app:assembleDebug :glass-lab:assembleDebug :glass-lab:assembleDebugAndroidTest --no-daemon
python3 scripts/verify-host-profile.py ORIGINAL.apk --glass
python3 scripts/verify-700-lyrics-extension.py ORIGINAL.apk --aapt2 "$ANDROID_HOME/build-tools/37.0.0/aapt2"
```

打包使用 `scripts/package-700-single-apk.py`，Actions 使用 `.github/workflows/build-700-single-apk.yml`，产物发布到独立预发布 `applemusic-7.0.0-beta-test-r1`，明确 `--latest=false`。保持 v1.2 为稳定下载入口，无删除或替换历史附件。

[测试下载](https://github.com/tcrrry/AMPP-Lyrics/releases/tag/applemusic-7.0.0-beta-test-r1) 已发布；v1、v1.1、v1.2 所有既有附件的 ID、大小、digest 和更新时间逐项保持一致，GitHub latest 仍为 v1.2。

没有本项目的 7.0 实机验收证据。上游实机反馈不能替代此嵌入版的启动、登录、播放、歌词和生命周期验证；profile 的 `runtimeVerified` 保持 false。
