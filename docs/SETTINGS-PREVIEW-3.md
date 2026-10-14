# Settings Preview 3 - 2026-10-14

Supersedes preview 2 for source ticker behavior. Local preview only; no releases
or tags changed. No phone installation, app restart or force-stop was performed.

## Accepted Scope

User reported the independent plugin and coexist build differed in ticker behavior:
opening a subwindow could stop it, and selecting quality/word priority could restart it.
Requested one consistent policy: continuously scroll overflow text without interference
from option changes or child-window focus. Deliver files while user is asleep.

## Implementation

- `SmoothSourceTicker` no longer uses TextView's native MARQUEE start/stop logic.
- An owned Choreographer callback advances fractional horizontal positions at 30dp/s.
  The clock does not consult window focus, selected state or preference values.
- Retains one-way repeat and native fading edges. Existing text layout is reused;
  no per-frame text assignment, measurement, bitmap creation or background recapture.
- Content and geometry updates retain the current phase while overflow remains.
- Fits-in-width text stays still. Hidden/detached pages stop callbacks; resuming a
  hidden page keeps phase without jumping over elapsed background time.
- Existing preview 2 home-rebuild prevention, resource lookup and native sibling
  settings entries remain. No playback background changes.

## Version / Upgrade Boundary

Plugin `1.0.7-preview.3`, versionCode **25**; main bundled installer VERSION=25.
Next formal plugin/bundle versionCode must exceed 25. Do not reuse the preview code.
Main host identity remains Apple Music `7.0.0-beta/1609`, module `151/1.7`.

## Verification

- Full plugin/import suite: 605 tests, zero failures/errors, one skipped.
- Regression tests cover focus loss/regain continuing the position, real native
  bitmap text rendering/pixel changes, retained text layout, phase preservation,
  repeat direction, no background-time jump, detach cleanup and no home rebuild.
- Ordinary and coexist builds/app tests passed; coexist lint zero errors.
- All four main installation forms repackaged locally and statically validated.
- Both module identities embed byte-identical plugin ZIPs matching preview 3 code.jar.
- Ordinary APK, APKS base and module distribution share the same ordinary module.
- Original host archives, native libraries, signatures, package identities and APKS
  ABI/density splits preserved; coexist layout behavior verification passed.
- Static checks and Robolectric pixel checks are not phone animation acceptance.

## Delivery

Both files were sent using explicit remote filenames to phone `/sdcard/Download/`,
and copied to `C:\Users\28411\Desktop`. Phone and desktop hashes match:

- `AMPP-Lyrics-1609-coexist-settings-preview-3.apk`
  SHA256 `971ba435830d972fd51343719c44c9c71678fd0d3a3f9e0df9278b02c0cf5328`
- `Tcrrry-Lyrics-Plugin-1.0.7-preview.3.zip`
  SHA256 `2d84a8f1731260a37382fb36ae158294b5dad2a637a1efeb58edbd1495d29e20`

Other three main forms: `build/settings-preview-3/other-forms/` (not sent to phone).
Frozen modules: `build/settings-preview-3/modules/`.
Independent plugin/source ZIP: sibling plugin `build/coexist-glass-preview-3/`.
Device acceptance pending. Preserve all formal artifacts and earlier previews.
