# Phase 3 locale correctness QA evidence

Phase 3 hardens locale-sensitive rendering before non-Latin production locales are packaged. This document separates source-review evidence from build, APK, and device checks that still need to be run.

## Commit series

1. `50e7b37` — Harden font fallback QA
2. `88df5d7` — Format dates for the active locale
3. `bdae0a8` — Guard locale-sensitive number formatting
4. `a207f0b` — Mirror logical RTL key navigation
5. `685b1e2` — Keep physical geometry stable in RTL
6. `f190518` — Harden text expansion and overflow
7. QA findings and evidence — this document

## Source-review evidence

- `PopupTheme.kt` retains its existing font-family structure. Provider title and subtitle text in `AudioNowPlayingBar` use the system Sans family, while the localized progress line retains popup typography.
- The debug-only font fallback activity contains static Arabic, Simplified Chinese, Traditional
  Chinese, Japanese, Korean, Malayalam, Hindi, Bangla, Cyrillic, Greek, and mixed-script fixtures. Only the
  debug manifest registers it.
- Date renderers resolve the active resource locale, Android best date-time pattern, current timezone, and system hour cycle. Date and time use one combined pattern where both are shown.
- Stable protocol, persistence, hash, and technical numeric formatting is pinned to `Locale.ROOT`. Display-number localization remains at the final UI renderer.
- Logical horizontal navigation uses `HorizontalDirection.START` and `HorizontalDirection.END`. Media seeking, colour controls, and the EPG timeline remain physical by design.
- The complete EPG structural guide region is LTR. Text inside that region and Canvas-measured labels use content-based paragraph direction.
- Mini-player corner choices use absolute alignment. Bounds-derived home placement uses an absolute offset. Seek, live timeline, trailer progress, hue, and saturation/value geometry are kept in narrow LTR providers.
- The pre-edit overflow inventory found 43 bounded text calls without an overflow argument. A post-edit parser inventory found none. The only `TextOverflow.Clip` call is the reviewed ASCII channel-number exception.
- Focus-triggered marquee was added to exactly the ten approved targets. Each remains a single-line text node with ellipsis when unfocused and reuses its parent focus state.
- The stepper, audio-delay row, stream-information columns, and series sorting row now reserve space for their actions while allowing translated values and labels to use bounded flexible space.
- At the Phase 3 snapshot, `tools/i18n/locales.json` packaged only `en-US` and the `en-GB`
  override. The original 23 community locales are packaged; newer catalogue-only requests remain unshipped and
  hidden until manual promotion at the 70% readiness threshold.
- `git diff --check` passed for the implementation commits.

## Automated verification status

Not run in this implementation session, in accordance with the plan's no-default-test-execution rule:

```sh
python3 tools/i18n/check_number_locale.py
python3 tools/i18n/check_text_overflow.py
python3 tools/i18n/validate_strings.py
python3 tools/i18n/check_hardcoded_strings.py verify --bootstrap
python3 tools/i18n/gen_supported_locales.py check
python3 tools/i18n/test_i18n_tools.py

./gradlew \
  :app:testStandardDebugUnitTest \
  :app:lintStandardDebug

./gradlew \
  :app:assembleStandardDebug \
  :app:assembleStandardRelease
```

After Phase 4a lands, use `python3 tools/i18n/validate_strings.py --report text` so the same
structural validation also prints informational coverage. Do not use the old `--release`
completeness policy as the acceptance criterion for community translations.

After assembling, run the APK checks with the actual output paths:

```sh
python3 tools/i18n/check_pseudo_locales.py --apk <debug-apk> --mode debug
python3 tools/i18n/check_pseudo_locales.py --apk <release-apk> --mode release
```

## Manual walkthrough status

The following checks require installed APKs and remain pending. Phase 3 should not be marked fully verified until they are recorded here or in the PR description.

Phase 4a reuses this groundwork for a paid seed pilot in German, Arabic, Japanese, and Turkish. That
pilot checks real translated content, but it does not replace the broader pseudolocale and device
walkthrough below. Translation coverage is informational; malformed localized values remain errors.

### Pseudolocales

Set OwnTV to **System default**, then repeat the full application walkthrough with device locales `en-XA` and `ar-XB`:

- Setup and source management
- Profile gate and profile dialogs
- Home, Search, Movies, and Series
- Live TV, channel/category overlays, catch-up, and the full EPG
- Programme dialogs and every player mode/control surface
- Subtitles and every settings category
- Companion web pages

Record per-screen expansion, clipping, marquee, extraction, focus-navigation, geometry, and paragraph-direction findings.

### Real scripts

Launch the fixture on API 26 and a recent emulator:

```sh
adb shell am start \
  -n tv.own.owntv/tv.own.owntv.core.i18n.FontFallbackQaActivity
```

Also exercise representative real provider titles, channel/programme names, playlists, and subtitle filenames in the actual EPG, overlays, and subtitle UI. Classify each issue as missing glyphs, incorrect shaping, incorrect paragraph direction, or only inconsistent serif/sans appearance.

For the first-pass locale runs, explicitly verify Malayalam shaping, Hindi Devanagari conjuncts,
and Bangla conjuncts and vowel signs in both the fixture and the language picker endonyms.

### RTL acceptance checks

Under `ar-XB` with a real Arabic provider title, verify that:

- Time increases left to right and programme blocks align with the header.
- Physical Left selects an earlier programme and physical Right selects a later programme.
- Arabic guide text shapes and orders correctly.
- The physical top-left mini-player remains top-left.
- Seek fills grow from left to right.
- Spatial navigation outside physical controls mirrors to logical start/end.

## Completion status

Implementation is complete. Automated, APK, pseudolocale, and real-device verification is pending and must not be inferred from the source-review evidence above.
