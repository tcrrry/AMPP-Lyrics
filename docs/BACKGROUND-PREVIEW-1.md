# Coexist Background Recovery Preview 1 - 2026-10-14

## Scope and Evidence

User accepted settings preview 3, but reports gray/unloaded playback backgrounds
in the coexist build, sometimes recovering after changing several tracks.
Phone unavailable. No ADB/device operations were performed in this turn.
No claim that network problems or all device gray screens have been diagnosed.

Inspected the actual verified 1609 host DEX with APKEditor's existing DEX parser.
Input origin SHA256:
`599972c4296973eff39bb0eb2394c409005da2d2d387a2174c3c1c4ac93cb4e6`.
Generated read-only analysis artifacts are under `build/background-audit/`.

`LyricsBackgroundLayerView.setArtwork` returns early for the same bitmap or equal
pixels. Its native onDraw builds the derived bitmap/shader lazily. Native detach
recycles cached blur bitmaps, while source identity may remain. The prior recovery
cleared shaders but not source identity, and recognized recycled rather than
missing render caches. ImageView direct bitmap arrival also lacked a callback.

Four added regressions failed against the old implementation before production
changes: identical-cover recovery, cold attachment with missing render state,
direct bitmap arrival and a missing background after the native fade.

These local reproductions establish repairable code defects, not proof of the
exact runtime cause on this user's phone.

## Changes

- Rebuild only missing/recycled background state, retaining healthy native output.
- Clear native source identity before reapplying the same real artwork through
  the existing native method. Preserve the original source if application fails.
- Observe initial attachment, native artwork delivery and setImageBitmap as well
  as the existing drawable/resume/layout triggers.
- Allow the native fade to render normally; make one bounded delayed verification
  if its renderer is still missing. No persistent polling or frame hooks.
- Bind deferred verification to source/queued artwork identities. An older track's
  check must not interrupt a newer track's normal fade.
- Coalesce checks and cancel them on detach/scope closure. Suppress recovery's own
  native setter callback to prevent recursive reapplication.
- Ignore placeholder artwork events rather than restoring a previous track's image.
- Resource lookup keeps Apple's resource namespace for coexist 1609.

No new downloads, background-color replacement, engine/source-matching changes,
preference resets or host-version gate relaxation were introduced.

## Preserved Baseline

The accepted settings-preview-3 bundled plugin ZIP and module resources are
byte-identical. Plugin stays `1.0.7-preview.3`, versionCode 25; code.jar SHA256:
`2e6020dddae07c0756b160be3f05e724b68b18bebbb09bf4946e14cf648cc791`.
Independent plugin source/ZIP was not changed or republished in this turn.
Next formal plugin/bundle versionCode must still exceed 25.

## Verification

- Full main suite: 1151 tests, zero failures/errors, one skipped.
- Background recovery suite: 27 tests, zero failures/errors.
- Ordinary/coexist builds and app tests passed; coexist lint zero errors.
- Both module identities embed the identical accepted lyrics payload.
- All four installation forms repackaged and statically verified, including
  signatures, identities, unchanged embedded host origins, native libraries,
  APKS ABI/density splits and coexist layout behavior.
- Formal v1.7 coexist input digest remains unchanged.
- No phone animation, actual background recovery, login or DRM test was possible.

## Delivery

Final desktop APK:
`C:\Users\28411\Desktop\AMPP-Lyrics-1609-coexist-background-preview-1.apk`

SHA256 `42a33e2fe0378ac9f3ff7b390e79bdfebbc76f754e79aec6b21cb9f9a5d68665`
Bytes: 142270593. Desktop hash matches the final packaged artifact.

Use only `build/background-preview-1-final/` for final artifacts/validation reports.
`build/background-preview-1/` is a preserved preliminary candidate, not the delivered
final binary. The desktop candidate created during this turn was replaced only
after verifying its owned hash; no user or historical release artifact was overwritten.

Other three forms remain local under `build/background-preview-1-final/other-forms/`.
GitHub formal releases, historical files and tags were not changed. The delivered
APK remains a preview pending phone confirmation, especially first playback,
rapid track changes and returning from settings/background.
