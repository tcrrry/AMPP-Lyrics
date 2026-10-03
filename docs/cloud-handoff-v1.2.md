# v1.2 发布交接

以公开仓库 main 的 a3c9fb3 为基线，分支 `fix/v1-2-default-lyrics`。共存 r2 和所有已发布附件保留。普通新安装默认开启自定义歌词，已有保存的关闭选择保留；两个设置页配色移到末尾；真实逐字计时仍用 Word，普通歌词使用 Line；自动歌词缓存升到 v11。

本地 `:app:assembleDebug --offline --no-daemon` 成功。使用 SHA-256 为 `e08466d26ba06c55b8f491a52e4c7bd383602f68cb3ce06af1182c0167123f10` 的公开 v1.1 APKS，更新模块后打包 APKS 和单 APK；签名、嵌入模块、单 APK 宿主 DEX、资源、原生库、包名及分包标记静态校验成功。未做手机实机验证。

完整测试未完成：离线缺少 Robolectric 4.14.1，在线 Maven Central 返回 HTTP 429。不得将此记录视为测试通过。

发布尚未执行：原生 Git push 缺少可用认证，现有 GitHub CLI 绑定推送返回 HTTP 401。需恢复 Codex 对 tcrrry/AMPP-Lyrics 的 GitHub 写入授权。不要请求签名私钥，也不要覆盖历史发布。

恢复授权后推送本分支，`publish-v1-2.yml` 会执行完整测试、三模块 Lint 和构建，重新生成三个常规包及校验和，创建 v1.2，并核对 v1 / v1.1 的附件未变。通过后设为最新并快进 main。失败时查阅 Actions 和它写入的 `docs/v1.2-validation.json`；发布成功后检查三个 latest/download 入口均指向 v1.2，共存链接仍固定为 v1.1。
