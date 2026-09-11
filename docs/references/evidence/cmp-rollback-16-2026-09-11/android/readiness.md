# CMP rollback 16 Android readiness

- Target AVD is running as `emulator-5554`.
- Boot-completed property is `1`.
- Display override is retained: `2560x1600` (physical size reported as `1344x2992`).
- ADB loopback forwarding is present: host TCP `18776` to device TCP `18776`.
- Server control record was readable before Android testing. Its SHA-256 is `760f223fb7479fc1b538696f72657a757f96a46668ce7c1da146cc060080eb95`; it reports zero Android requests and zero stored archives.
- The fixture server is loopback-only. Its reviewed handler accepts the Android namespaced WebDAV directory and path-style S3 bucket, records method/status/digest metadata without headers or bodies, and expects the documented upload/list/download/delete flow.
- No APK was installed, no app was started, no app data/settings were changed, and no screenshots were taken.

Waiting for parent agent: final APK path and SHA-256, source-freeze commit, server-ready confirmation, and parent confirmation before each GUI remote-delete step.
