# Contributing to OwnTV

Thanks for your interest in improving OwnTV! Contributions of all sizes are welcome — bug fixes,
features, docs, or ideas.

## Ground rules

- OwnTV is a **player only**. Please keep its bring-your-own-source positioning — no bundled channels,
  playlists, or content, and nothing that facilitates access to unauthorized streams.
- It targets **Android TV** (D-pad / leanback first). Keep navigation remote-friendly.
- Match the existing code style and structure (Kotlin, Jetpack Compose for TV, Koin, Room).

## Project setup

1. Install **Android Studio** (a version that supports **AGP 9.x**).
2. Clone your fork and open the project; let Gradle sync.
3. Run the `app` config on an **Android TV emulator or device**, or build from the CLI:
   ```bash
   ./gradlew assembleDebug
   ```

A quick tour of the codebase lives in the [README](README.md#%EF%B8%8F-how-it-works-backend), and the
full design/architecture is in [`extras/`](extras/).

## Workflow (branch → Pull Request)

We use a simple fork-and-PR flow:

1. **Fork** the repo (or, if you're a collaborator, create a branch directly).
2. Create a branch off `main`:
   ```bash
   git checkout -b feature/short-description
   ```
3. Make your changes and **commit** them with clear messages — these become the release changelog,
   so write them for a reader (e.g. `Fix EPG grid scroll desync on D-pad`).
4. Push your branch and **open a Pull Request** against `main`.
5. CI builds the app on your PR — please make sure it's green.
6. A maintainer reviews and merges.

> Keep each PR focused on one thing. Small, reviewable PRs get merged faster.

## Commit messages

- Write in the imperative: "Add…", "Fix…", "Refactor…".
- One logical change per commit where practical.
- Add `[skip ci]` to the message for doc-only or config commits that don't need a build.

## Reporting bugs / requesting features

Open an issue with what you expected, what happened, your device / Android TV version, and steps to
reproduce. Logs (`adb logcat`) help a lot for playback issues.

By contributing, you agree your contributions are licensed under the project's [GPLv3 License](LICENSE).
