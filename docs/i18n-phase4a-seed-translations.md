# i18n Phase 4a — historical seed plan for the original 23 translations

> **Historical:** this records the original 23-locale seed iteration. Every inventory, count, and
> packaging statement below describes that superseded snapshot, not the current catalogue. The
> current authoritative catalogue-only and 70% promotion policy is `tools/i18n/README.md`; statements
> below that all catalogue locales are immediately packaged/selectable are superseded.

This was the implementation plan for Phase 4a. Work from this file alongside
`docs/i18n-phase2-language-picker.md`, `docs/i18n-phase3-qa.md`, `docs/i18n.md`, and
`docs/internationalization.md`.

Phase 4a has two ordered parts:

1. Align the existing tooling and picker with the product policy agreed after Phase 3.
2. Build the one-off seed tool and generate the 23 community-language resource sets.

The policy alignment is part of 4a because the current release validator would otherwise reject an
incomplete community translation. Generated picker coverage remains useful, but it is informational:
translation changes update the badge without imposing a minimum percentage. Land the policy
alignment before adding the generated locale files.

Phase 4b (Weblate) and 4e (APK delta) remain separate. The former 4c packaging flip is absorbed here:
all supported languages are packaged and picker-visible because coverage is informational and missing
keys safely fall back to English.

## Locked product policy

- English in `values/` is the only language OwnTV guarantees to be complete.
- Every other language is community-supported and best-effort.
- When a localized key is absent, Android must fall back to source English. Missing translations are
  expected and never fail CI or block a release.
- A localized value that is present must be structurally safe. Invalid XML, broken placeholders,
  invalid plural forms, empty values, and similar defects still fail CI because those entries override
  English rather than falling back to it.
- Translation coverage is informational in both CI and the language picker. It is not stored in
  `locales.json` and is never used as a release gate.
- `SupportedLocales.kt` remains a generated runtime catalogue derived from `tools/i18n/locales.json`
  and the resource tree so it can carry the picker's coverage value. Translation changes require
  regeneration, but any resulting percentage, including 0%, is valid.
- `safe_literals.txt` is an internal Kotlin-literal classification file, not a translation glossary or
  a list of Android strings. Never feed it into the translation model.
- `values/donottranslate.xml` remains the small Android resource set for brand, engine, and unit
  constants. It is not copied into localized directories.
- The seed aims to produce a complete initial snapshot for all 23 target locales. That is a property
  of this one seed run, not a permanent CI or release requirement.
- Seeded translations do not receive review-state entries. Translation approval state is not a
  release requirement.

## Current and post-alignment inventory

The current source tree has six translatable files containing 1,655 keys: 1,602 `<string>` entries and
53 `<plurals>` entries. There are no `<string-array>` entries. The language-picker coverage badge uses
one of those strings, `settings_language_coverage`.

Part 1 keeps that badge and source string. The seed must derive its inventory and should find:

- 1,655 source keys: 1,602 `<string>` plus 53 `<plurals>`
- six source filenames
- 25 catalogue entries: source English, the en-GB regional override, and 23 community targets
- 23 target locale directories
- 138 generated translation files

These counts are assertions against the present checkout, not magic constants for the implementation.
The tool must always compute and print the final inventory and fail a dry run if it differs from its
request manifest.

The current per-file key counts produce 44 translation chunks per locale at a maximum of 40 keys per
chunk. Recompute rather than assuming that count if the source changes before execution.

## Part 1 — align fallback, coverage, and catalogue policy

### Files changed by the policy alignment

| Path | Change |
|---|---|
| `tools/i18n/validate_strings.py` | Make completeness informational, add reports, and remove review-state/release gating. |
| `tools/i18n/translation_status.json` | Delete; it no longer represents release policy. |
| `tools/i18n/gen_supported_locales.py` | Keep informational resource coverage and make its key-set calculation match the CI report. |
| `app/src/main/java/tv/own/owntv/core/i18n/SupportedLocales.kt` | Regenerate after translation resources change so badge values are current. |
| `app/src/main/java/tv/own/owntv/features/settings/LanguageSettingsScreen.kt` | Keep the informational coverage badge. |
| `app/src/main/res/values/strings_settings.xml` | Keep `settings_language_coverage`. |
| `.github/workflows/i18n.yml` | Print the informational report and stop invoking release coverage policy. |
| `tools/i18n/test_i18n_tools.py` | Replace coverage/status gates with report and structural-validation tests. |
| `app/src/test/java/tv/own/owntv/core/i18n/SupportedLocalesTest.kt` | Retain coverage assertions and runtime catalogue invariants. |
| `docs/i18n-phase2-language-picker.md`, `docs/i18n.md`, `docs/internationalization.md` | Reconcile the documented policy and workflow. |

### 1. Validator and CI behavior

Change `tools/i18n/validate_strings.py` so missing community translations are informational while
present-but-invalid translations remain errors.

Remove:

- the Tier 1 100% coverage failure
- release-only translation review-state enforcement
- parsing and validation of `tools/i18n/translation_status.json`
- the `--release` policy distinction

Delete `tools/i18n/translation_status.json`; it has no remaining product or release role.

Keep structural failures for every localized entry that exists:

- invalid XML and Android escaping
- translation-only keys
- `translatable="false"` in a translation file
- localized `donottranslate.xml`
- invalid or missing required quantities on a plural resource that exists
- placeholder mismatch for every localized string or plural quantity
- bare non-positional format placeholders
- empty localized values
- invalid leading or trailing whitespace

Add `--report text|json|none`, defaulting to `text`. Coverage is computed from the same suffixed key
sets the validator already uses for strings, plurals, and arrays. Report the 23 Tier 1 community
targets. Exclude source English and the intentionally partial tier-0 en-GB regional override; en-GB is
not a translation backlog.

The text report is deterministic and catalogue-ordered:

```text
Translation coverage:
  de          0 / 1655    0.0%  1655 missing
  ml       1537 / 1655   92.9%   118 missing
```

The JSON report has a versioned root and machine-readable per-locale counts:

```json
{
  "schemaVersion": 1,
  "sourceKeys": 1655,
  "locales": [
    {
      "languageTag": "ml",
      "translatedKeys": 1537,
      "missingKeys": 118,
      "coveragePercent": 92.9
    }
  ]
}
```

Missing keys affect the report but not the exit code. Structural errors still exit non-zero. Keep the
human report visible even when structural errors exist, so CI gives the maintainer both kinds of
information in one run.

For `--report json`, reserve stdout for the single JSON document and send structural diagnostics to
stderr. The JSON document is still emitted when structural errors make the process exit non-zero.

Update `.github/workflows/i18n.yml` to invoke the validator with the text report and stop using
`--release`. The generator freshness check remains gating so the displayed badge cannot silently go
stale. It may require regeneration after `locales.json` or translation resources change, but it must
never fail merely because a locale's current percentage is low.

Do not promote Android lint `MissingTranslation` to an error.

### 2. Runtime locale catalogue and picker coverage

Keep `tools/i18n/gen_supported_locales.py` dependent on both `tools/i18n/locales.json` and the
resource tree.

- Keep `coverage` on `SupportedLocale` and every generated entry.
- Calculate its numerator and denominator with the same suffixed key-set rules used by the validator's
  informational report, with a regression test proving exact parity.
- Keep the generated runtime metadata and helpers used by `LocaleStore`, `LocalizedContent`, settings,
  and the language picker.
- Keep `check`; freshness means that the checked-in Kotlin matches both the catalogue and current
  translation resources.
- Regenerate `SupportedLocales.kt` after the pilot files and again after all 23 locale sets are
  promoted. A freshly generated 0% or partial value is valid and must pass `check`.

Do not replace this with runtime JSON parsing. `tools/i18n/locales.json` is tooling input and is not
packaged into the APK; generated Kotlin keeps the runtime catalogue typed and removes startup I/O and
parse-failure paths.

### 3. Picker and documentation

Keep the coverage badge in `LanguageSettingsScreen.LanguageRow`, its `coverage` parameter, and the
`settings_language_coverage` source string. The badge is a status indicator only: it does not disable
a locale or imply a release requirement. The picker continues to show only `packaged &&
pickerVisible` rows.

Update the affected tests and documentation:

- `tools/i18n/test_i18n_tools.py`
- `app/src/test/java/tv/own/owntv/core/i18n/SupportedLocalesTest.kt`
- `docs/i18n-phase2-language-picker.md`
- `docs/i18n.md`
- `docs/internationalization.md`

Replace every promise of 100% Tier 1 release gating with the English-fallback policy. Document the
important boundary explicitly: fallback occurs only when a localized key is absent. An empty, broken,
or stale localized value overrides English.

Add this authoring rule to `docs/i18n.md`: when the semantic meaning of English copy changes, introduce
a new key so an old community translation cannot override the new English meaning. Purely cosmetic
English edits may retain the key.

## Part 2 — one-off seed

### Outcome

Generate the initial complete snapshot for the 23 non-source catalogue locales:

- 23 locale directories
- the same six filenames as source in each directory
- 138 UTF-8 Android XML files
- every post-alignment source key represented in its corresponding file
- locale-correct plural quantities
- no localized `donottranslate.xml`

All target entries use `packaged: true` and `pickerVisible: true`. A locale is selectable before its
seed is complete; its badge reports the current percentage and missing keys fall back to English.

The seed's strict completeness check is intentionally stronger than CI. CI permits future community
translations to be partial; this one paid seed run must not silently lose a requested key.

### Files

| Path | Purpose |
|---|---|
| `tools/i18n/seed_text.py` | New stdlib-only extraction, token, escape, XML emission, and offline validation logic. |
| `tools/i18n/seed_translations.py` | New manual batch driver. The only module that imports `anthropic`. |
| `tools/i18n/glossary.json` | New curated preserve-exact phrases and domain terminology. Never generated from `safe_literals.txt`. |
| `tools/i18n/requirements-seed.txt` | New exact `anthropic` SDK pin for a local venv; never installed by CI. |
| `tools/i18n/test_i18n_tools.py` | Add offline policy, generator, report, and seed regression tests. |
| `.gitignore` | Ignore `/runs/`; never commit batch inputs, IDs, raw results, or retry state. |
| `app/src/main/res/values-*/strings*.xml` | 138 generated translation files. |

`seed_text.py` must not import the SDK. Existing i18n tests run in an environment where `anthropic` is
not installed.

`seed_translations.py` carries a header stating that it is a manual, one-off supply-chain tool. It must
never be wired into CI or run automatically by Gradle.

### Curated glossary

`glossary.json` has two concepts:

- `preserveExact`: complete brand, product, protocol, and format phrases that may appear inside
  translatable sentences, such as `OwnTV`, `ExoPlayer`, `mpv`, `OpenSubtitles`, `Xtream`, and `M3U`.
- `consistentTerms`: roughly 120 product-domain terms whose chosen translation should be reused across
  independent chunks, such as playlist, portal, source, profile, EPG, programme guide, catch-up, Live
  TV, movies, and series.

Do not add standalone `Own` or `TV` merely because they are separate source resources. Do not import
any category from `safe_literals.txt`; that file contains thousands of internal identifiers, regexes,
logs, paths, and protocol tokens unrelated to translated UI.

A locale glossary request translates only `consistentTerms` and returns the fixed `preserveExact`
phrases unchanged.

### API contract

- Model: `claude-opus-4-8`
- API: Anthropic Message Batches
- Thinking: `{"type": "adaptive"}`
- Effort: explicit `high` in `output_config`
- Structured JSON: `output_config.format` with `type: "json_schema"`
- Prompt cache: locale system block marked `{"type": "ephemeral", "ttl": "1h"}`
- No temperature, top-p, or top-k overrides

Use the official Anthropic Python API, batch, structured-output, adaptive-thinking, prompt-caching, and
pricing documentation. There is no assumed local `claude-api` skill.

The previous approximately $16 figure is a working estimate, not a cap. Dry-run manifests must print
request counts and estimated input/output tokens. After the pilot, compute the actual effective cost
from returned usage, including thinking and cache usage, and require the maintainer to approve the
revised remaining-locale estimate before submission.

### Durable run stages

The driver is resumable and separates preparation, submission, and collection. Never hide a network
submission behind `--dry-run` or a collection command.

Each run writes an immutable manifest under `runs/seed/<run-id>/` containing:

- source inventory hash
- `locales.json` hash
- glossary hash
- model and API settings
- ordered request IDs
- request payload hashes
- submitted batch IDs
- per-request status and usage
- raw results
- validated translations and retry history

Persist the batch ID immediately after a successful submission and before polling. A resumed command
must adopt that ID rather than submit the same manifest again.

Provide explicit commands or subcommands for:

1. preparing glossary requests
2. submitting an already-written manifest
3. checking batch status
4. collecting results
5. preparing translation requests from collected glossaries
6. validating and promoting a locale
7. resuming any submitted run
8. checking existing generated locale files entirely offline

Only the maintainer runs submission commands with API credentials. The implementing agent writes the
tool, tests, manifests, dry-run path, and documentation, then works from the persisted files after the
maintainer has collected results.

### Correct batch topology

Glossary results are inputs to translation prompts, so they cannot share one batch submission with the
translations that consume them. Generate Malayalam first as the maintainer-readable flow check, then
Hindi and Bangla as separate single-locale runs. The de/ar/ja/tr pilot must finish before money is
spent on the remaining 16 locales.

At the current 44 chunks per locale, the initial topology is:

| Stage | Requests |
|---|---:|
| Malayalam glossary | 1 |
| Malayalam translations | 44 |
| Hindi glossary | 1 |
| Hindi translations | 44 |
| Bangla glossary | 1 |
| Bangla translations | 44 |
| Pilot glossaries: de, ar, ja, tr | 4 |
| Pilot translations | 176 |
| Remaining glossaries | 16 |
| Remaining translations | 704 |
| Initial total before retries | 1,035 |

Retries are separate follow-up batches containing only failed keys. They are not included in the
1,035.

### Extract and tokenize

Parse the six final `values/strings*.xml` files. Skip `donottranslate.xml` and anything marked
`translatable="false"`. For every entry retain:

- filename and source order
- key and resource kind
- the immediately preceding `<!-- Translators: ... -->` comment
- source text or full English plural quantity map
- the exact `<xliff:g>` elements and their order
- intentional leading/trailing whitespace envelope for `_separator` and `_metadata_year` keys

Replace each complete `<xliff:g ...>...</xliff:g>` element with a collision-resistant opaque token and
store the exact original XML in a per-key, per-quantity side table. Validate the exact token multiset
before detokenizing; reordering is allowed, deletion and duplication are not.

Decode source representation syntax before prompting:

- XML entities to their characters
- `\uXXXX` Unicode escapes
- Android `\'`, `\"`, `\n`, `\t`, and `\\` escapes
- `%%` to a literal `%`

The model may see literal punctuation such as `%`; it never sees Java format placeholders, xliff tags,
or raw Android/XML escape syntax.

For a locale-only plural quantity, translate from English `other` and reuse its token side table. For a
quantity present in English, use that quantity's own text and tokens.

### Glossary and translation prompts

Run one structured glossary request per locale. Store the collected locale glossary in the run
directory before preparing translation chunks.

Chunk each source file independently at no more than 40 keys. Use deterministic ordering. A custom ID
must be unique, stable, and safe for the API, for example:

```text
de__strings_content__000
```

Keep the locale's cached system block byte-identical across all of its translation requests. Put
chunk-specific keys, comments, source text, and required plural quantities in the user message.

Use one stable response schema across chunks so schema changes do not defeat prompt caching. A response
contains arrays of string and plural records; all six Android plural fields are represented as string
or null, and the offline checker enforces which quantities are required for the locale. The response
schema guarantees parseable structure, not semantic completeness.

### Collect and classify results

Batch results are unordered. Key only by `custom_id`, never response position.

Handle every batch result type: succeeded, errored, canceled, and expired. A succeeded request is not
automatically usable. Also require:

- `stop_reason == "end_turn"`
- a text content block containing the structured JSON
- the exact requested key set, with no extras or duplicates
- the correct resource kind for every key
- required plural quantities and null only where allowed
- exact opaque-token parity

Treat refusal, `max_tokens`, malformed content, and failed semantic checks as retryable request
failures. Preserve valid keys from a partially bad chunk and retry only the failed keys with the exact
validation error in the follow-up prompt.

Allow two follow-up attempts after the initial request. After that, write
`runs/seed/<run-id>/<languageTag>-unresolved.json` and exit non-zero.

### Escape and emit

Write into `runs/seed/<run-id>/work/<languageTag>/`, never directly into the final Android resource
directory.

For ordinary keys, trim model-added leading/trailing whitespace and use the result as the text core.
For `_separator` and `_metadata_year`, ignore the model's outer whitespace, retain its trimmed core,
and store the exact source whitespace envelope for restoration after the core has been escaped.

For model-authored text, in this order:

1. Reject XML-illegal control characters.
2. Escape an existing literal backslash as `\\`.
3. Escape `&` as `&amp;` and `<` as `&lt;`.
4. Escape apostrophe and double quote as Android `\'` and `\"`.
5. Escape every model-authored `%` as `%%`.
6. Encode literal newline and tab as `\n` and `\t`.
7. Escape a leading `@` or `?` in the trimmed core as `\@` or `\?`.
8. Substitute opaque tokens with the original byte-exact `<xliff:g>` elements.
9. Restore the stored source whitespace envelope for `_separator` and `_metadata_year` keys.

Injecting xliff elements last keeps the `%1$d` inside them from becoming `%%1$d`.

Emit literal UTF-8 translated text, not `\uXXXX`. Preserve source filename and key order. Do not copy
translator comments into localized files. Emit manually rather than serializing with ElementTree.

Every file begins:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
```

Never emit a localized `donottranslate.xml`.

### Offline seed validation and atomic promotion

Before promotion, validate the staged locale entirely offline:

- exact six-filename set
- each key remains in the corresponding source filename
- exact complete post-alignment source-key set, with no extras
- no `translatable="false"`
- no `donottranslate.xml`
- required locale plural quantities
- no invalid plural quantity names
- exact placeholder parity using `tools/i18n/format_specs.py`
- no bare placeholders
- non-empty values
- spacing rules
- raw XML and Android escaping
- successful parse through `validate_strings._parse_dir`

Only after the whole locale passes may its staged directory be renamed into
`app/src/main/res/<resourceDirectory>/` as one same-filesystem promotion. Refuse to overwrite an
existing final locale unless it matches the current run manifest exactly or the maintainer supplies an
explicit recovery flag.

A failed locale leaves its final resource directory absent or unchanged. Never leave a partial locale
in `app/src/main/res`.

## Tests to write

All seed-text and policy tests are offline and live in `tools/i18n/test_i18n_tools.py` unless an
existing JVM test is the clearer home.

Policy and catalogue:

- a packaged locale missing source keys exits zero and reports the missing count
- a malformed present translation still exits non-zero
- translation review state is neither read nor required
- adding or removing translation keys changes generated coverage and fails generator freshness until
  `SupportedLocales.kt` is regenerated
- changing `locales.json` does make generated Kotlin stale
- generated coverage uses exactly the same source and translated key sets as the validator report
- generated `SupportedLocale` retains its coverage field
- picker rows continue to render the coverage badge without disabling partial locales

Seed text:

- tokenize and detokenize round-trip xliff elements byte-exactly
- reordered tokens preserve exact placeholder parity
- missing, duplicated, unknown, or naturally colliding tokens raise before writing
- all supported source escapes decode correctly before prompting
- a model-authored literal percent becomes `%%`, while an injected `%1$d` remains unchanged
- literal backslash, apostrophe, quote, ampersand, less-than, newline, tab, leading `@`, and leading `?`
  are escaped in the correct order
- ordinary whitespace is trimmed; separator whitespace is restored from source
- Arabic plural output carries all six quantities and the count token in each
- exact filename and file-owned key mapping is enforced
- structured success with refusal or `max_tokens` is rejected
- unordered results are matched only by custom ID
- a persisted batch ID is resumed without resubmission
- a failed locale is never partially promoted
- staged output reparses to the complete source key set

## Verification commands

Write and show these commands during implementation. Do not run them unless the maintainer explicitly
asks for execution:

```sh
python3 tools/i18n/test_i18n_tools.py
python3 tools/i18n/gen_supported_locales.py
python3 tools/i18n/gen_supported_locales.py check
python3 tools/i18n/validate_strings.py --report text
python3 tools/i18n/seed_translations.py prepare-glossary --locales hi --dry-run
python3 tools/i18n/seed_translations.py prepare-glossary --locales bn --dry-run
python3 tools/i18n/seed_translations.py prepare-glossary --locales de,ar,ja,tr --dry-run
python3 tools/i18n/seed_text.py check --locales hi
python3 tools/i18n/seed_text.py check --locales bn
python3 tools/i18n/seed_text.py check --locales de,ar,ja,tr
./gradlew :app:processStandardDebugResources --console=plain
```

The real submission commands are printed from the prepared manifest for the maintainer. The agent does
not submit a batch, poll Anthropic, or spend API credit.

After all 23 locales are promoted, repeat the offline seed check, validator report, generator freshness
check, and Android resource compilation. CI should print 100% immediately after the seed, but a future
missing community translation must reduce the report without failing the build.

## Pilot walkthrough

Generate Malayalam by itself first and switch between English and Malayalam throughout the app. The
maintainer can review Malayalam directly, so this is the first end-to-end check of picker selection,
fallback, script rendering, resource ownership, and the informational coverage badge. Reconcile every
new English source key before preparing this run; otherwise the seed checker will correctly reject the
locale as incomplete even though Android and CI permit English fallback.

Generate Hindi by itself next with `--locales hi`. Validate all six `values-hi/strings*.xml` files,
then perform an English/Hindi switch and Devanagari glyph check. Hindi is already visible at 0%
before generation and uses English fallback until its files are promoted.

Generate Bangla by itself next with `--locales bn`. Validate all six `values-bn/strings*.xml` files,
then perform an English/Bangla switch and inspect conjuncts, vowel signs, and line wrapping. Bangla
is already visible at 0% before generation and uses English fallback until its files are promoted.

Run the paid pilot for de, ar, ja, and tr only. After offline validation, regenerate
`SupportedLocales.kt` and build a debug APK; no temporary catalogue edit is needed.

- German: inspect Sidebar and TopBar expansion; confirm focused marquee remains usable.
- Arabic: confirm RTL mirroring, glyph shaping, no tofu, readable mixed provider text, and a
  representative counted-string surface.
- Japanese: confirm CJK fallback and a stable cross-script switch with no recreate loop or lost
  selection.
- Turkish: confirm `İ`, `ı`, `Ş`, and `ğ` render correctly, translated labels remain usable without
  clipping, and the same-script switch applies without an Activity recreation.

Use the existing Phase 3 real-script and RTL walkthrough for the broader geometry and D-pad sweep.
Plural-file completeness and the exact Arabic count-token contract are proved by the offline seed
checker; do not claim that casually observing one UI count proves every CLDR branch.

After the walkthrough, keep the catalogue enabled and regenerate `SupportedLocales.kt` whenever
translation files change so the informational badges remain current.

Only after the Malayalam, Hindi, and Bangla flow checks and the four-locale pilot are clean, and their
actual API usage is reviewed, may the maintainer approve the remaining 16-locale submissions.

## Done when

- CI treats missing community translations as informational and still rejects broken present values.
- CI prints deterministic per-locale coverage.
- `translation_status.json` and release approval gating are gone.
- the picker keeps an informational coverage badge.
- generated Kotlin coverage matches the current resource tree and the CI report.
- generator freshness can fail for stale badge metadata, but never for a low coverage percentage.
- the seed tool is stdlib-isolated from the Anthropic driver, resumable, dry-runnable, and fully tested.
- the 138 locale files are present and pass the seed's stricter complete-snapshot check.
- no unresolved file exists for any target locale.
- Android resource compilation succeeds when verification is authorized.
- the Malayalam, Hindi, and Bangla single-locale walkthroughs and de/ar/ja/tr pilot are recorded as clean.
- all 23 community locales are packaged and picker-visible; en-GB remains a hidden regional override.
- no API credentials, batch payloads, raw results, or run state are committed.

## Explicitly out of scope

- Weblate project/component configuration or GitHub hooks
- Gradle packaging changes
- a runtime JSON translation catalogue
- translation approval workflows or review-state gates
- automatic translation in CI or Gradle
- APK size measurement
- treating machine-seeded text as human-reviewed

## Deferred phases

### 4b — Weblate

Configure six split Android components through discovery, project language aliases, bidirectional
GitHub synchronization, hooks, and the maintainer setup checklist. Weblate completeness and review
state are informational; they do not become OwnTV release gates.

### 4c — packaging and picker visibility

Completed as part of 4a: all 23 community targets are packaged and picker-visible. Missing keys fall
back to English, and the picker shows each locale's informational translation percentage even when it
is incomplete.

### 4d — later validator and supply-chain hardening

Any future source-drift detection or Weblate-state ingestion is report-only unless the product policy
is explicitly changed. It must never restore a 100% community-language release gate. Keep source-hash
reporting, split status storage, or other scaling work out of 4a unless a concrete non-gating consumer
exists.

### 4e — APK delta

Build `standardRelease` before and after the Phase 4a catalogue flip, record the actual APK and `resources.arsc` delta,
and retain `localeFilters` as the rollback lever.
