# 与官方 Apple Music 共存的可行性评估

评估日期：2026-10-03。当前结论：技术上可探索，但不能仅改包名就得到可靠的共存版；先保留现有 APK / APKS，不制作或发布共存包。

## 已确认的约束

- 当前整合包的 Manifest 声明 `sharedUserId="com.apple.android"`。在仍采用 shared UID 的系统上，官方版与重签名应用不能以不同签名加入同一 UID；只改包名不能解决这一问题，需要处理 UID 隔离及相关权限依赖。
- Manifest 包含 `com.apple.android.music.provider`、ArtworkProvider、WidgetImageProvider、`com.apple.android.music.ams.stable` 等 Provider authority。共存包必须隔离 authority，并核对应用内访问这些 Provider 的 URI，避免安装冲突或访问到官方实例。
- Manifest 注册 `musicsdk://applemusic/authenticate-v.*` 及 Apple Music / iTunes 链接。两个实例存在相同入口时，外部认证回调和链接可能打开错误实例；是否影响内部账号登录，需要实机验证。
- 模块的 `ModuleConstants.TARGET_PACKAGE` 固定为 `com.apple.android.music`。`EmbeddedBootstrap.prepare/supports`、`HookEntry` 主进程判断、`EmbeddedSettingsHost` 和版本查询都依赖该目标；直接改应用包名可能导致增强模块被跳过或查询官方版信息。
- 同一常量也用于资源查找。安装包名、原始类名和资源命名空间不是同一概念，不能将所有 `com.apple.android.music` 字符串全局替换；否则原始类或资源定位可能失效。

NPatch 1.0.7（741）提供 `--newpackage`。检查对应的 Manifest 修改代码，它支持改包名和映射 Provider authority，但这不构成上述 UID、应用内部 URI、模块目标识别与外部回调均已兼容的保证。

参考：模块源码 `ModuleConstants.kt`、`EmbeddedBootstrap.kt`、`HookEntry.kt`、`FeatureInstallation.kt`、`EmbeddedSettingsHost.kt`；当前 v1.1 单 APK 的 Manifest；[NPatch v1.0.7 源码](https://github.com/7723mod/NPatch/blob/v1.0.7/patch/src/main/java/top/nkbe/patch/NPatch.java)。

## 稳定性判断

共存至少需要维护独立应用身份、Provider / UID 隔离和模块适配。Apple Music 的账号设备标识、原生库、DRM 和离线下载流程是否依赖原身份尚未验证；这些是待验证风险，不能据此认定必然失败，也不能因安装成功就认定稳定。

若以后重新考虑，验收必须覆盖：官方版与共存版双向安装顺序、分别登录与切换账号、在线与离线播放、下载、歌词增强、外部认证回调、通知和媒体控制、升级及独立卸载。任何一项串到另一实例，或要求不可靠的运行时身份伪装，都不适合提供给普通用户。

目前收益主要是免于卸载官方版，代价是新增一套身份适配和长期回归测试。按当前优先保证稳定性的目标，暂缓共存版。
