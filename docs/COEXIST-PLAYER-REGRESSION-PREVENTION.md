# 共存版播放器回归：原因、修复与发布检查

日期：2026-10-14。对象：`com.tcrrry.ampplyrics.coexist`，Apple Music 7.0.0-beta / 1609。

正式发布接入：v1.8 使用 `scripts/package-1609-release-v1.8.py`，沿用下文门禁并验证最终共存 APK 与 origin。历史预览验收不等于新正式二进制已全面实测，版本更新结果见 `release-v1.8.md` 与发布附件 `v1.8-validation.json`。

## 结论

这次不是歌词设置页把播放器画没了。现场存在两个独立阻碍：手机关闭了共存版通知权限；安装包缺少 vivo 用来发现合作音源的服务 action。恢复通知权限只恢复普通媒体通知，补齐服务声明后 vivo 原生播放器才恢复显示。

升级时还丢失了共存路由，导致播放背景灰底；这是另一个回归，不能与播放器或原子岛混为一谈。

## 为什么以前能用，这次又失效

1. 早先私有 vivo 测试流程已经有 `VivoAtomicManifest.java`，但它留在 `release-audit/install-diagnosis/private-r2-release/source/scripts`，没有纳入当前主项目的 1609 正式发布流程。
2. 当前 `package-1609-release-v1.7.py` 原先只做包名/Provider 隔离后重新嵌入模块，没有执行 vivo 声明补充。新宿主打包不会自动继承旧 APK 中的清单修改。
3. 原有打包校验覆盖签名、包名、DEX、资源、布局和模块完整性，未把 vivo 服务发现作为必须通过的条件。因此“构建和静态检查通过”仍放过了这个遗漏。
4. 手机现场另有通知权限被关闭：`POST_NOTIFICATIONS=false`、AppOps ignore、通知 importance NONE。没有证据证明这是本次升级主动重置的，不能把用户/系统权限状态写成代码根因。

历史边界：用户反馈 v1.6 曾正常，但本地检查的正式 v1.6 和正式 v1.7 共存包均未包含该 vivo action；早先私有测试包包含。当前无法还原过去正常时的完整安装包和系统状态，因此不能声称“正式 v1.6 一定带了声明，v1.7 才删除”。能够确认的是：当前失败包缺少声明，正式打包流程没保留已有私有适配；本次补齐后原生播放器显示恢复。

## 实际如何修复

### 1. 恢复共存路由，修灰底

恢复 v1.6 的 `CoexistenceRouting.kt` 及策略测试，在 `HookEntry.onPackageReady` 初始化时注册。处理自身显式 Intent、Provider authority、content URI 与资源命名空间；不改外部应用目标。用户确认背景恢复。详见 [背景回归记录](COEXIST-1609-REGRESSION.md)。

### 2. 恢复手机通知权限

先保存快照，再对共存版授予 `POST_NOTIFICATIONS`。普通媒体通知恢复，但此时 vivo 原生播放器仍显示“未在播放”。这是设备设置修复，不是可写入 APK、替所有手机永久授予的权限。

原生通知中心播放器开关也由 0 恢复为 1。仅打开该开关仍未解决服务连接问题。不清空系统音源列表，不重复注册已有共存音源。

### 3. 补齐现有媒体服务的 vivo 发现入口

在原有、已导出的 `com.apple.android.music.player.MediaPlaybackService` 的浏览器 intent-filter 内增加唯一 action：

```text
com.vivo.musicwidgetmix.support.service
```

保留 `android.media.browse.MediaBrowserService` 和其他原有媒体入口。不新建服务，不增加常驻软件，不改包名或版本号。独立歌词 ZIP 无法给已安装宿主增加这项 AndroidManifest 声明。

以背景已恢复的 routing-preview-1 为底包，仅改变内嵌宿主 AndroidManifest.xml，复用完全相同的模块与歌词插件字节，然后重签/嵌入。最终与内嵌 origin 均检查声明所在服务、导出状态和浏览器 action；签名、NPatch 配置、DEX、资源及原生库保持不变。

## 本次实机证据与未完成项

- 用户自行覆盖安装 `AMPP-Lyrics-1609-coexist-vivo-preview-1.apk` 并播放。
- 已安装包 SHA-256：`16e977a7b04eddbc823b8fdb9a4937ef97aa99133eab4e3fef2b511e8a6b2272`。
- vivo action 查询从 `No services found` 变为发现共存版原有 MediaPlaybackService。
- 系统服务记录确认 `com.vivo.musicwidgetmix` 已绑定该浏览器服务，Android 会话为 PLAYING。
- 控制中心原生播放器、桌面原子随身听显示《君のとなり》、MIREI、封面和播放控件。未点击控件，不把显示验收写成暂停/切歌操作验收。
- 顶部原子岛未恢复。系统对应名单包含原版 AM 包名，不包含共存版自定义包名；用户决定暂不继续处理。不得把原生播放器成功写成原子岛成功，更不能宣称原子岛歌词已支持。
- 实测没有覆盖重启手机后的冷启动、其他品牌和其他安装形式。

快照和截图：工作区 `release-audit/coexist-media-2026-10-14/`。详情见 [本次预览记录](COEXIST-VIVO-PREVIEW-1.md)。

## 防止再次遗漏

### 已加入的打包门禁

`scripts/package-1609-release-v1.7.py` 现在按以下顺序处理未来构建：

```text
合并宿主 -> 共存身份隔离 -> VivoAtomicManifest 补声明
-> 检查只有 AndroidManifest 改变 -> NPatch 嵌入
-> 对最终 APK 与内嵌 origin 执行 VivoAtomicManifest --verify
```

缺少 action、重复 action、错误共存包名、服务未导出或缺少浏览器入口，任一条件不满足就失败。既有签名、资源、库和布局校验继续保留。此次只改未来构建脚本，不重建或覆盖已验收 APK，不替换正式发布附件。

门禁实测：对缺失声明的 routing-preview-1 执行校验，按预期失败；对用户已安装的 vivo-preview-1 执行校验，通过。Java 校验器编译、打包脚本语法检查与 `git diff --check` 均通过。本轮未重新运行完整四包构建，也未追加播放控制实测。

项目 `AGENTS.md` 与 `scripts/README.md` 已链接/列出这条发布约束，便于以后接手升级时先看到。

### 以后每次升级宿主或发布共存包

- 不仅搬歌词代码，还要逐项检查共存路由、注册时机、清单变更和打包步骤是否保留。
- 正式共存包必须走上述门禁；采用其他打包脚本时也必须对最终 APK 和 origin 调用相同验证，不能只检查源码字符串。
- 保留已验收预览包的 SHA-256；如正式包重新构建，应明确它是新二进制，需要重新验收。
- 手机检查通知权限、音源注册和原生播放器开关；保存改动前状态，不全量覆盖系统白名单。
- 在实际共存版播放后，查询 vivo 服务发现及绑定，再检查控制中心和桌面随身听的歌曲、封面，最后单独测试暂停/恢复/切歌与冷启动。
- 原子岛作为独立能力记录结果，当前不承诺共存版支持。原包、APKS、模块和插件 ZIP 的结果不能直接互相套用。

## 责任与范围

这次流程的问题是已有适配没有进入主发布路径，而且缺少对应的二进制门禁；不能把责任归给用户设置，也不能仅靠“下次注意”解决。上述主流程补声明与最终产物校验用于防止同一种遗漏；系统版本、协议变化或权限变化仍需单独实机检查。
