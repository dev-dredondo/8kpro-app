<!-- Thanks for contributing to OwnTV! Please fill this in so it's easy to review. -->

## Before you open this PR (required)
<!-- These are mandatory — PRs that duplicate an existing in-app feature are closed. -->
- [ ] I read the **[User Guide](https://github.com/ahXN00/OwnTV/blob/main/extras/USER_GUIDE.md)** and checked the app's **Settings** and existing **functions** — this change isn't already possible in the app.
- [ ] I searched existing issues/PRs and this isn't a duplicate.

## What does this PR do?
<!-- A clear summary of the change and how it works. -->


## Which issue / improvement does it address?
<!-- Link the issue it fixes ("Closes #12"), or describe the upgrade it makes and why it's worth it. -->


## How was it tested?
<!-- IMPORTANT: OwnTV is a TV app — please test on a REAL Android TV / Fire TV device, not the emulator
     only. The emulator misses a lot: HDR, hardware decoding, surround/passthrough, and real remote
     (D-pad) behaviour. -->
- **Device(s) tested on:** <!-- e.g. Fire TV Stick 4K Max, NVIDIA Shield, TCL Google TV -->
- **What you exercised:** <!-- e.g. played a 4K HDR movie, switched audio track on live, EPG catch-up, D-pad nav -->

## Checklist
- [ ] Builds locally (`./gradlew assembleDebug`) and CI is green
- [ ] **Tested on a real Android TV / Fire TV device** (not emulator only) — D-pad/remote navigation works
- [ ] No user data is wiped (Room migrations stay additive)
- [ ] Keeps OwnTV player-only (no bundled channels/content)
- [ ] Commit messages are clear (they become the release notes)
