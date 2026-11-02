# AM++ 1.6.8 / 1614 稳定性与 vivo 预览 1

主项目 v1.11 稳定性修订，基于 AM++ 1.6.8 / Apple Music 7.0.0（1614），覆盖普通版、共存版、APKS 与独立模块。

## 闪退记录与处理

手机历史崩溃记录显示 11 月 2 日多次资源读取异常：drawable 的 ComplexColor type=0x5、color/background_color 的 type #0x5，以及启动 MainActivity 时 TypedArray 颜色转换 type=0x5。它们与此前资源类型错误现象一致，但日志不能单独确定底层根因。

移植此前有限范围的独立 AssetManager 资源恢复与旧版 drawBackdrop 二进制接口。针对 TypedArray.getColor，只有能取得属于 com.apple.android.music、且声明类型为 color 的精确资源 ID 并成功读取时才恢复；无 ID、非本应用资源、类型不同或重试失败时保留原异常。启动主题 TypedArray 的实际 ID 是否可获取尚未验证，因此不承诺覆盖该次崩溃或消除所有闪退。

## vivo

移植前版 External Media API、宿主服务、双加载路径 shim、连接授权以及独立 vivo 插件；1614 原包对照前版方法及字段检查 40 项通过。此接口沿用 AM 原生媒体会话，非替换播放器。播放、歌词、翻译、循环、当前队列、收藏、已下载和重启恢复均须实机验收。

## 验证

模块、插件构建及 lint 通过，宿主、原生兼容、插件运行时与 vivo 单测通过；作者 PluginStore 的真实歌词 ZIP 和 vivo ZIP 导入测试通过。旧 SDK 入口提示检查、延迟连接授权、真实宿主 SDK API 和歌词插件玻璃 ABI 检查通过。APKS 原始分包字节保持一致，外层及 origin 保留既有载荷并增加 shim，外层仅增加 ExternalMediaService；清单语义比较通过。签名与输入相同，并通过 16KB zipalign。

同一 v1.11 发布页保留更新前的附件备份。需要 vivo 完整功能时另行在插件管理器导入并启用 vivo ZIP；模块 APK 不能自行向宿主增加服务声明。仅生成和传输文件，未代为安装、重启或清日志。
