# Settings Preview 2 - 2026-10-14

## Scope

Local previews only. No GitHub releases, tags or formal assets were changed.
The user installs/imports files; no ADB installation, force-stop or restart was performed.

- Coexist glass now resolves its own `dev.amenhancer.module.coexist` resources,
  preferring the loaded archive over an unrelated installed ordinary module.
- Native host settings list has sibling AM++ and Tcrrry entries. Their callbacks
  are independent, deduplicated and revoked when the host view is destroyed.
- Preference/source refreshes do not rebuild an unchanged settings home.
  The existing source ticker stays attached; content polling still applies real changes.
- Glow settings read current configuration when opened, avoiding stale captured values.
- No playback background changes. User reported gray on early tracks and recovery
  after switching tracks; cause remains unconfirmed, not attributed to network as fact.

## Important Version Boundary

Preview bundle: `com.tcrrry.lyrics`, `1.0.7-preview.2`, versionCode **24**.
Main `BundledLyrics.VERSION` is 24 so an existing preview 23 or formal 22 upgrades.
The next formal plugin/bundled versionCode must exceed 24. Do not reuse 23 or 24
for a different payload; PluginStore otherwise retains the already installed version.
The main host remains Apple Music `7.0.0-beta/1609`, module `151/1.7`.

## Verification

- Plugin complete unit/import suite: 600 tests, zero failures/errors, one skipped.
- Main complete suite: 1138 tests, zero failures/errors, one skipped.
- Ordinary/coexist app tests passed; lint zero errors, 63 existing warnings.
- Actual plugin ZIP validated: DEX entry, dictionary pins, no bundled SDK/host/Compose classes.
- Ordinary and coexist modules contain byte-identical bundled plugin ZIPs.
- Ordinary APK, APKS base and standalone module contain the exact same ordinary module.
- All repatched host origins are byte-identical to their formal v1.7 inputs.
- Package identity, original signature and native libraries retained.
- APKS ABI/density splits retained byte-for-byte; coexist layout behavior checks passed.
- Formal `verify-integrated-lyrics.py` pin remains the default; explicit preview
  validation requires supplying the actual preview plugin ZIP.
- Formal coexist APK and plugin 1.0.6 ZIP hashes rechecked unchanged.

These are desktop/static checks, not device playback/animation acceptance for all forms.

## Delivery

Both files are in phone `/sdcard/Download/` and `C:\Users\28411\Desktop`:

- `AMPP-Lyrics-1609-coexist-settings-preview-2.apk`
  SHA256 `fc027a9fa7839bf99a3c945cf0e1384f0d82cf3a47f9e84181c74ad0c939fafe`
- `Tcrrry-Lyrics-Plugin-1.0.7-preview.2.zip`
  SHA256 `9fa97af25d0a497148a6f0150e0d92aa4e57ee09f702f03fc2cbb9a9edcde1b5`

Phone hashes verified over isolated ADB server 5038. Directory-target multi-file
push produced truncated basenames; those exact hash-verified files were renamed
with `mv -n` to the above names and hashed again. Prefer explicit destination names.

Main local artifacts: `build/settings-preview-2/`.
Other three forms and validation JSON: `build/settings-preview-2/other-forms/`.
Frozen module inputs: `build/settings-preview-2/modules/`.
Independent plugin ZIP/source/checksums: sibling plugin `build/coexist-glass-preview-2/`.

Pending: user acceptance on coexist and independent plugin. Other forms have no
new phone acceptance; do not label them device-verified or publish without instruction.
