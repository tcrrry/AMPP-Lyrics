# Project boundaries

- This project's new lyrics controls belong in the independent **Tcrrry 歌词设置** page (`TcrrryLyricsSettingsUi`), including glow enhancement, sensitivity and trigger position. Keep them out of the upstream AM++ main settings page. Preserve this boundary for future plugin extraction.
- Keep existing saved setting keys and values when moving controls. UI relocation must not reset user preferences.
- Production releases omit development diagnostic copy buttons, matching-input debug panels and test labels. API connection checks and lyrics matching history/preview are normal product features.
- A new release updates all four download forms: ordinary APK, coexist APK, APKS and module APK. Preserve historical releases, tags and assets.
