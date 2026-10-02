# 源码构建与打包

v1.1 加入发音覆盖率评分和后续刷新判断，模块内部版本为 1.6.3（113）。公开安装包下载入口使用最新正式 Release；旧 v1 仍保留。

v1 对应已经手机验收的 r25，基于 AM++ 1.6.2 与 Apple Music 6.5.3（1599）。公开源码包含模块及其构建依赖；旧开发仓库的提交历史、打包输入、签名材料与个人备份不迁入此仓库。

## 构建模块

使用 JDK 17、Android SDK 37 与 Build Tools 37.0.0。Gradle 版本由 `gradle/wrapper/gradle-wrapper.properties` 固定。

```sh
chmod +x gradlew
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon
```

输出位于 `app/build/outputs/apk/debug/app-debug.apk`。这是独立模块，不是整合版 Apple Music 安装包。

可用 `keystore.properties.example` 配置自己的本地签名；实际私钥与口令不应提交到 Git。自行构建的签名可能与发布版本不同。

## APKS 整合包

Gradle 不直接生成整合包。自行打包需取得合法可用的 Apple Music 分包与 NPatch，将构建出的模块嵌入对应分包后运行：

```sh
python3 scripts/package-tcrrry-embedded.py patched output.apks
```

当前发布文件包含 `base.apk`、`split_config.arm64_v8a.apk` 与 `split_config.xxxhdpi.apk`。其他设备变体需匹配相应分包并验证；签名必须在各分包间一致。

旧 v1 安装包沿用已验证 r25 的二进制；v1.1 重新构建模块后嵌入同版本分包，整合包沿用原有 NPatch 公共测试签名。独立模块为调试构建；如覆盖安装提示签名冲突，应先确认旧模块的签名与安装方式。原项目及第三方许可见 LICENSE 与 THIRD_PARTY_NOTICES.md；Apple Music 本身不属于模块 GPL 源码。


## 单 APK 整合包（试验版）

单 APK 使用 v1.1 APKS 中保留的原始三分包，先合并，再用相同的 NPatch 1.0.7（741）重新嵌入原模块。不能直接合并已注入分包：NPatch 的内嵌 origin.apk 也必须包含完整资源和原生库。

依赖 Java 17 及以上、Python 3.11 及以上、unzip、Android Build Tools 35.0.0 的 apksigner / aapt2，以及以下固定工具（脚本校验工具 SHA-256）：

- [APKEditor 1.4.9](https://github.com/REAndroid/APKEditor/releases/tag/V1.4.9)
- [NPatch 1.0.7（741）](https://github.com/7723mod/NPatch/releases/tag/v1.0.7)

```sh
python3 scripts/package-single-apk.py input.apks AMPP-Lyrics-AppleMusic-6.5.3-arm64.apk \
  --editor /path/to/APKEditor-1.4.9.jar \
  --npatch /path/to/jar-v1.0.7-741-release.jar \
  --apksigner /path/to/build-tools/35.0.0/apksigner \
  --aapt2 /path/to/build-tools/35.0.0/aapt2
```

脚本检查 APKS 完整性、单 APK 签名与 APKS 一致、NPatch 配置不变、内嵌模块校验值不变、包名和版本正确、不再要求分包，以及所有 arm64 原生库内容一致；随后输出 APK 和 `.sha256` 文件。NPatch 的嵌套 ZIP 布局包含重叠条目，因此提取内嵌文件用 unzip，而不是关闭 Python zipfile 的安全检查。

当前单 APK 要求 Android 11 及以上，只包含 arm64-v8a / xxxhdpi 变体。静态校验不能替代实机安装、登录、播放、歌词显示及覆盖安装测试；发布页必须标注试验版。
