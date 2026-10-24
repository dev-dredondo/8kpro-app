# OwnTV TV app — i18n checks

**The strings themselves are not in this repository.** Every `strings*.xml`, the language catalogue
that owns them, the Weblate integration and the translator guide live in the core library repo:
<https://github.com/ahXN00/OwnTV_Core> (`tools/i18n/README.md` there). Add, change or translate a
string there, then bump `owntvCore` in `gradle/libs.versions.toml`.

What still runs here, on the TV app's own Kotlin:

| Check | What it enforces |
| --- | --- |
| `check_hardcoded_strings.py` | No user-visible text left hardcoded in `app/src/main/java` |
| `check_number_locale.py` | Numbers formatted with an explicit locale, not the default |
| `check_text_overflow.py` | Bounded Compose text has an overflow strategy |
| `check_pseudo_locales.py` | Pseudolocales are packaged in debug and absent from release |

```sh
python3 tools/i18n/check_hardcoded_strings.py verify --bootstrap
python3 tools/i18n/check_number_locale.py
python3 tools/i18n/check_text_overflow.py
```

`locales.json` is kept here too, but only because the Gradle build reads its `packaged` entries to
set `localeFilters`. **The core repo's copy is the authoritative one** — change it there first, then
copy it across.

### Clearing a literal-inventory failure

`verify` only reports; no flag makes it write, `--bootstrap` included — that flag drops the
merge-base comparison and nothing else. Two failure kinds, two fixes:

```sh
# UNCLASSIFIED — a literal exists in code but in neither reviewed file.
python3 tools/i18n/check_hardcoded_strings.py classify-safe \
    --path app/src/main/java/tv/own/owntv/example.kt --text 'SELECT 1' --category sql

# STALE CLASSIFICATION — a classified literal was edited or deleted in code.
python3 tools/i18n/check_hardcoded_strings.py prune-safe
```

Both rewrite `safe_literals.txt` and regenerate `hardcoded_baseline.txt`. Classify a literal only when
no user can ever read it; categories and their reasons are listed at the top of `safe_literals.txt`.
Text a user *can* read stays unclassified so it lands in `hardcoded_baseline.txt` as declared debt —
that file may only shrink against a pull request's merge base. Never file real UI copy as technical to
make CI green; extract it to `strings.xml` instead, or leave it in the baseline for a later pass when
the fix is bigger than the string (persisted values, for example).
