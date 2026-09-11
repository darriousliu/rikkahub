# CMP rollback 14B — Android CLI GUI verification

- Date: 2026-09-11
- AVD: `Pixel_10_Pro_XL` (`emulator-5554`)
- APK: `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`
- APK SHA-256: `5824fc5f08c4d7a6f0f8aafbd8f17c3abfe616a20ae2e4107f60d5f82e67dca0`

The APK hash matched `/private/tmp/cmp-rollback-14b/android-ready.json`. No model request or credential access occurred. All conversations, files, and the assistant below were test-only identifiers. The fixture seed used the app's Kotlin serializers for `UIMessage` and its role values.

## Conversation deletion

| Step | Expected | Actual |
| --- | --- | --- |
| Seed two test conversations with separate Android-private upload files, then open the source conversation through the app GUI. | Both source and fork are visible; the files are independent. | Both test rows appeared and the source showed its offline text and checkboard image. |
| Swipe-delete source in History through the GUI. | The source database row and its exact attachment path are removed; the fork attachment remains. | The GUI showed `Conversation deleted`. The source path was absent. The database copy contained only the fork test record/path. The fork file remained readable with SHA-256 `bd4be71ac7efd6ecbe50b2f5d55e347d3a0d87d4cd3ce4efd96df7364e088fd0`. |
| Force-stop, cold-start, and reopen the fork through the GUI. | The independent fork remains readable. | Its offline text and purple/teal checkboard image remained readable after cold start. |
| Swipe-delete the fork through the GUI. | The final test record and its exact file are removed. | The GUI showed `Conversation deleted`; the fork path was absent and a database query for the test UUIDs and both exact paths returned no rows. |

Test attachment hashes before deletion: source `02e1c536cd221fa0bdd093a9d72d6559f630768a9c3faac77495b19357f433c4`; fork `bd4be71ac7efd6ecbe50b2f5d55e347d3a0d87d4cd3ce4efd96df7364e088fd0`.

## Assistant asset cleanup

| Step | Expected | Actual |
| --- | --- | --- |
| Create `CMP14B-asset-fixture` through the assistant GUI and assign offline avatar/background images through the system picker. | Each image is copied into the app's private upload directory. | Avatar `files/upload/9059c614-6dd6-4a3d-a017-74e02fa56913.jpg` had SHA-256 `aa5a66835a44d97eae81378e93e730ead4b208b2b6fbdb4aeccf983c638fb9e4`; background `files/upload/bd09ae0e-b886-4819-9f0a-9370e97aebac.png` had SHA-256 `d7077c375e7948a8d71402029e6e9026fc88f35c5baf0fe85df5e95657859c9d`. The GUI displayed `Background image set`. |
| Delete that exact assistant through its Actions → Delete → Confirm GUI. | Both exact private asset paths are deleted. | The assistant disappeared from the GUI list, and separate exact-path checks for both files returned `No such file or directory`. |

## Selected screenshots

1. [Two seeded conversations](/Users/liuzhenhui/AndroidStudioProjects/rikkahub/docs/references/evidence/cmp-rollback-14b-2026-09-11/android/05-history-fixtures.png)
2. [Source deleted in GUI](/Users/liuzhenhui/AndroidStudioProjects/rikkahub/docs/references/evidence/cmp-rollback-14b-2026-09-11/android/09-source-deleted-gui.png)
3. [Fork readable after cold start](/Users/liuzhenhui/AndroidStudioProjects/rikkahub/docs/references/evidence/cmp-rollback-14b-2026-09-11/android/11-fork-cold-readable.png)
4. [Fork deleted in GUI](/Users/liuzhenhui/AndroidStudioProjects/rikkahub/docs/references/evidence/cmp-rollback-14b-2026-09-11/android/12-fork-deleted-gui.png)
5. [Background set in assistant settings](/Users/liuzhenhui/AndroidStudioProjects/rikkahub/docs/references/evidence/cmp-rollback-14b-2026-09-11/android/35-assistant-assets-applied.png)
6. [Assistant avatar visible before deletion](/Users/liuzhenhui/AndroidStudioProjects/rikkahub/docs/references/evidence/cmp-rollback-14b-2026-09-11/android/36-assistant-detail-before-delete.png)
7. [Temporary assistant deleted in GUI](/Users/liuzhenhui/AndroidStudioProjects/rikkahub/docs/references/evidence/cmp-rollback-14b-2026-09-11/android/40-assistant-deleted-gui.png)

## Cleanup

The original settings file was restored byte-for-byte (SHA-256 `dd57e7ff72ca11a9d810b1fa1cfed1aefef454c4595e50059c18814935afcb86`). The test-only Downloads fixtures, private marker, temporary instrumentation source, private database/settings snapshots, layout dumps, and local fixtures were removed. The emulator display override was restored to `2560x1600` (physical `1344x2992`) and the AVD was stopped.
