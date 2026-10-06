# Tcrrry Lyrics 独立插件试验

这是独立 Gradle 工程，不加入当前应用的 settings.gradle。插件 ID、Java 命名空间、数据目录和设置页都与 AM++ / Tcrrry 当前安装包分开。

本次 0.1.0 是可安装的歌词兼容插件：修复计时 span 内或独立单元的英文词间空格；提供默认关闭的相邻短字平滑。没有宣称迁移完成第三方搜词、翻译、注音或辉光控制。

安装：在支持插件系统 v1 的 AM++ 中导入 `Tcrrry-Lyrics-Plugin-0.1.0.zip`，启用后重启，通过该插件自己的设置页调整。已有公开 1.4 的四种安装包没有插件运行时，不能导入 ZIP；不要用这个 ZIP 覆盖安装 APK。官方解析接口签名不符合时插件会拒绝加载，不修改播放器。

构建：

```sh
JAVA_HOME=/path/to/jdk17 ANDROID_HOME=/path/to/android-sdk \
  gradle -p plugins/tcrrry-lyrics check pluginZip
```

需要 Android 37 / build-tools 37.0.0。`lib/ampp-plugin-api-v1.jar` 来自作者提供源码的 `plugin-api:exportSdk`；仅用于编译，不打进插件。API 许可沿用上游 Apache-2.0；本工程不依赖 AM++ 内部类。运行时通过 SDK 取得 Apple Music 类加载器，仅匹配 `TTMLParserNative.songInfoFromTTML(String)` 的已验证签名。

六项纯 Java 回归检查由 `check` 调用 `regression` 执行，包含空格、时间范围、英文边界、段落边界和背景角色边界。插件 ZIP 仅含清单与 DEX 代码，不含 Android 资源表或原生库。

下一阶段的完整第三方歌词插件需要独立的曲目信息、异步注入、回收与补充轨道适配；离线 MLKit 还需要独立伴随 APK 或 SDK 原生库能力。当前正式项目的完整翻译/发音功能继续保留。

SDK 输入 SHA-256：`a0471a9b3480f4e90451402cfd9ed18cec50fccc24f07ccf4b89088911401341`。
