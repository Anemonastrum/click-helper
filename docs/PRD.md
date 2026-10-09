# Product Requirements Document — FlashSale Helper

**Version:** 0.1.0 prototype  
**Date:** 2026-10-10  
**Platform:** Android 8.0+ (minSdk 26), Android AccessibilityService only  
**Project objective:** Enable an informed user to plan, preview and manually arm an on-device sequence of touches with a millisecond-formatted live clock and a scheduled start.

## 1. Problem

Generic macro recorders are hard to configure precisely: they lack clear countdown and state controls, coordinates can be difficult to target, screen layouts shift, and network clock offset can be confused with device gesture latency. This project is a focused user-defined scheduler for visible Android UI operations.

## 2. Principles

- **User-controlled:** The user creates every target, chooses the time, arms the schedule and starts countdown explicitly. An emergency STOP must remain visible.
- **No root / no hooking:** Only approved Android AccessibilityService and overlay APIs; no Xposed.
- **Transparent timing:** Distinguish device time, SNTP estimate, RTT, manual lead, deadline and actual dispatch deviation.
- **Test before act:** Dry run is on by default. No automated verification bypass or simulated “guaranteed checkout”.
- **Data minimization:** Keep settings locally. No screenshots, captured passwords, payment data or analytics uploads.
- **Graceful failure:** If the target package changes or the device rotates, abort instead of blindly tapping elsewhere.

## 3. User personas / jobs

- **Individual shopper:** Set a sale time and a series of visible touch points; start a countdown and monitor progress.
- **Android enthusiast / tester:** Evaluate device gesture scheduling deviation using logs and deterministic dry runs.

## 4. Functional requirements

| ID | Priority | Requirement | Acceptance condition |
|---|---|---|---|
| FR-01 | P0 | Enable service from Activity | Button opens Android Accessibility Settings; app explains consent |
| FR-02 | P0 | Floating overlay | Visible over foreground apps, draggable by heading, compact mode |
| FR-03 | P0 | Live clock | Displays `HH:mm:ss.SSS` with current device wall-clock time |
| FR-04 | P0 | Countdown | Displays `HH:mm:ss.SSS`, never negative, until dispatch deadline |
| FR-05 | P0 | Target selection | Crosshair user positions with touch; SAVE/CANCEL without passing picker gestures through |
| FR-06 | P0 | Persistent sequence | Stores up to 25 normalized targets, target package and orientation |
| FR-07 | P0 | ARM | Validates sequence, package, rotation and overlay obscuration; locks monotonic deadline |
| FR-08 | P0 | START | Starts countdown only from ARMED; at due time executes sequence |
| FR-09 | P0 | STOP | Cancels pending work, invalidates stale callbacks and keeps status visible |
| FR-10 | P0 | Dry run | Enabled by default, executes timing path but dispatches no touches |
| FR-11 | P0 | Gesture API | Uses `dispatchGesture` with bounded press duration |
| FR-12 | P1 | Time synchronization | Optional SNTP offset / RTT / jitter and age of last result |
| FR-13 | P1 | Manual lead | Per-profile anticipation in milliseconds; RTT weighting must be opt-in |
| FR-14 | P1 | Step delays | Per-target delay after previous gesture callback |
| FR-15 | P1 | Diagnostics | Private bounded log records target/attempt/deviation/error; never sensitive UI content |
| FR-16 | P1 | Manual GitHub publishing | workflow_dispatch builds tested APK, signs appropriately, posts APK + hash to GitHub Release |
| FR-17 | P2 | Visual calibration | Planned: marker nudge controls and touch lag percentile benchmark |
| FR-18 | P2 | View-based anchors | Planned: optional accessible view anchor fallback to coordinates |
| FR-19 | P2 | Conditional workflow | Planned: finite-state transitions on expected visible UI state |

## 5. User journeys

### Configure and schedule
1. User enables service, opens target app, opens overlay.
2. User picks targets using overlay crosshair. A target is recorded only after explicit **SAVE POINT**.
3. User opens app settings, specifies target local clock time `HH:mm:ss.SSS`, manual lead, tap duration and delays. Dry run stays on for test.
4. User positions overlay away from all targets. Optional SNTP sync.
5. User presses **ARM**, observes validation status, and presses **START**.
6. Overlay counts down; sequence executes/dry-runs; state becomes COMPLETED, STOPPED or ERROR.

### Abort
At any time while ARMED/COUNTDOWN/RUNNING user may press **STOP**. All subsequent scheduled actions become invalidated. An already submitted Android gesture may still complete (documented limitation).

## 6. State definitions

- `IDLE`: app/service ready; no deadline.
- `ARMED`: validated and locked target deadline, waiting for START.
- `COUNTDOWN`: waiting for dispatch deadline.
- `RUNNING`: processing steps serially.
- `COMPLETED`: all steps dispatched or dry-run simulated; not necessarily accepted by the target application.
- `STOPPED`: user cancellation or service interruption.
- `ERROR`: validation/dispatch failure; no additional steps.

## 7. Non-functional requirements

- App-private preferences, no backup of sensitive settings, no network requests beyond explicit SNTP.
- No busy-wait spin loop on UI thread. Scheduler is best-effort and jitter is measured rather than promised away.
- Display refresh is bounded by panel rendering and physical refresh rate, regardless of .SSS formatting.
- Defensive checks for device rotation, target package change and overlay interception.
- Unit tests for time conversion, DST/day rollover, lead calculation and monotonic deadline.
- Build CI on pull requests and push. Manual release only via Actions input.

## 8. Explicit non-goals

- Bypassing anti-bot systems, CAPTCHA, account/payment security, purchase quotas or platform terms.
- Auto-submitting financial transactions or capturing payment/account secrets.
- Guaranteeing inventory allocation, server acceptance or sub-millisecond delivery.
- Supporting arbitrary unattended operation on a locked or sleeping device.
- Privileged or root-level gestures, hooking app internals, or self-updating APKs.

## 9. Success measurements

- 100% of test gestures suppressed in dry-run mode.
- No following step after STOP (except a gesture already handed to Android).
- Clear measured dispatch deviation logs for first step.
- Release artifact contains an APK plus checksum, with production signing required for stable release.
- Targets survive app restart and cannot silently migrate across rotation or target package.

## 10. Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| RTT mistaken for shopping-app latency | Early/late triggering | NONE default; label half/full RTT as heuristic |
| Android scheduling jitter / phone sleep | Missed target time | On-screen, awake test sessions; no accuracy claims; telemetry |
| Incorrect coordinates after layout shift | Wrong button pressed | Revalidate orientation/package, dry run, stop, planned node anchors |
| Overlay covers gesture target | Missed/blocked input | Position check on ARM and before each step |
| User accidentally submits an order | Financial impact | Dry run default, human review of payment and terms |
| Accessibility disclosure / storefront restrictions | Distribution rejection | Explicit disclosure/consent, avoid excessive capabilities, policy review |
| Temporary CI debug signing | App updates fail | Prerelease label and documented stable keystore workflow |

## 11. Milestones

- **MVP (included source):** Core service, overlay, picker, time config, ARM/START/STOP, dry-run, optional SNTP, unit tests, CI & manual Releases.
- **M1 (planned):** Local benchmark harness, touch completion latencies, device-specific p50/p95, in-app log viewer.
- **M2 (planned):** Reusable profiles, element anchors, safe conditional workflows, tap target preview and import/export.
- **M3 (planned):** Accessibility improvements, localization, observed device compatibility matrix and automated Android instrumented tests.

## 12. Open questions

- Should multiple profiles be keyed by package name or named independently?
- Should a run require an explicit *allow gestures* confirmation after a successful dry run?
- How should process death and screen lock be represented in session history?
- What timing error budget is reasonable on supported devices and OEMs?
