# FlashSale Helper (Android)

A **Miuix Compose-designed**, AccessibilityService-only, no-root Android proof-of-concept for **user-configured, time-scheduled UI gestures**. Features include a floating **`HH:mm:ss.SSS`** clock, optional NTP server-time estimate, countdown, movable overlay, crosshair target picker, ARM / START / STOP, saved multi-step taps, dry-run execution, and local execution diagnostics.

> **Status: v0.1.0 prototype.** The Android APK is built by GitHub Actions once you upload the source. The implementation has not been device-tested here. Timed gestures do not guarantee precise on-screen execution or acceptance by a merchant's server. Respect the target app's terms and purchase limits; never use this to bypass verification or anti-abuse measures. Keep final payments under user control.

## Requirements

- Android **8.0+ (API 26)**; Android 12–16 behavior should be tested on actual devices.
- Android Studio, Android SDK **37.0** (minor API level 0), JDK 17 and Gradle **9.7.1** (Android Gradle Plugin **9.4.1**).
- No root, Xposed, LSPosed, `SYSTEM_ALERT_WINDOW`, or backend account required.
- Optional network access to an NTP server (UDP 123) for synchronization.

## Miuix interface

- The settings dashboard and draggable accessibility overlay are rendered using [Miuix](https://github.com/compose-miuix-ui/miuix) **0.9.4** on Jetpack Compose, with system-driven light/dark colors.
- Touch-point selection remains a custom full-screen Android view to preserve physical touch coordinates.
- ComposeView in the accessibility overlay has its own lifecycle and saved-state registry; it is removed when the overlay closes.
- This project uses Miuix under the terms of its Apache-2.0 license. The Miuix sources are not copied into this repository.

## What works in this prototype

| Capability | Implementation |
| --- | --- |
| Millisecond-formatted local and NTP-estimated time | Floating accessibility overlay |
| Countdown to next configured local-time occurrence | Monotonic deadline derived at ARM |
| ARM | Validates points, foreground package/rotation/overlay overlap and freezes deadline |
| START | Begins countdown, then runs user-configured gestures |
| STOP | Cancels pending actions and invalidates callbacks (in-flight gesture may finish) |
| Touch-point picker | Transparent full-screen selector with crosshair, SAVE and CANCEL |
| Multi-step touches | Normalized coordinates, per-step post-completion delay |
| Dry run | Walks sequence without dispatching gestures (enabled by default) |
| Clock sync | Four SNTP samples, low-RTT selection, offset/RTT/jitter readout |
| Early trigger | Explicit manually configured anticipation; optional half/full RTT heuristics |
| Safety checks | Same target package, same rotation, overlay-hit avoidance, bounded step count |
| Diagnostics | On-device private bounded `execution.log`, no screen captures |

**Not implemented:** screenshots, OCR, automatic target discovery, conditions based on application UI, gesture latency calibration, merchant-server synchronization, background reliability across sleep/lock, notification support, auto-payment, CAPTCHA handling, or guaranteed millisecond timing.

## Run it

1. Clone or download the repository and open it in Android Studio. Sync Gradle using JDK 17, Android SDK 35 and Gradle 8.13.
2. Build/install the `debug` variant (or launch `gradle :app:assembleDebug` if Gradle 8.13 is on PATH).
3. Open **FlashSale Helper** and tap **Enable AccessibilityService**. In Android Accessibility settings, enable **FlashSale Helper Controls**. Read and accept Android's warning only if you trust this application.
4. From its settings, choose a target time like `20:00:00.000`, set **manual early dispatch** and **tap duration**. Leave **Dry-run** enabled initially. Tap **Save settings**.
5. Open your desired target app. On the floating panel, choose **+ TARGET**, tap or drag to position the crosshair, and tap **SAVE POINT**. Add more points in sequence as needed.
6. The app settings allow editing the wait after each saved point. Return to the target application. Move the panel away from every target and optionally choose **SYNC** to sample NTP.
7. Press **ARM** to validate and lock the deadline; press **START** to begin waiting. The remaining time appears as `HH:mm:ss.SSS`. **STOP** invalidates subsequent scheduled actions.
8. Review diagnostics using `adb shell run-as com.example.flashsale cat files/execution.log` on a debuggable build.

**Countdown behavior:** target time is interpreted in the phone's timezone, on the next occurrence of that wall-clock time; if today's time has passed, the next day is chosen. The planned dispatch moment is target time minus manual anticipation minus the **optional** RTT-derived heuristic. Using `None` for RTT lead is strongly recommended: RTT is *not* a measured touch-to-server latency. Clock synchronization estimates the time of the NTP server, **not** the retail platform's clock.

**STOP limitation:** Android has no guaranteed mechanism to retract a gesture already handed to `dispatchGesture()`. STOP prevents future scheduled gestures and ignores callbacks from a cancelled run, but the current in-flight gesture can finish.

## Build commands

```bash
# Requires installed Gradle 8.13, Java 17, Android SDK 35.
gradle :app:testDebugUnitTest :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

This source snapshot intentionally uses a globally installed Gradle 8.13 (`gradle` command) and GitHub's `setup-gradle` action rather than claiming to include a Gradle Wrapper JAR that could not be verified. If you want to commit an official wrapper locally, run `gradle wrapper --gradle-version 8.13` and then `./gradlew :app:assembleDebug`.

## Manually build and publish on GitHub

1. Push this repository to GitHub, with the default branch **main**.
2. In **Actions → Build APK and publish GitHub Release → Run workflow**, enter a version tag like `v0.1.0-rc1`.
3. Choose **debug** for a test build. This publishes a **pre-release APK**, plus SHA-256 checksums. Debug signing on GitHub runners is temporary; subsequent debug APKs may need a reinstall.
4. For a production-installable release, configure the four signing secrets and choose **release** (see [Release guide](docs/RELEASING.md)). Never publish or lose your production keystore.
5. Download APK from the **Releases** page; Action run also provides an artifact.

The workflow is manual (`workflow_dispatch`); routine CI builds/test on push and pull request but **does not publish releases**. Existing release tags are not overwritten.

## Repository map

```text
.github/workflows/ci.yml               Test and build on pushes/PRs
.github/workflows/manual-release.yml   Manually publish an APK to Releases
app/src/main/java/com/example/flashsale/
  FlashAccessibilityService.kt         Lifecycle, ARM/START/STOP, gesture executor
  FloatingControls.kt                  Floating overlay and crosshair picker
  MainActivity.kt                      Settings and saved steps
  Models.kt                            Data objects and run states
  NtpSync.kt                           SNTP measurements
  Scheduler.kt                         Clock/deadline calculations
  SettingsStore.kt                     Private local preferences
app/src/test/                           Timing unit tests
docs/PRD.md                             Product requirements document
docs/SYSTEM_DESIGN.md                   Architecture and engineering decisions
docs/RELEASING.md                       GitHub Secrets and release walkthrough
docs/TEST_PLAN.md                       Device/manual acceptance checklist
```

## Privacy and safety

All points/settings are stored in app-private Android preferences. No personal purchase details are read or transmitted. Network permission is only for the optional SNTP request. The app does **not** use Accessibility to read UI text or collect account data. Its gestures operate on the visible foreground app; no root hooks, hidden APIs, bypass of platform restrictions, auto-CAPTCHA, or order submission integration. Android warns about sideloaded AccessibilityServices for good reason: inspect the code before enabling.

For Android Accessibility and gesture behavior, see [AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService). Build compatibility: [AGP 8.13 release notes](https://developer.android.com/build/releases/agp-8-13-0-release-notes).

## License

MIT, see [`LICENSE`](LICENSE).
