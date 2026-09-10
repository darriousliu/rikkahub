# CMP12 iOS GUI verification

## Environment

- Simulator: iPhone 17 Pro Max, iOS 27.0, UDID `03C090DA-107B-4F9F-BCCD-8D5265D32820`.
- Build: XcodeBuildMCP `build_sim` for `iosApp/iosApp.xcodeproj`, scheme `iosApp`, Debug. Build succeeded.
- Artifact: `RikkaHub.app/RikkaHub`, SHA-256 `8c54f9f009711617ae6685ca490c5679413bc8c32e16195afee094b484a01569`.
- Bundle ID: `me.rerere.rikkahub.ios`.
- Seed: `ios-before.pb` SHA-256 `78a1d0a8abd27c4ed0ce9d08c132b4ae921f26645d12731b97d0479e01aa30d3`; seeded/current SHA-256 `02f6cff0b3345b4273900019c6e324242b98216625fdb618fc61c4f30da87345`. The supplied seed script reported that all non-CMP12 preference entries were preserved.

## Artifact provenance

XcodeBuildMCP `build_sim` returned this exact app path, which was then passed unchanged to `install_app_sim`:

`/Users/liuzhenhui/Library/Developer/XcodeBuildMCP/workspaces/rikkahub-2353818a8111/DerivedData/iosApp-5ff671450a50/Build/Products/Debug-iphonesimulator/RikkaHub.app`

The installed bundle read from the assigned simulator is:

`/Users/liuzhenhui/Library/Developer/CoreSimulator/Devices/03C090DA-107B-4F9F-BCCD-8D5265D32820/data/Containers/Bundle/Application/CA1D6773-BBD0-4169-8B4E-EBC584B18298/RikkaHub.app`

| File | XcodeBuildMCP app SHA-256 | Installed bundle SHA-256 | Match |
| --- | --- | --- | --- |
| `RikkaHub` | `8c54f9f009711617ae6685ca490c5679413bc8c32e16195afee094b484a01569` | `8c54f9f009711617ae6685ca490c5679413bc8c32e16195afee094b484a01569` | Yes |
| `RikkaHub.debug.dylib` | `6c3fd1aa5e288786bd9287284fc2044f1973b6c642abae6b3ce2d3771ab8807b` | `6c3fd1aa5e288786bd9287284fc2044f1973b6c642abae6b3ce2d3771ab8807b` | Yes |

No other DerivedData output was inspected or used for this provenance check.

## Results

| Case | Expected | Actual |
| --- | --- | --- |
| Initial success | Fixed answer and item render | Query `cmp12-iOS-query` displayed `CMP12 ios success` and `1. CMP12 ios item`. Server request 1 had the expected iOS header, non-empty query, limit 10, and HTTP 200. |
| Fixed error | Error text includes `CMP12_HTTP_503` | The same real Test UI displayed `Error: CMP12_HTTP_503 CMP12 fixed error`, with the QuickJS call stack. Server request 2 returned HTTP 503. |
| Retry | A subsequent run works | After changing only the local fixture to `ok`, the same Test UI again displayed the fixed answer/item. Server request 3 returned HTTP 200. |
| Leave while waiting | In-flight request is cancelled on leaving detail | After fixture mode `wait`, server request 4 was observed, then the visible back control left the detail page. The server recorded `cancelled` after 10.149 s, earlier than the normal 30 s timeout and before the fixture's 45 s wait ends. |
| Reopen and run | Provider stays usable after cancellation | CMP12 JS was reopened from Search Services; its saved script still targeted `127.0.0.1:18772/ios/search`. Query `cmp12-ios-reopen` succeeded; server request 5 returned HTTP 200. |
| Cold start | Provider/script persist and run again | The app was stopped and launched through XcodeBuildMCP. Search Services still listed CMP12 JS, and its full saved script was present. Query `cmp12-ios-cold-start` succeeded; server request 7 returned HTTP 200. |

## Evidence

- `gui-ios-success.png`: initial success state.
- `gui-ios-error.png`: fixed HTTP 503 state.
- `gui-ios-retry-success.png`: success after retry.
- `gui-ios-cold-start-success.png`: cold-start success, including item title, URL, and text.

## Limits and cleanup

- The Build iOS Apps plugin's `tap` did not trigger Compose click handlers in this runtime. Its independent HID `touch`, `type_text`, and `swipe` tools drove all visible UI actions; no macOS-global Simulator UI or keyboard automation was used.
- Raw evidence PNGs were exported from the assigned simulator only. No production code or project configuration was changed.
- The test app is stopped. Private recovery artifacts remain in `/private/tmp/cmp-rollback-12`: `ios-before.pb`, `ios-input.pb`, `ios-seeded.pb`, `ios-current.pb`, and `ios-container-path.txt`. Parent restored the original preference and read it back; the complete file matches the original SHA-256. See `ios-cleanup.json`.
