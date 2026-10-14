# vivo 原生播放器与原子岛预览

日期：2026-10-14。仅预览。覆盖安装后已验证原生播放器显示恢复；原子岛未恢复，用户决定暂不继续处理原子岛。

## 现场结论

- 设备：vivo V2359A / PD2309；无线 ADB 独立服务端口 5038。
- 已安装的共存包与 routing-preview-1 的 SHA-256 相同，用户确认播放背景恢复。
- 共存版 Android MediaSession 已激活并处于 PLAYING，歌曲元数据与媒体按钮会话正确。
- 恢复 POST_NOTIFICATIONS 后普通媒体通知出现，但 vivo 控制中心播放器仍显示“未在播放”。普通通知不等于原生播放器或原子岛成功。
- 已保存设置快照；原生通知中心播放器开关由 0 改为 1，仍未解决。
- 系统音源白名单已包含共存包，但查询 `com.vivo.musicwidgetmix.support.service` 返回 No services found。
- 对同版本 vivo MusicWidgetMix 6.2.7.2 的静态检查显示：未知包名的合作能力判断依赖该服务发现列表。“请升级至最新版”是兼容性提示，不足以证明 Apple Music 版本过旧。

现场记录与截图：`release-audit/coexist-media-2026-10-14`（工作区上级目录）。

## 最小改动

在已有、已导出的 `com.apple.android.music.player.MediaPlaybackService` 的浏览器 intent-filter 中增加：

```text
com.vivo.musicwidgetmix.support.service
```

不新增服务或后台伴随应用，不改媒体协议、歌词代码、包名或版本号。保留浏览器 action；保持共存隔离、routing-preview-1 背景修复以及已验收的设置页与滚动行为。

脚本：`scripts/package-coexist-vivo-preview.py`、`scripts/VivoAtomicManifest.java`。

## 构建校验

- 输入 SHA-256：`b8745fa56cf53fc89712178f7905a591da3bc0633d9bf5a41b184beeac855ba0`。
- 内嵌模块逐字节不变：`d4c84101e2350c3e4d611ca75042d463be6c6adf7c89c35efef2d3f3bd9e2ab8`。
- 宿主所有非签名 ZIP 条目仅 AndroidManifest.xml 改变，DEX、资源、库和资产不变。
- 最终包与内嵌 origin 均验证 vivo action 唯一且位于原有导出浏览器服务中。
- 签名证书、完整 NPatch 配置和库文件不变；共存包 1609/minSdk31 不变。
- 最终与内嵌宿主的所有布局 behavior 类名校验通过。
- 输出：`build/coexist-vivo-preview-1/AMPP-Lyrics-1609-coexist-vivo-preview-1.apk`。
- 输出 SHA-256：`16e977a7b04eddbc823b8fdb9a4937ef97aa99133eab4e3fef2b511e8a6b2272`。

## 待验收

仅传文件，由用户自行覆盖安装，不卸载、不清除数据、不自动重启 Apple Music。安装后检查 PackageManager 服务发现、vivo 与共存版的浏览器连接、原生播放器歌曲信息和控制，以及桌面播放时的原子岛。服务声明或文件传输成功不代表以上功能已修复。若仍失败，继续检查 vivo 缓存、控制器选择和连接协议，不盲目提高版本号或替换系统白名单。

不修改正式发布包，不将此预览视为正式发布。

## 覆盖安装后的实机结果

- 用户自行覆盖安装并开始播放，已安装 APK SHA-256 与上述预览一致。
- PackageManager 已能通过 vivo action 发现共存版的原有 MediaPlaybackService。
- `dumpsys activity services` 确认 `com.vivo.musicwidgetmix` 已绑定共存版的 MediaBrowserService；Android 会话为 PLAYING。
- 控制中心原生播放器与桌面原子随身听均显示《君のとなり》、MIREI、封面及播放控件。此次只检查显示和连接，未点击播放控件以免中断播放。
- 实际桌面截图未出现顶部原子岛；系统原子岛显示开关在设置页中已开启，并非开关未开。
- 同版本系统 APK 的 `s4.m0.g()` 固定初始化原子岛名单，含 `com.apple.android.music`，不含自定义共存包名；`MusicWidgetMixService.m0()` 与 `s4.e.v()` 在此名单之外跳过包名更新/通知。普通音源白名单与服务声明不能等同于这份名单。
- 这是当前原子岛失败的明确静态阻碍，不声称排除了系统的所有其他显示路径。
- 2026-10-14 用户表示“做不到就算了”，停止原子岛适配，不改系统 APK、不更换共存包名、不新增常驻软件。保留已工作的播放器修复，不发布新的正式版本。

本轮快照：`vivo-preview-installed-playing.json`；截图：`vivo-preview-control-center.png`、`vivo-preview-home.png`、`vivo-preview-source-settings.png`，位于前述现场记录目录。
