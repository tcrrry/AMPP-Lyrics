# Repository checks and build helpers

This directory contains host profile checks, build helpers and optional device regressions.
Use the [host adaptation guide](../docs/host-adaptation-guide.md) for the per-build workflow.
Functional acceptance is manual by default; run device scripts only when explicitly requested.

## Coexist vivo player release gate

See [the regression record](../docs/COEXIST-PLAYER-REGRESSION-PREVENTION.md) before changing host inputs or coexist packaging.
`package-1609-release-v1.7.py` now adds vivo discovery after package isolation and verifies
both the final coexist APK and its embedded origin using `VivoAtomicManifest --verify`.
The existing exported Apple MediaPlaybackService must retain its browser action and exactly
one `com.vivo.musicwidgetmix.support.service` action. A missing declaration fails packaging.
Keep coexist routing startup registration and its tests; preserve accepted plugin bytes.
Static success does not prove device player controls or Atomic Island support.

## Device script requirements

- PowerShell 7 and ADB
- An unlocked device with Apple Music and the module installed
- `ANDROID_SERIAL` set, or the corresponding `-Serial` / `-Device` argument supplied
- Root access for scripts that record through `/data/local/tmp`
- Python with OpenCV (`cv2`) and NumPy for the image/video analyzers

Several liquid-glass checks use fixed coordinates or 1080 × 2376 regions from the reference phone. Review and adjust those values before running on another resolution. The tablet checks expect Apple Music to be open in landscape.

Example:

```powershell
$env:ANDROID_SERIAL = "your-device-serial"
.\scripts\verify-device-dual-pane.ps1
```

## Host profile verification (no device required)

`verify-host-profile.py` is a read-only DEX check of an exact host profile. It reads the original
APK or split APK container (APKM, XAPK or APKS), including DEX files in the splits. It checks
the members recorded for static verification and the exact contracts described below;
`--glass` additionally selects the recorded glass seams.

For Fragment builds, verification also covers all declared method/field/constructor descriptors
in the dual-pane, lyrics runtime, karaoke width, video handoff and optional song-layout sections.
Layout policy and the six comparison-evidence fields must be explicit. Installation additionally
checks the code-owned required-role inventory before registering any hook; an omitted JSON role
cannot silently disable a dependency. Use [the adaptation checklist](../docs/host-adaptation-checklist.md)
to record helper call paths, layout/include differences and native data-binding writes.

```powershell
python scripts\verify-host-profile.py "apple-music-6-5-3.xapk" --version-name 6.5.3 --version-code 1599 --glass
python scripts\verify-host-profile.py "Apple+Music_6.5.2_APKPure.xapk" --glass
```

The version tuple comes from the binary AndroidManifest.xml in the base APK. Optional version
arguments and an outer manifest.json must agree with that identity. Use `--profile PATH`
to check an explicit development profile that has not been enabled or indexed yet.
A PASS means the static evidence behind a profile still holds for that package; it does not prove
runtime behaviour, resource IDs, container types or blur sampling.

Besides the pinned classes, methods and fields, the script asserts the two seams whose names R8
reuses between builds: the direct catalog query (only one method may satisfy the
`(String, Map, Continuation)` shape, and it must be the verified name for that version) and the
obfuscated `androidx.lifecycle.LiveData` alias that `COMPOSE_OBSERVE_AS_STATE` accepts.

## Independent plugin build and CI caching

`build-plugin-example.py` exports the SDK JAR, then builds the example in a temporary
project using only that JAR. It needs Android platform/build-tools 37 and keeps the
example independent of the repository's Gradle modules.

CI uses `setup-gradle` as the only cache owner for Gradle User Home. Task caching and
parallel module builds are enabled in `gradle.properties`; the temporary example also
receives `--build-cache` explicitly.

The SDK/example output cache uses an exact fingerprint from
`python scripts/build-plugin-example.py --cache-key` (requires `JAVA_HOME` and
`ANDROID_HOME` or `--android-sdk`). It covers SDK/example sources and assets, Gradle
configuration/build logic, the builder script and wrapper, JDK release metadata, and
the Android platform/D8 JAR contents. Generated build directories are excluded.
Related changes invalidate the cache; unrelated application source changes can reuse
it. There is no partial-key restore, and either missing output causes a rebuild.

Full tests, Lint, release assembly and signature verification remain in the workflow.

## v1.8 release

Use `bundle-released-lyrics.py PLUGIN.zip --main-version 1.8` before building both modules.
`verify-integrated-lyrics.py` pins the final 1.0.7 manifest and code.jar; only the update
channel differs. `package-1609-release-v1.8.py --help` produces all four installation
forms from pinned 1012 inputs. It verifies vivo discovery in both final coexist APK
and origin. See `docs/COEXIST-PLAYER-REGRESSION-PREVENTION.md` for acceptance limits.
Source-contract tests declare repository sources/build configuration as inputs, and
plugin import tests declare the fixture ZIP contents, so changes invalidate their
test results even when compiled class files or fixture paths stay unchanged.
Run `python -m unittest discover -s scripts/tests` to check artifact cache invalidation.
