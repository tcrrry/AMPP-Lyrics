# 源码构建与打包

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

v1 安装包沿用已验证 r25 的二进制，未为改名重编译或重签名。原项目及第三方许可见 LICENSE 与 THIRD_PARTY_NOTICES.md；Apple Music 本身不属于模块 GPL 源码。
