# CMP12 Android GUI verification

Date: 2026-09-11

Device: `Pixel_10_Pro_XL` (`emulator-5554`), Android 17 / API 37.

Artifact: `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`.
The local artifact SHA-256 and installed `base.apk` SHA-256 both equal
`e530ad0d2db2347d7dcdb720b3567cac467a320e1e180b73698ed7cba4b58e95`.

## Fixture and permissions

The private DataStore was backed up without printing its contents to
`/private/tmp/cmp-rollback-12/android-input.pb` (SHA-256
`40d6f4828ef0bb9302b5da34138cf270e1046ebae6c962696315ce525b3dd8d5`).
`fixture.py seed android` reported `preservedOtherEntries: true`; the seeded
file SHA-256 was `41d59ee1062fc181fc39f44d43f2074a9d61dc6fc1d1f87d81c9c8dd0cee897c`.

`ACCESS_LOCAL_NETWORK` was initially `granted=false` and was temporarily
granted for the `10.0.2.2:18772` fixture. Parent restored it to `granted=false` after validation. The app was force-stopped after the test. The pre-restore private
readback is `/private/tmp/cmp-rollback-12/android-current.pb` (SHA-256
`ad331d96860eead89281d39a7ff4bd08754944abb82474457f72f2f43fce29c1`).

## Results

All requests below were initiated using the visible `Test Search` input and
`Run Test` button on Settings > Search Service > CMP12 JS. The fixture control
endpoint was used only to select its fixed response mode.

| Case | Expected | Observed | Evidence |
| --- | --- | --- | --- |
| Initial success | fixed answer and item | `CMP12 android success`, `1. CMP12 android item`, fixture URL and body rendered | `gui-android-success.png` |
| Fixed HTTP error | `CMP12_HTTP_503` | `Error: CMP12_HTTP_503 CMP12 fixed error` rendered with QuickJS stack | `gui-android-error.png` |
| Retry after error | success renders again | fixed answer and item rendered | `gui-android-retry-success.png` |
| Waiting request cancellation | leaving detail cancels active request | switched fixture to `wait`, clicked visible Run Test, then immediately used detail-page Back; returned to Search Service list | `gui-android-cancelled.png` |
| Re-enter after cancellation | provider can run again | re-opened CMP12 JS, entered `reentry`, and success rendered | `gui-android-reentry-success.png` |
| Cold start persistence | provider and script survive, then execute | after force-stop/launch, CMP12 JS remained in provider list; its `10.0.2.2` fetch script was visible; `coldstart` rendered success and item | `gui-android-cold-provider.png`, `gui-android-cold-script.png`, `gui-android-cold-success.png` |

The Android UI confirms the wait request was started and the detail composition
was exited. Parent confirmed fixture request 10 was cancelled after 10.926 seconds, earlier than the normal 30 second timeout. The event is recorded by the fixture server, not displayed by the Android UI.

No Gradle task was run during this GUI task. No production source, settings UI
fields, or unrelated device data were edited.

Parent restored search configuration and verified it by readback; only normal `launch_count` differs from the original preferences. Local network permission is restored, and the test emulator is stopped. See `android-cleanup.json`.
