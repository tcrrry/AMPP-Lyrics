# 1609 共存版背景与媒体通知回归排查

2026-10-14。以下正文保留最初静态排查时的结论；随后用户已确认路由修复恢复背景。播放器另需 vivo 发现声明和设备通知设置，原子岛未恢复且用户暂不继续处理。最新原因、实机证据与防回归门禁见 [播放器回归说明](COEXIST-PLAYER-REGRESSION-PREVENTION.md)。

## 版本对照

- 用户确认：v1.6 / Apple Music 1606 共存版正常；v1.7 / 1609 启动播放、尚未打开歌词设置时就可能灰底，同时状态栏与 vivo 原子岛不再显示歌曲。
- v1.6 在 `onPackageReady` 注册 `CoexistenceRoutingRuntime`。它将宿主对自身的显式 Intent、Provider authority、content URI 以及资源查询映射到共存安装身份。v1.7 合并上游时这份代码及注册调用均缺失。
- 两版共存 APK 的 Manifest 均以 `com.tcrrry.ampplyrics.coexist` 安装，资源表仍属于 `com.apple.android.music`。两版均声明 `POST_NOTIFICATIONS`、媒体播放前台服务权限；`MediaPlaybackService` 仍注册媒体浏览、MediaSession、媒体按钮入口。
- 1609 原始宿主 DEX 的 `ArtworkContentProvider` 仍把 `com.apple.android.music.provider.ArtworkProvider` 写死后传给 `UriMatcher.addURI`；共存 Manifest 的 authority 是 `com.tcrrry.ampplyrics.coexist.provider.ArtworkProvider`。缺少运行时重定向会使两者不一致。这是已确认的静态缺陷；是否涵盖手机上全部灰底情况仍待验证。
- v1.7 还新增了原生播放背景生命周期修复钩子，v1.6 没有。之前尝试增强此钩子的背景预览包没有解决用户的问题，相关未发布改动已撤回。本预览包保留上游原实现。
- 玻璃层资源查询中也有以安装包名查原始资源的代码，但 v1.6 已有同样写法；它不能单独解释此次新回归。恢复的 v1.6 路由会同时处理这些查询。

## 修复范围

从 v1.6 恢复 `CoexistenceRouting.kt` 和对应策略测试，在 v1.7 的 `HookEntry.onPackageReady` 中按原时机注册。原包构建由路由自身检查直接跳过。歌词插件维持 `1.0.7-preview.3` / versionCode 25，设置页与滚动行为不变。

## 桌面验证

- `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug -PamppCoexistence=true --offline --max-workers=2` 通过。
- 共存路由策略测试覆盖 Provider、content URI、资源包名、自身显式包名和外部地址不变。
- 使用正式 v1.7 共存包的原始宿主重新嵌入模块；打包检查了宿主原始内容、签名、原生库、包名、布局类和内置插件字节。
- 预览 APK：`C:\Users\28411\Desktop\AMPP-Lyrics-1609-coexist-routing-preview-1.apk`，142278785 字节，SHA-256 `b8745fa56cf53fc89712178f7905a591da3bc0633d9bf5a41b184beeac855ba0`。
- 待手机验证：冷启动首曲背景、连续切歌背景、播放通知、媒体按钮、vivo 原子岛，以及从通知返回是否进入共存实例。静态检查不能替代这些结果。

正式 v1.7 发布附件未更改。`build/background-hook-isolation/` 是已放弃的单变量诊断包，不作为交付物。
