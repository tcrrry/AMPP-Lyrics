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


## 单 APK 整合包（推荐）

单 APK 使用 v1.1 APKS 中保留的原始三分包，先合并，再用相同的 NPatch 1.0.7（741）重新嵌入原模块。不能直接合并已注入分包：NPatch 的内嵌 origin.apk 也必须包含完整资源和原生库。

依赖 Java 21 及以上、Python 3.11 及以上、unzip、Android Build Tools 35.0.0 的 apksigner / aapt2，以及以下固定工具（脚本校验工具 SHA-256）：

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

当前单 APK 要求 Android 11 及以上，只包含 arm64-v8a / xxxhdpi 变体。已有用户实机安装验证通过，发布页优先推荐单 APK；这项反馈不代表登录、播放、歌词显示及覆盖安装均已验证。


## 备用共存 APK（测试版）

共存包独立使用 `com.tcrrry.ampplyrics.coexist`，显示名称为“AM++ Lyrics 共存测试版”。不会修改或覆盖普通 APK / APKS / 模块 APK 发布附件。此包需要单独登录，仅供实机验证。

先构建专用模块（普通构建不带此参数）：

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -PamppCoexistence=true --no-daemon
```

再用固定的原始 APKS 生成共存包：

```sh
python3 scripts/package-coexist-apk.py input.apks app/build/outputs/apk/debug/app-debug.apk \
  AMPP-Lyrics-AppleMusic-6.5.3-arm64-coexist-test-r2.apk \
  --editor /path/to/APKEditor-1.4.9.jar \
  --npatch /path/to/jar-v1.0.7-741-release.jar \
  --apksigner /path/to/build-tools/35.0.0/apksigner \
  --aapt2 /path/to/build-tools/35.0.0/aapt2
```

模块构建使用 Java 17，NPatch 打包使用 Java 21。`CoexistenceManifest.java` 移除 shared UID、隔离 Provider 和自身权限、设置独立名称，并取消 BROWSABLE 外部链接注册。模块将安装身份与原始资源命名空间分开，并在共存进程内重定向自身 Intent / Provider URI。第二版还将共存包名的动态资源查找映射回原资源命名空间，并将自身 UriMatcher authority 与隔离 Provider 保持一致，保留独立安装身份。打包脚本校验资源、原始 DEX、原生库、签名、内嵌模块及隔离结构。

也可运行 GitHub Actions“构建备用共存 APK（测试版）”；它分别测试默认与共存模块身份，仅上传共存测试 APK 及其校验文件，不替换原有附件。详细测试范围见[共存评估](coexistence-feasibility.md)。

## v1.2 常规版本发布

`publish-v1-2.yml` 在 `fix/v1-2-default-lyrics` 分支执行完整测试、Lint 和构建后，使用 v1.1 APKS 保留的原始宿主重新嵌入新模块，生成普通单 APK、APKS 和模块 APK。工具及输入均校验 SHA-256；APKS 保留原 ABI / 密度分包，只更新主包并核对签名。单 APK 进一步核对宿主代码、资源和原生库。发布不覆盖 v1 / v1.1 附件，共存 r2 下载固定指向 v1.1。

`docs/v1.2-validation.json` 保存实际源码提交、Actions 运行编号、测试结果及附件校验和。发布成功后才将该分支以快进方式同步到 `main`；如有并发修改则停止同步，不强推。
