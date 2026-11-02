# External Media SDK v1 preview

基线是 1017 稳定性修复提交 `03fa989`。正式主项目、原有发布包、歌词与翻译插件均未修改。

## 分层

1. 宿主 APK 预声明一个默认关闭的 `ExternalMediaService`。本次包含标准 MediaBrowser 和 vivo 发现 action 模板。
2. `PluginContext.getExternalMedia()` 是兼容 SDK v1 的可选 default 方法。旧的 SDK v1 实现得到 UnsupportedOperationException，旧插件的原有方法描述符保持不变。
3. 插件通过 `registerEndpoint` 独占接口。宿主负责组件启停、现有 MediaSession 观察、元数据装饰、浏览目录与回调分发。没有创建第二个 MediaSession，也不替换原生 MediaItem 或播放状态。
4. vivo 的 support_event、歌词字段、循环模式、分页目录、Apple Music 1609 控制器契约均在独立 vivo 插件中。

Android 不能由 ZIP 动态添加任意 Manifest 组件或 intent-filter。通用 SDK 可复用，但未来品牌若有新的发现 action，仍需宿主增加相应的预声明模板。此预览不声称已经支持 OPPO 或小米。

## 宿主安装包构建

源代码是 AM++ 嵌入模块，而非 Apple Music 原生 APK 工程，因此仅在模块 Manifest 加 service 不足以让 Android 找到它。本预览将 framework-only shim 的 DEX 加入外层宿主和 `assets/npatch/origin.apk` 两个加载路径。

1. 构建 `:app:assembleRelease` 和 `:plugin-api:exportSdk`，构建 `:vivo-plugin:assembleRelease`。
2. 用 Android 37.0 `android.jar` 编译 `host-proxy/src/main/java`，D8 使用 `--min-api 30`。
3. `tools/ExternalMediaManifest.java` 依赖 APKEditor 1.4.9。参数：原始稳定版 base.apk、临时 APK、导出的二进制 Manifest。
4. `tools/ReembedExternalMedia.java` 参数：原始稳定版 base.apk、新模块 APK、shim DEX、导出的 Manifest、工作目录、输出 APK、zipalign 可执行文件。内嵌 origin 重写后也会先做 16 KB / 4 字节对齐。
5. 该工具逐项比较未修改资源，保留 NPatch 832 的全部原 payload，只替换内嵌模块、内嵌 origin、外层 Manifest，并分别增加一个 shim DEX。原 MediaPlaybackService 与全部旧权限和组件保持一致。
6. zipalign 使用 `-P 16`。以与已安装宿主相同的合法签名身份签名，再同原 arm64 和 xxxhdpi split 打包 APKS。本地预览使用现有 NPatch 公开测试身份；源代码中不包含签名秘密。

验证包括 Manifest 语义树对比、双加载路径 DEX 定义、逐 entry 资源比对、SDK ABI 兼容探针、PluginStore 真实 ZIP 导入、播放器控制/过期队列条目测试、歌词文字选项/缓存边界测试、已有宿主回归与 lint。

## 设备验收仍待完成

- 覆盖安装宿主，插件管理器导入 vivo ZIP 并启用。
- 打开一首有歌词的歌，确认原子随身听显示正确歌词及进度。
- 切歌后核对歌词歌曲 ID，切换两个文字显示选项。
- 核对循环模式三种状态在 AM 与系统间同步。
- 打开当前播放列表选歌，再改变 AM 队列，确认旧列表不会误选歌曲。
- 禁用扩展，核对接口取消、AM 仍能正常播放、歌词增强原有功能保留。

桌面检查和 USB 只读会话查询均不代替上述设备验收。安装或重启需要用户执行/明确授权，未自动操作。

## preview.3 连接恢复

- 正常进程重建时不再先禁用已启用的发现组件。全部插件加载结束后，若没有活动接口，才清理旧组件状态；显式停用插件仍会关闭组件。
- Manifest 的 `ampp.external.media.bootstrap_root` 和 `ampp.external.media.bootstrap_clients` 声明允许等待初始化的根目录和调用方。服务先核对包名所属 UID；没有活动插件时只有声明过的客户端可以进入等待。
- `ConnectionAuthorization` 会在插件就绪后重新核对每个等待客户端的根目录，核对通过后才发布原生 MediaSession token。正常使用 MediaBrowserService 先返回根、后调用 setSessionToken 的异步连接机制，没有轮询或伪造系统广播。
- `host-proxy/tests/.../ConnectionAuthorizationTest.java` 覆盖提前重连、延迟 token、运行时拒绝、根目录变化、包名冒用和未声明客户端等场景。它是 JVM 策略测试，不能替代真机重开 AM 的连接验收。
- 更新已含 shim 的宿主使用 `tools/UpdateExternalMedia.java`，替换现有外层 classes3.dex 和内嵌 origin 的 classes5.dex；禁止追加重复定义。其余 NPatch 和原生载荷逐项校验。
