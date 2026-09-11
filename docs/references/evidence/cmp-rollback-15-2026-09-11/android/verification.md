# CMP rollback 15 Android GUI verification

- Date: 2026-09-11
- Device: Pixel_10_Pro_XL, Android GUI override 2560x1600
- Package: `me.rerere.rikkahub.debug`
- Final APK SHA-256: `693ded50032bef65fd4b59e0e298b1ab3efe19deef70150c854f91f0969039ba`

## Passed coverage

1. Final APK opened the Local backup page. The file picker and save picker were each opened and cancelled before any real import or export.
2. ChatBox fixture import displayed the fixed `CMP15 android Imported` conversation in the drawer. Re-importing the same fixture still showed one visible conversation entry; provider additions were not used as a de-duplication expectation.
3. Cherry Studio fixture added `CMP15 android provider`. Its GUI detail showed fixture URL `https://cmp15.invalid/v1`, one model, and a masked API key.
4. Native ZIP import wrote `upload/cmp15-android-native.txt`; its on-device SHA-256 matched the fixture: `69b8959e3a89933ec70c1a98cc2889bd546f82462072d968a116fc34f1d0040e`.
5. GUI export created `rikkahub_backup_20260911_145121.zip` in Downloads. The private copy SHA-256 was `e9bcce8fd37a16d68c727a057ab6ecd62d1ac7859543f0be7fa8637a33860669`. Private inspection passed ZIP CRC, readable settings JSON, SQLite database header, marker bytes, and found 5 entries with 3 fixture providers.

## Cleanup

The three fixture files, exported ZIP, and marker were deleted by exact path. The app was stopped and the private pre-test database/settings/user-files archive was restored (archive SHA-256 `a37bd475c99fa431481676e23ddb00ee5ff6a3804ea51a3bc66d3ef960d4abd1`), removing the test conversation and provider. The restore command exited successfully and exact test-file absence was checked afterwards. No per-file post-restore hash comparison was performed.

## Corrected WAL-backed supplementary export check

The shortest GUI rerun imported only the rebuilt ChatBox fixture, accepted the restart prompt, and exported through the Local backup save picker. Cherry Studio and native-marker flows were not rerun. The private GUI export `rikkahub_backup_20260911_150858.zip` has SHA-256 `41ce7358c8e5b4faa69d05adbdf85c507d2eb5fb64a24d7ebeea57f3520e2f0a` was independently rechecked by the parent and then deleted with the private copies.

The corrected private inspector restored the database main entry as `rikka_hub` and placed its sidecars as `rikka_hub-wal` and `rikka_hub-shm` before opening SQLite. It produced this redacted summary:

```json
{
  "zipCrcPassed": true,
  "settingsJsonReadable": true,
  "databaseEntryHasSqliteHeader": true,
  "fixtureProvidersInExport": 1,
  "entryCount": 4,
  "databaseOnlySupplement": true,
  "databaseWalIncluded": true,
  "databaseSidecarBasenameNormalized": true,
  "fixedConversationCount": 1,
  "fixedConversationTitleMatches": true,
  "fixedMessageNodeCount": 2,
  "fixedChineseBodyAndAnswerMatch": true
}
```

After the supplementary check, only its exact Downloads fixture and export paths were deleted; direct path-existence checks confirmed both absent. The app was stopped and the pre-rerun user-state archive (SHA-256 `a37bd475c99fa431481676e23ddb00ee5ff6a3804ea51a3bc66d3ef960d4abd1`) was restored successfully. No per-file post-restore hash comparison was performed.

## Scope limits recorded after review

- The GUI verification showed the imported conversation title in the drawer; it did not open the conversation body.
- The initial private SQLite lookup opened `rikka_hub.db` without restoring the archive's WAL to the runtime name `rikka_hub-wal`; that lookup is invalid and is not evidence of a persistence/export failure. The corrected supplementary check above supersedes it. No database rows or message contents were printed.
- The AVD was stopped after verification. Its `2560x1600` display override was retained; it was not reset.
- Earlier private test artifacts were deleted as recorded. The parent independently re-ran the corrected inspector, confirmed its results, and removed the rebuilt supplementary fixture set, pre-rerun backup, new private GUI export and the complete private Android test directory. The repository screenshots listed below remain.

Screenshots: `local-backup-page.png`, `chatbox-conversation.png`, and `cherry-provider.png`.
