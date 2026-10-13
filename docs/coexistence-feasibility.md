# 与官方 Apple Music 共存的可行性评估

评估日期：2026-10-03。当前结论：按用户要求提供独立共存 APK 测试选项，保留现有 APK / APKS / 模块 APK。共存测试版尚需用户实机验证，不作为默认推荐包。

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

目前收益主要是免于卸载官方版，代价是新增一套身份适配和长期回归测试。现按用户要求先提供隔离的备用测试包，再依据实机反馈决定是否继续维护。

## 共存测试版的实现范围

- 安装包名为 `com.tcrrry.ampplyrics.coexist`，显示名称为“AM++ Lyrics 共存测试版”；普通 APK/APKS 的包名和附件保持不变。
- 移除 shared UID，隔离 Provider authority 和自身声明的权限名；使用独立应用数据目录，需要单独登录。
- 模块采用独立 coexist 构建：只接受共存包身份，仍按 Apple Music 6.5.3 精确版本适配；资源命名空间与类名保留原值。普通 debug 模块仍只接受原包身份。
- 在共存进程内重定向原包自身的显式 Intent / ComponentName，以及访问原包 Provider 的 content URI。不会重写 Apple HTTPS 认证网址或第三方 Provider。
- 共存版移除 BROWSABLE 链接过滤器，避免争抢官方版的外部链接和音乐 SDK 认证入口。请从共存版自己的图标进入；外部链接和第三方音乐 SDK 登录不属于此测试版的支持范围。
- 原始 Apple Music DEX 与 arm64 原生库不修改。单元测试、签名与文件校验仅覆盖代码和静态结构，不证明账号登录、DRM、离线下载或双实例媒体控制已经可用。

## 用户实机验证

保留现有原版/普通增强版，直接安装共存测试 APK。确认出现独立图标且原应用保留；随后在共存版内单独登录，验证在线播放、歌词来源菜单、设置、下载及离线播放，再返回原应用确认功能正常。分别从两个应用的通知进入播放器，确认没有打开另一实例。若移除共存版，应仅删除共存实例及其本地数据。

## 第二版修正与用户反馈

用户反馈初版可以登录，但列表文字重叠、封面空白，歌词页显示“尚未收到匹配输入”。用户随后确认：在 AM++ 设置开启“自定义歌词替换”后歌词匹配恢复，原因是共存版独立配置的开关未开启，不是 QQ／网易云未接入。

第二版 `coexist-test-r2.apk` 为动态资源查找补充包名兼容：安装身份仍独立，查询共存包资源时转到保留的原资源命名空间。这针对改包名后的布局资源解析问题，显示效果需实机复测。反编译内置布局代码确认其通过当前 Context 包名解析约束 ID；内置 ArtworkContentProvider 又用原 authority 注册 UriMatcher，与已经重定向的共存 URI 不一致。第二版同时隔离 UriMatcher 注册的自身 authority，修正封面 Provider 匹配冲突。单个运行时路由 hook 安装失败会记录日志并继续安装其他 hook。

共存版有独立配置，首次安装“自定义歌词替换”默认关闭，不继承普通版开关。第二版歌词页显示关闭提示并提供设置入口，“查看匹配输入”增加 Apple Music ID 和两个开关状态。请开启“自定义歌词替换”和“自动实时补全”，保存后完全退出重开，再播放歌曲、打开歌词页测试。保留默认开关行为，避免未经选择改变用户配置。

第二版与初版共存测试包的安装身份、签名相同，可以覆盖升级共存测试包；普通版附件保持不变。资源兼容和诊断改动通过构建验证后发布，账号、播放、封面与歌词仍需用户复测。

用户另反馈：普通版正常；共存初版点击资料库的播放列表、艺人、专辑、歌曲等分类入口均立即闪退，而从“最近添加”点击专辑可进入；暂无崩溃堆栈，不能确认与资源/Provider 问题同源。第二版需要复测全部资料库分类入口，若仍闪退应依据 AndroidRuntime 崩溃日志继续定位。

## 第三版：资料库分类页布局修正

用户反馈最新共存 r2 中具体专辑详情可打开，但资料库“播放列表、歌手、专辑、歌曲”等分类入口会闪退。检查已发布 r2 的内嵌宿主发现，共用的 `library_details_page_fragment` 使用 `.collection.mediaapi.fragment.ScrollConfigurableAppBarLayoutBehavior`；CoordinatorLayout 会按 Context 的安装包名补全相对类名，得到不存在的 `com.tcrrry.ampplyrics.coexist.collection...`。宿主类名仍在 `com.apple.android.music` 下。

r3 在共存打包时将 `layout_behavior` 中以点开头的宿主类名补全为原始完整类名。实际 r2 底包回归检查发现 3 个布局中的 4 处需要补全：资料库分类页、专辑布局与播放列表布局。修复前校验失败，修复后通过；比较 ZIP 条目确认此步骤仅改变这 3 个布局，DEX、资源表、Manifest 与原生库未改变。

`VerifyCoexistenceLayouts.java` 检查最终 APK 及 NPatch 内嵌 origin，拒绝残留的相对行为类名，并确认资料库分类页保留正确的滚动行为类。原有签名、包名、资源 ID、宿主 DEX 和嵌入模块校验继续执行。

新文件为 `AMPP-Lyrics-AppleMusic-6.5.3-arm64-coexist-test-r3.apk`，上传至 v1.2；不替换 r2 或普通 APK/APKS/模块附件。可覆盖安装相同签名和包名的旧共存版。共存版新安装仍需手动开启自定义歌词替换；已经保存的配置保留。静态缺陷已修正，所有分类入口是否恢复仍需用户实机复测，不能以构建或静态校验代替手机测试。

验证记录：[r3 共存构建 Actions](https://github.com/tcrrry/AMPP-Lyrics/actions/runs/37112457362) 与[完整 CI](https://github.com/tcrrry/AMPP-Lyrics/actions/runs/37112457529) 均成功。最终 APK 及内嵌 origin 的布局检查通过；仅追加 r3 APK 和校验文件，发布前后逐项比对确认旧附件 ID、大小与摘要不变。实机复测仍待用户反馈。
