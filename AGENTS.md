# Project boundaries

- This project's new lyrics controls belong in the independent **Tcrrry 歌词设置** page (`TcrrryLyricsSettingsUi`), including glow enhancement, sensitivity and trigger position. Keep them out of the upstream AM++ main settings page. Preserve this boundary for future plugin extraction.
- Keep existing saved setting keys and values when moving controls. UI relocation must not reset user preferences.
- Production releases omit development diagnostic copy buttons and test labels. Keep “查看当前匹配信息” in independent Tcrrry settings in production: show playback input and the actually applied result’s title, artist, album, duration and ID. API connection checks and lyrics matching history/preview are normal product features.
- A new release updates all four download forms: ordinary APK, coexist APK, APKS and module APK. Preserve historical releases, tags and assets.
- Before upgrading host inputs or coexist packaging, read `docs/COEXIST-PLAYER-REGRESSION-PREVENTION.md`. Preserve coexist routing startup registration and the vivo discovery action in the existing exported MediaPlaybackService. Run `VivoAtomicManifest --verify` on both the final coexist APK and embedded origin; missing declarations must fail packaging.
- Treat ordinary notifications, vivo native player display/controls and Atomic Island as separate acceptance results. Current coexist native player display is device-verified; Atomic Island is not supported/accepted. Do not replace validated binaries or grant device permissions silently as part of a release.
