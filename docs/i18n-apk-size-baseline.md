# i18n locale-filter APK-size baseline

> Recorded at Phase 0 completion (commit on `feature/i18n-phase-0`, 2026-07-29) as the reference for
> measuring the resource-table cost of packaging additional locales in Phase 4e.

## Why this exists

`androidResources.localeFilters` strips library locale folders (appcompat alone contributes ~85) so
only the catalogued, `packaged = true` qualifiers ship. Phase 0 packaged **only `en` and `en-rGB`**;
Historically Phase 4a enabled the original 23 complete community qualifiers. New catalogue-only locales are not packaged merely because they are catalogued.
Phase 4e measures that complete before/after change. This file is the **before** measurement so the
aggregate 24-language APK-size delta is visible
and a surprise regression (a library locale folder that slipped past the filter) is caught.

## Measurement method

```
AAPT2=~/Library/Android/sdk/build-tools/<version>/aapt2   # or $ANDROID_HOME/build-tools/<v>/aapt2
APK=app/build/outputs/apk/standard/release/app-standard-release-unsigned.apk

stat -f "%z" "$APK"                         # total APK bytes (macOS; `stat -c %s` on Linux)
unzip -l "$APK" | grep resources.arsc       # resources.arsc stored size + compression
$AAPT2 dump configurations "$APK"           # locale configs actually packaged
python3 tools/i18n/check_pseudo_locales.py --apk "$APK" --mode release
```

The `resources.arsc` row reports the **stored** (post-compression) size; the first column is the
uncompressed size. The release APK is built with R8 optimization + resource shrinking, so the arsc is
the locale-filtered string table only.

## Phase 0 baseline (standard release, unsigned)

| Metric | Value |
|---|---|
| Total APK size | 51,693,164 bytes |
| `resources.arsc` stored (compressed) | 40,008 bytes, stored uncompressed |
| Packaged locale configs | `en`, `en-rGB` (verified via `aapt2 dump configurations` + `check_pseudo_locales.py --mode release`) |
| Pseudolocales in release | none (verified) |
| Build | `assembleStandardRelease`, arm64-v8a + armeabi-v7a ABI split, R8 optimization on |

## Phase 4e comparison template

Build `standardRelease` before and after the Phase 4a catalogue flip, then append the measured result here:

| Phase | Locales packaged | APK size | `resources.arsc` | Δ APK | Δ arsc |
|---|---|---|---|---|---|
| 0 | en, en-rGB | 51,693,164 | 40,008 | — | — |
| Historical 4e, after Phase 4a flip | en, en-rGB + original 23 community locales | TBD | TBD | TBD | TBD |

The earlier 2-4 MB aggregate allowance is a planning estimate, not a result. Record the measured
delta without converting it into a per-locale claim. Use `aapt2 dump configurations` to verify that
only the intended 23 community configurations were added and that no dependency locale slipped past
`localeFilters`.
