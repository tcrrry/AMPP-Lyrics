# v1.7 整合设计

固定 AM++ 1609 上游源码与 SDK v1，不把旧版反射名直接套用到新宿主。主项目旧提交保留为整合提交的祖先，旧发布不变。

BundledLyrics 在 PluginManager 后台 prepare 阶段通过原作者 PluginStore 导入自有插件，之后才启动插件。保留更新版本和停用状态；删除的插件不每次重新出现。自有 preferences 与 TTML 迁入 SDK 数据目录，不覆盖已有数据。密钥在 AndroidKeyStore 中解密并重新加密，明文不落盘、不上传；原加密文件保留供失败后重试。

上游歌词替换只在运行时 reader 中屏蔽，原保存值不重置。插件 code.jar 与独立 1.0.6 一致。PluginContext 仅针对自有插件 update-channel.json 提供主程序版本，其余插件与资源不受影响。

主模块通过 parent-first 类加载提供自有 com.tcrrry.lyrics.plugin.OfflineTranslationClient 的兼容实现，公开方法签名不变。ML Kit、原生库、模型资源由主模块拥有；独立 ZIP 仍使用伴随客户端。SDK 生命周期与 Hook 注册约束保持不变。

普通/共存分别编译，安装包名与类/资源命名空间分离。只对已知共存包归一化档案，不放宽未知包和版本。APKEditor 合并原始分包并调整最低 API，NPatch 嵌入模块，CoexistenceManifest 处理身份与相对布局行为；DEX、资源标识和原生库分别验证。

Windows 用现有 SDK 和项目 gradlew.bat 运行 test、:app:lintDebug、:app:assembleDebug；保存普通模块，再以 -PamppCoexistence=true 构建共存模块。使用 scripts/package-1609-release-v1.7.py --help 提供固定 1012 APKS、两种模块、APKEditor、NPatch、aapt2、apksigner 和 javac 路径。编译用 JDK 17，打包用 JDK 21。

输入/工具 SHA-256 与宿主签名在打包脚本内固定。源码和自动验证不是所有设备已实测的声明。
