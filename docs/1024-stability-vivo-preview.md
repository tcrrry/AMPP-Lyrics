# 1024 稳定性与 vivo 适配预览 1

基线为作者 1024：AM++ v1.6.7 / 3ff083e09452a551cf6c91929e3eda9ea376d68f，Apple Music 7.0.0-beta / 1613。独立工作目录 AMPP-1024-Stability，保留旧 1017 工程，不改歌词插件或主项目。

## 闪退相关

从已被用户报告明显改善闪退的 1017 修复提交 03fa989 移植两项补丁：补回旧插件使用的 drawBackdrop 二进制重载；只对 Apple Music 1609、1613 的特定资源类型转换异常启用独立 AssetManager 读取恢复。正常资源读取、第三方资源、类型使用错误和未识别异常均保持原样。恢复失败保留原异常。

这属于已知兼容修复及异常缓解，不代表所有闪退都能杜绝。本次未连接设备，无法证明当前残余闪退仍是相同原因，也没有执行实机安装。

## vivo 接入

- 加入兼容 SDK v1 的可选 External Media API、生命周期管理、媒体会话装饰和歌词缓存。
- 外层 Manifest 仅新增默认关闭的 ExternalMediaService，含标准 MediaBrowser 与 vivo 发现 action、启动客户端和根目录元数据；原有组件、权限、版本、target SDK 均不变。
- Framework-only shim 以相同 DEX 同时加入外层宿主与内嵌 origin 的两个加载路径。
- 保留启动早于插件加载时的延迟授权／原生 token 就绪机制，不新增播放器或 MediaSession。
- vivo 0.2-preview.1 支持 1613，保留 1609。入口在旧 SDK 下能独立加载并提示宿主要求；实现只在扩展 API 存在时加载，避免缺失 PluginMediaEndpoint 被误报为入口类缺失。

## 打包与验证边界

原版 1024 APKS 的 SHA-256 为 749526ee1240375f3d69ec1332b6843e2da41196258b1aa3f07d07bb943c33b8。重打包只替换嵌入模块、内嵌 origin 和外层 Manifest，并各增加一个 shim DEX。4937 个外层非修改载荷、4936 个 origin 非签名载荷逐项比对一致；两个原始 split 保持字节不变。

主包以原有 NPatch 公开测试身份签名，证书 SHA-256 为 08b00b38cd98762bf261952c2c1014c09208ac2c278fd3085421994a516c3e23，匹配作者原始主包。分包应按整套宿主实际最低 API 30 验证，而非单独按无 uses-sdk 分包的默认 API 1 验证；按 API 30 原始分包可通过，无须重签。内嵌模块为本地构建的测试签名，不冒充作者签名。

已做单元测试、Lint、模块构建、215 项歌词/播放模式接口基线核验、vivo 命名接口核验、真实 PluginStore ZIP 导入、Manifest 语义树对比、SDK/入口类/双路径 DEX 检查、APK 签名与对齐检查。具体计数与文件哈希见配套 validation.json。

原生资源恢复测试和 JVM 连接/加载测试不等于 1024 手机端闪退、歌词、循环或选曲已验收。安装后应复测更多菜单、设置返回、正常播放、切歌、歌词以及原子随身听。
