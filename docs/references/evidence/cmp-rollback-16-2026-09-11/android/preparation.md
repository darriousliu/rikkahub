# CMP rollback 16 Android GUI preparation

- Date: 2026-09-11
- Owner: Android GUI only
- Target AVD: `Pixel_10_Pro_XL`
- Required display override: `2560x1600` (preserve; never reset)
- Expected package: `me.rerere.rikkahub.debug` (confirm from final APK)
- Repository source under test: parent-supplied final APK built after source freeze; preparation HEAD was `056166f83b2a46dda9a4c81679041b7b6044fc8b`.

## Start gate

Do not begin data setup, installation, app launch, backup, restore, or GUI actions until the parent agent supplies all of:

1. final APK absolute path and SHA-256;
2. source-freeze commit and confirmation that the APK came from it;
3. explicit confirmation that the local server is ready at `127.0.0.1:18776`;
4. confirmation that the parent has inspected the uploaded ZIP before Android sends a remote delete.

The Android agent must not close, wipe, snapshot-reset, or otherwise alter another process or AVD. It may boot only `Pixel_10_Pro_XL` if it is stopped, retaining the display override.

## Isolated state and evidence plan

All private Android artifacts live below `/private/tmp/cmp-rollback-16/android` with mode `0700`. Before any fixture or settings change:

1. Stop the target app.
2. Copy the complete private app state, including database files, settings, and user files, to a `0700` private baseline directory.
3. Record a per-file SHA-256 manifest, relative path, byte size, and file count. Do not dump full settings or archive contents into repository evidence.
4. Only then import or seed the uniquely named synthetic fixture. All fixture conversation and attachment titles/paths begin with `cmp16` and are resolved by exact identifier before any deletion.

At completion, stop the app, restore the private baseline, and compare every recorded file path, file count, size, and SHA-256. Restore failure or any mismatch fails the run. Do not reuse rollback-15 snapshots.

## Required GUI protocol

Run the sequence independently for WebDAV and S3 with one uniquely identified synthetic fixture each. The service configuration stays loopback-only and uses the parent-provided test-only values; no Keychain, model provider, or external network is used.

1. Configure WebDAV at the supplied loopback endpoint and path `cmp16-android`, then run GUI backup. Use `adb reverse tcp:18776 tcp:18776` when supported; otherwise use `10.0.2.2` consistently for both transports.
2. Verify the uploaded ZIP was retained remotely. Parent must inspect the actual uploaded ZIP before the Android flow deletes it.
3. In the GUI, delete the exact synthetic conversation and confirm its disappearance. Then delete the exact synthetic attachment.
4. Restore from the remote ZIP through the GUI.
5. Use the product's original restart action.
6. Verify through the GUI that the restored conversation has exactly two expected bodies, the relevant backup settings are restored, and both the `upload` and legacy attachment are restored.
7. Only after parent confirmation of ZIP inspection, use the GUI to delete the remote ZIP and confirm its absence.
8. Repeat for S3, using path-style bucket `cmp16-android`, region `auto`, and the same selected loopback host.

## Private archive inspection

The retained private copy of each GUI-uploaded ZIP is inspected without publishing contents. Checks record only digest/count/boolean results:

- ZIP CRC succeeds;
- settings JSON is readable;
- archive entries are present under their expected names;
- entries include `rikka_hub.db`, `rikka_hub-wal`, and `rikka_hub-shm` when WAL is present;
- independent SQLite inspection renames the main entry to `rikka_hub` and its sidecars to `rikka_hub-wal` and `rikka_hub-shm` before opening it;
- exact synthetic conversation count, two-body condition, settings condition, and attachment conditions pass.

Never publish credentials, settings values, full ZIP contents, database rows, or screenshots containing non-synthetic user data.

## Evidence

Repository evidence contains only this plan, a redacted readiness record, screenshots of synthetic data, and summary digest/count/boolean results. Private files, screenshots, logs, saved APKs, backups, and copied ZIPs remain in `/private/tmp/cmp-rollback-16/android` until cleanup is safe.
