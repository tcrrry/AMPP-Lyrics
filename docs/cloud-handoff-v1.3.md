# v1.3 构建记录

宿主：Apple Music 7.0.0-beta / 1606，来源为上游 embedded-2026.08.10-r1 的 103 分包。
源 APKS SHA-256：`1adae4761b292221189bd47f2f302fe48c7bb45863b2581663ed5efba9146093`。

## 验证

普通模块 1,095 项、共存模块 1,095 项、glass 22 项测试通过；Lint 与构建通过。入口分流、生命周期回调撤销、共存精确版本识别有回归测试。原宿主 262 项接口与 39 项发音／菜单契约通过。四种产物的身份、签名、原包与模块嵌入、DEX、原生库、资源及共存布局行为通过静态校验。实机反馈仅覆盖此前普通测试版；v1.3 新包仍需实机反馈。

## 重现

使用 Android SDK 37、完整 JDK 17 构建。分别运行并保存普通和共存模块，不能让后一次构建覆盖前一份输入：

```bash
bash gradlew test :app:lintDebug :glass:lintDebug :glass-lab:lintDebug :app:assembleDebug --no-daemon --max-workers=4
cp app/build/outputs/apk/debug/app-debug.apk /tmp/ordinary-module.apk
bash gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -PamppCoexistence=true --no-daemon --max-workers=4
cp app/build/outputs/apk/debug/app-debug.apk /tmp/coexist-module.apk
```

打包使用 JDK 21 的运行时；`--javac` 指向 JDK 17。输入和工具的固定 hash 由脚本强制校验：

```bash
python3 scripts/package-700-release.py \
  --input /path/to/103-version-com.apple.android.music-npatched.apks \
  --module /tmp/ordinary-module.apk --coexist-module /tmp/coexist-module.apk \
  --output /tmp/v1.3-dist \
  --editor /path/to/APKEditor-1.4.9.jar \
  --npatch /path/to/jar-v1.0.7-741-release.jar \
  --aapt2 /path/to/android-sdk/build-tools/37.0.0/aapt2 \
  --apksigner /path/to/android-sdk/build-tools/37.0.0/apksigner \
  --javac /path/to/jdk17/bin/javac
```

输出四种安装包、SHA-256 和静态校验报告。直接上传 Release，不使用 Actions 临时产物作为发布中转。普通 CI 保留测试，临时 APK 上传改为手动选择且仅保留一天，避免持续占用 Actions 存储。历史 Release 与附件保留。

如当前云代理拒绝二进制上传，可由 `publish-v1-3.yml` 在 GitHub runner 重现全部检查后直接上传发布草稿；不使用 upload-artifact、不占用 Actions 临时产物额度。检查草稿附件完整后再正式发布。

## 发音修复覆盖更新

用户要求直接覆盖现有 v1.3，四种安装包及校验文件均替换，其他发布保留。模块内部版本为 1.6.7 / 117。原因是将旧版辅助字幕资源 ID 转为名称时误对应到逐字发音布局：1606 的 A.U 只接受 q8.D9 / q8.b9，实际辅助字幕绑定 E9 / c9 是对应子类；逐字发音 M9 / O9 不兼容。使用 lyrics_translation_line_karaoke 与 lyrics_bg_translation_line_karaoke 后，原生渲染方法可完成绑定。

运行现有 publish-v1-3.yml 的 workflow_dispatch，显式设 replace_existing=true 才可覆盖已发布的 v1.3。覆盖前校验并备份当前附件；上传失败自动恢复。完成后 v1.3 标签指向修复源码，主页按钮沿用原文件名。
