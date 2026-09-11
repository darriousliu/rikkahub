# CMP rollback 15 — macOS desktop GUI evidence

Date: 2026-09-11. The final desktop package was exercised through a dedicated
JVM process with `-Duser.home=/private/tmp/cmp-rollback-15/desktop/profile`.
All screenshots capture only the verified RikkaHub window owned by that test
process. No default user database or settings were read.

## Final fixture identity

- Chatbox JSON SHA-256: `9fd3a832d4e02e8a3b558cc15c4dc317f57963fe483dcfcbb779f7f2d1b922c7`
- Cherry ZIP SHA-256: `f7459196a28459735f8206374f4286668fdb06c59dfb6c0868d3185888d9b04e`
- Native ZIP SHA-256: `396eea544fe9949c544294d3967d3401207cb617fbf514caea660ff799aeaf23`
- Imported conversation ID: `8ffa6157-f1d7-31bc-9edd-b0ed071f4600`

## Verified behaviour

- `chatbox-visible-chinese.png` shows the imported `CMP15 desktop Imported`
  conversation, its fixed Chinese user message, and fixed offline response.
- `chatbox-duplicate-single.png` shows exactly one visible entry with that title
  after a second GUI import of the same stable ID.
- `cherry-provider-visible.png` shows `CMP15 desktop provider` directly in the
  provider page; no provider-count assertion was used.
- `native-import-result.png` shows the GUI restore-success state. The exact
  registered marker path `upload/cmp15-desktop-native.txt` was absent before
  import and present afterward.
- `gui-export-complete.png` records the local export page after saving the
  archive to the private test directory. The exported archive SHA-256 was
  `0a7ce3b5adfbabce13a80e54c2443c9b656a3663ffa972a4b0a6d023c4bc6e3b`.
  The private `inspect-export.py` helper passed CRC, `settings.json`, SQLite
  database, WAL/SHM layout, marker, and a one-row query for this conversation
  with the fixed Chinese body and response.
- Save cancellation left the existing private export ZIP hash unchanged; open
  cancellation returned to the same sole RikkaHub main window with no restore
  or restart action.

No settings contents, database contents, API credentials, or exported ZIP are
included here. The test process, private profile, fixture copies, logs,
temporary tools, private ZIP, and exact marker are removed after this record
is written.
