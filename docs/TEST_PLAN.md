# Test Plan

## Automated JVM unit tests

`SchedulerTest.kt` covers target occurrence today/tomorrow, monotonic deadline math and explicit RTT compensation. Run `gradle :app:testDebugUnitTest` with Gradle 8.13, Android SDK 35 and JDK 17.

## Manual device acceptance checklist

- [ ] Install debug APK; accessibility service initially disabled; user explicitly enables it.
- [ ] Floating overlay appears on target app; heading drag moves it; MIN collapses; STOP stays accessible.
- [ ] Local clock renders `HH:mm:ss.SSS` and updates; NTP clock shows fallback when offline.
- [ ] Crosshair picker moves as user drags and only saves on SAVE; CANCEL never creates a point or taps target app.
- [ ] Save at least three points; edit post-gesture delays; data survives service restart.
- [ ] With dry-run selected, ARM validates and START counts down; no app input occurs.
- [ ] With a **safe, non-financial test activity**, disable dry run and confirm correct ordered gestures.
- [ ] Press STOP during countdown: no new gesture is submitted.
- [ ] Press STOP between steps: subsequent steps are cancelled.
- [ ] Rotate phone: ARM refuses old coordinates; a running sequence aborts.
- [ ] Move overlay over a saved target: ARM refuses to execute.
- [ ] Switch foreground app during a run: sequence aborts before subsequent gesture.
- [ ] Test inaccessible NTP / network lost: service remains usable using device time.
- [ ] Examine execution log for expected deviation/error events; no app screen contents or credentials.
- [ ] Test on at least one Android 12, 14 and 15/16 device with gesture navigation and status bar visible.
- [ ] Run GitHub CI successfully and manually publish debug prerelease with SHA-256 checksum.
- [ ] Production build fails without signing secrets; with configured secrets `apksigner verify` passes.

## Performance telemetry

For a safe test target, record planned deadline and actual `dispatchGesture()` submission. Repeat at least 30 times per device with the screen on, report median/p95/maximum error and clock offset/RTT/jitter. Do not interpret `onCompleted()` time as receipt by a shopping server.

## Known gaps

No instrumented tests/emulator tests are provided. No latency or power benchmarks have been recorded for a real handset. Android/OEM-specific overlay coordinate and power-management behavior needs device verification before claiming readiness.
