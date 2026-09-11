# CMP rollback 14B — desktop GUI verification

Date: 2026-09-11

## Artifact and isolation

- Final app executable: `desktopApp/build/compose/binaries/main/app/RikkaHub.app/Contents/MacOS/RikkaHub`
- SHA-256: `47e94214978380da758e8b7324415c4f748023aba7648034b260e9cb9f96c3a4`
- The SHA exactly matched `/private/tmp/cmp-rollback-14b/desktop-ready.json` before the final app was started.
- Every final run used `JAVA_TOOL_OPTIONS=-Duser.home=/private/tmp/cmp-rollback-14b/desktop/profile`.
- The independently confirmed final GUI PIDs were 74263, then 75635 after cold restart. Both were stopped after use. Existing desktop apps were not operated; process metadata checks were used to identify the isolated test runs.
- All fixtures were offline generated images. No model request or secret access occurred.

The parent agent reported that the final 385 code regressions and desktop package passed. This desktop verifier did not rerun those code tests.

## Registered fixture file hashes before GUI actions

| Fixture | SHA-256 |
| --- | --- |
| Shared legacy attachment | `957881807f4a9218b17be4d255c98bc8977cd284d2e584f18730a6e72266e050` |
| Source-only upload attachment | `8590576d250937d6eeb13e5d6c637e130888155cb60ada80e1776dfd5e53cfb8` |
| Shared assistant background | `0ae9250414556c515b4b59ed5e638d26c4cbfb24d7ee02905eb080056e5de66b` |
| Assistant A-only avatar | `7cde296f5e74a33b0373a5005fd178321a79919cc4ce330fee7c28032f7913fc` |
| Favorite-only retained file | `0ccb340dbd3dbaea26565e398f4cd763b4ba67309731aa9bbefe7a6a00c1b061` |

Only these UUID-named paths were created or checked. The app's attachment root was never enumerated.

## Steps and results

| Step | Expected result | Actual result | Evidence |
| --- | --- | --- | --- |
| Open `CMP14B-SOURCE`, containing the source-only upload | Source content is visible before deletion | GUI displayed source text and fixture image | [01-source-before-delete.png](01-source-before-delete.png) |
| Long-press source in Messages, then press the unique Delete item | Source record and exclusive upload are removed; shared legacy file remains for retained fork | Source DB row disappeared; upload file was absent; shared legacy file retained its original SHA. Retained message JSON had two legacy URL references: selected image and unselected tool output. | [02-source-deleted-retained-present.png](02-source-deleted-retained-present.png) |
| Select `CMP14B-RETAINED` | Selected retained branch displays the legacy image | AX tree named `CMP14B retained selected legacy image`; screenshot visibly shows the blue synthetic image and branch control `1/2`. | [03-retained-before-cold-restart.png](03-retained-before-cold-restart.png) |
| Cold-restart same profile on final package | Retained conversation and image return without source | Final PID 75635 cold-started directly in `CMP14B-RETAINED`; AX and screenshot again show the selected legacy image. | [04-retained-cold-start.png](04-retained-cold-start.png) |
| Long-press and GUI-delete the last retained fork | Last conversation/node references and shared legacy file are removed | Both CMP14B conversation rows and nodes were zero; shared legacy attachment was absent. Favorite fixture row remained and its file SHA stayed `0ccb…c061`. | [05-retained-deleted.png](05-retained-deleted.png) |
| In assistant A Basic settings, press background Remove | A loses the old shared background but B's reference preserves it | GUI exposed “已设置背景图片” then removal. Shared background remained with SHA `0ae9…e66b`; A avatar remained with SHA `7cde…13fc`. | [06-assistant-a-background-cleared.png](06-assistant-a-background-cleared.png) |
| GUI-delete assistant A through its row-local Actions sheet and confirmation | A-only avatar is removed; B's shared background remains | Settings contained only B among the CMP14B assistants; A avatar was absent; shared background retained SHA `0ae9…e66b`. | [07-assistant-a-deleted.png](07-assistant-a-deleted.png) |
| GUI-delete assistant B through its row-local Actions sheet and confirmation | Last background reference is removed | B no longer appeared in isolated settings; shared background was absent. Favorite fixture remained with SHA `0ccb…c061`. | [08-assistant-b-deleted-background-cleaned.png](08-assistant-b-deleted-background-cleaned.png) |

Final isolated database state: zero CMP14B conversation rows, zero CMP14B message-node rows, one retained favorite fixture row. No production source file was modified and no commit was made.

Parent verification independently confirmed these database counts, all four GUI-deleted files absent, and the favorite isolation control's exact hash. The parent then removed that fifth test image; all five registered desktop fixture files are absent. See [parent-disk-verification.json](parent-disk-verification.json). The private disposable profile and remaining test tools were removed after verification. The favorite used a separate file; candidate deletion with a favorite reference is covered by code tests, not this GUI control.

## Screenshot checksums

| Screenshot | SHA-256 |
| --- | --- |
| `01-source-before-delete.png` | `499411e7933b88905d8956908ffcfb9f77b830512c2a2d5e0ccca03a170e7e59` |
| `02-source-deleted-retained-present.png` | `313ffa455e0a08d467c10be45a0cbfc5959db5beaed2b4a4e8a30fac3665057f` |
| `03-retained-before-cold-restart.png` | `a647d69a31e78051a080962b5a038ce8277d0ce770f87c9a37715d814e5f8180` |
| `04-retained-cold-start.png` | `a647d69a31e78051a080962b5a038ce8277d0ce770f87c9a37715d814e5f8180` |
| `05-retained-deleted.png` | `8ff7fb92fa995134110ec70c63990a13edd0bb106bb2901e14e72eec3cae3646` |
| `06-assistant-a-background-cleared.png` | `bb831005b551b0ae847ffdd16f2cb57d2917adbf692e84c9e1ac352feced4a7c` |
| `07-assistant-a-deleted.png` | `ce17a9ae6f75b6d6e0adcc039bb8408a6b5a9d3e0aac73b61e8aca86055fd70b` |
| `08-assistant-b-deleted-background-cleaned.png` | `2dad732a3005ad4e518c28c4ecc97a4f802952d925518c727532a52759ce8914` |
