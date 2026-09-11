# CMP rollback 16 — macOS desktop GUI evidence

The final distributable at `desktopApp/build/compose/binaries/main/app/RikkaHub.app` was verified before launch. Each test run used only a private `user.home` under `/private/tmp/cmp-rollback-16/desktop/profile` and a PID-specific foreground check before every CGEvent. GUI actions targeted only the isolated test PID; the user's original PID 99566 was not stopped or modified. An early generic read-only process lookup was replaced with explicit PID targeting.

## Offline service and test data

- WebDAV used the loopback `cmp16-desktop` path; S3 used the loopback, path-style `cmp16-desktop` bucket.
- Configuration was pre-seeded while the app was stopped. Screenshots retain no unmasked credential.
- The unique fixture was `CMP16 Desktop Sync Fixture`, containing exactly the two CMP16 desktop message bodies.
- The registered FileKit markers were the unique upload and legacy attachment names plus the later local-import-only marker. Existing files were never overwritten and cleanup was hash-gated.

## WebDAV GUI result

1. The GUI test connection reported success, and GUI backup created `backup_20260911_194751.zip`.
2. The uploaded ZIP was copied to private mode-0700 evidence storage for parent review. Parent independent archive inspection is recorded in `webdav-parent-archive-check.json`.
3. The unique conversation was deleted with its GUI long-press Delete action; the two registered attachment markers were removed only after exact SHA-256 matches.
4. GUI restore reported success and the application's own restart prompt was accepted. After restart, the fixture title and both bodies were visible, and both attachment marker SHA-256 values matched their expected values.
5. The initial Color Mode observation is excluded as an archive-settings assertion: it is managed outside SettingsStore. Parent review instead verified the archive-backed backup reminder time. GUI backup changed it from unrecorded to `2026 Sep 11 19:47:51`; GUI restore showed `未记录备份时间` again, matching the archive value `0`. See [settings evidence](settings-restoration-evidence.json). The restored WebDAV endpoint and username were also visible; its password remained masked.
6. GUI deletion of the remote ZIP reported success and the list count became zero.

## S3 GUI result

1. The GUI test connection reported success, and GUI backup created `backup_20260911_200854.zip`.
2. The private ZIP copy was retained for parent review; parent independent inspection is recorded in `s3-parent-archive-check.json`.
3. The same GUI deletion, exact-marker removal, GUI restore, original restart, fixture/body verification, and attachment-hash verification all completed.
4. S3 backup changed the same archive-backed reminder time to `2026 Sep 11 20:08:54`; restore returned it to the archive value `0`, visibly `未记录备份时间`.
5. GUI deletion of the S3 archive completed successfully. The loopback server ended with no desktop WebDAV or S3 archive remaining.

## Local backup-file picker result

The Local tab's **备份文件导入** action opened the macOS system picker owned by the isolated PID. It imported only `cmp16-desktop-local-only.zip`, whose sole entry is `upload/cmp16-desktop-local.txt`; no Chatbox import was used. The application displayed restore success and its original restart prompt.

After restart the source ZIP SHA-256 remained `081a651947f1a9f355cdf836647a6eba60f63e3bb09bfa9e2c296fd6df42e52e`; the restored marker was 38 bytes with SHA-256 `12e8fc53cd6473baccea9c57fdcc6079a75e554a24fbd35948c119e50a6a9977`. No `restore_*` file remained under the isolated profile or the RikkaHub FileKit root.

## Cleanup

The final isolated PID and private profile were removed. The two remote-test markers and local picker marker were removed only after their expected hashes matched. Parent archive review completed before the private `expected.json`, ZIP copies and remaining temporary files were deleted. The loopback service exited after the parent closed the desktop agent; the parent independently confirmed no remaining listener. See [parent cleanup](parent-cleanup-check.json) and [overall cleanup](../cleanup.json).

Machine-readable evidence: [final-verification.json](final-verification.json). Parent review also checked [restored attachment bytes](s3-parent-restored-files.json).

Captured states: [WebDAV upload](webdav-after-backup.png), [WebDAV restore](webdav-restore-dialog.png), [restored messages](webdav-restored-messages.png), [S3 upload](s3-after-backup.png), [S3 restore](parent-s3-restore-dialog.png), [local picker result](local-picker-import-result.png).
