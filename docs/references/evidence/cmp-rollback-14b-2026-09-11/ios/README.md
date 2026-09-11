# CMP rollback 14B — iOS GUI verification

- Device: iPhone 17 Pro Max (`03C090DA-107B-4F9F-BCCD-8D5265D32820`), kept Booted.
- Final package: XcodeBuildMCP `build_sim` followed by `build_run_sim`, scheme `iosApp`, completed on 2026-09-11. Installed executable SHA-256: `41167edb3acd5d6f0e8293f21e4794fa3fc4180a33fad3b182355ed5c45ce06f`.
- The final package was built and installed from the final source; no old package result was counted.
- Fixture data used deliberate legacy iOS FileKit container URIs. Product first-open migration completed before GUI actions; every checkpoint reports `legacyContainerReferences: 0` for the remaining fixture messages. The original settings, message IDs and non-fixture database contents were preserved. Final synthetic identifiers and relative paths are in [fixture-manifest.json](fixture-manifest.json).

## Retried visible-fixture GUI coverage

Earlier retries exposed fixture-only serialization problems: `MessageRole.USER` had been written with the wrong case, and nested `ToolApprovalState` had been written as a string. The retry used data exported by the Kotlin serializer. The initial tiny-image screenshot did not prove visible rendering and was discarded; it is not counted as a pass.

The final retry used `CMP14B CHECKER Source` and `CMP14B CHECKER Retained`, sharing a recognizable image with purple, white, cyan and orange quadrants, plus Source's exclusive upload image.

1. In ChatDrawer, long-pressed Source, selected Delete, and confirmed deletion. The `cold-fork` checkpoint records Source DB count `0`, exclusive file absent, retained DB count `1`, and the shared file still present with SHA-256 `2bd5873f14344de32c78be0de396f9a5f2aa9b57fe7d2648fde12a4f6aeb7ef7`.
2. Stopped and launched the app, opened Retained, and observed its direct image message. [03-retained-visible-checker-cold-start.jpg](03-retained-visible-checker-cold-start.jpg) visibly shows the colored shared image in `CMP14B CHECKER Retained`; [cold-fork.json](cold-fork.json) is the paired disk/DB assertion.
3. Long-pressed Retained in ChatDrawer and deleted it. [fork-deleted.json](fork-deleted.json) records Source/Fork DB counts `0` and both exclusive and shared files absent.

## Assistant asset GUI coverage

[04-assistant-assets-before.jpg](04-assistant-assets-before.jpg) shows the migrated legacy avatar for `CMP14B CHECKER Asset A` and the paired `CMP14B CHECKER Asset B` in the Assistants page. Deleted A through its row Actions menu, Delete, Confirm. [asset-a-deleted.json](asset-a-deleted.json) records A absent, its exclusive avatar absent, B still present, and B's background retained with SHA-256 `d7077c375e7948a8d71402029e6e9026fc88f35c5baf0fe85df5e95657859c9d`.

Selected Asset B in the drawer assistant picker, opened its `CMP14B Background Preview` conversation, and observed the background image applied across the chat page. [05-asset-b-background-visible.jpg](05-asset-b-background-visible.jpg) and [asset-b-visible.json](asset-b-visible.json) capture that state. Finally deleted B through Assistants Actions, Delete, Confirm. [all-deleted.json](all-deleted.json) records both test assistants and all four test files absent, plus all three test conversations absent.

The parent independently rechecked the final database and file state, stopped the app, and restored the original preferences byte-for-byte. Non-fixture conversations, messages and favorites had identical row counts and content hashes before and after the final fixture run. Earlier fixture paths and identifiers were also absent. See [cleanup.json](cleanup.json).

Private backups and fixture helpers were removed. No preference bytes, database copies, real settings, or production source changes are stored in this evidence directory. The simulator remains Booted with the tested app stopped.
