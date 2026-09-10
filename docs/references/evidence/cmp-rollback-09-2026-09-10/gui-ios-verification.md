# CMP rollback 09 — iOS GUI verification

Date: 2026-09-10

Simulator: iPhone 17 Pro Max (`03C090DA-107B-4F9F-BCCD-8D5265D32820`)

Bundle: `me.rerere.rikkahub.ios`

Final artifact: `RikkaHub.debug.dylib` SHA-256 `5fc269d8f404469db6b4aa50e19df226a3a9086f93b8da4425b397efca17d30e`

GUI agent model: `gpt-5.6-terra`, reasoning: `high`

## Scope and setup

Validated Assistant Prompt Page on the final iOS simulator artifact. The parent agent prepared fixtures for test assistant `9070a2d6-c80c-46bc-b5fd-386f53412ea8` and confirmed all other settings and assistants remained unchanged. The currently selected test assistant was deliberately retained because its original `selectedAssistantId` was not recorded before the earlier navigation; no assistant was deleted and no selection was guessed.

The test assistant's original template was recorded as `{{ message }}`.

## A fixture and reset

| Step | Expected | Actual |
| --- | --- | --- |
| Fixture A preview | User and assistant preview each render role, original message, local date, and time including seconds; no raw template tokens. | Passed. Both preview rows rendered `CMP09-A` with `user` / `assistant`, original Chinese messages, local date `2026年9月10日`, and time `15:38:26`. |
| Leave and return | A template remains visible. | Passed. The message-template field remained on A after returning to Prompt. |
| Real Reset button | Field immediately becomes `{{ message }}` and both previews immediately become their original messages; A disappears. | Passed. Field and both rows refreshed immediately. |
| Same-data cold launch | The reset template remains `{{ message }}`. | Passed after `stop_app_sim` then `launch_app_sim` and navigating back to Prompt. |

## Invalid-template recovery

| Step | Expected | Actual |
| --- | --- | --- |
| Invalid fixture `{% unsupported_tag %}` | Prompt preview reports a template error. | Passed. The UI showed `Can't find tag unsupported_tag with content at 9070a2d6-c80c-46bc-b5fd-386f53412ea8:1:3`; the field also displayed its missing-`{{ message }}` validation. |
| Real Reset button | Error clears, field restores `{{ message }}`, and the two original preview messages return. | Passed immediately. Both original Chinese preview rows were visible again. |

## Evidence

Raw screenshots are retained as JPEG, without changing their image format.

| File | SHA-256 | Evidence |
| --- | --- | --- |
| `gui-ios-a-preview.jpg` | `116b6533a794ec7bc0bd7f345c323989f36acbd8266e470edb0ec42f30fda493` | Both A previews. |
| `gui-ios-a-reset.jpg` | `8230b94889d813d5a2a3247197e3d44ba1d657eb101ef58d9afa48d071b9fa75` | Reset field and original previews. |
| `gui-ios-a-cold-start.jpg` | `85e5951132abbc3f9c0caede381303fd79e0f519e2145b9924b84f2d80609755` | Reset field after cold launch. |
| `gui-ios-invalid-error.jpg` | `927f405eb1193c7dcdc4506859338311f66f7db6a21c3da61f5506b9be98af0a` | Unsupported-tag error. |
| `gui-ios-invalid-reset.jpg` | `fb70ebae2b3edae7198df3aaa47daf2ef01ef49af53e6c7a3c5bc1cf4c3b935f` | Invalid fixture reset and preview recovery. |

## Limits and cleanup

No Android or desktop verification was performed. No model request was issued and no credentials were read. `type_text` remains unverified for template editing: its one allowed attempt appended instead of replacing the original value; it was not retried. The discrepancy in the UI snapshot's separator representation is recorded only as a snapshot recognition/display difference; persistent fixture data was verified separately by the parent agent and no runtime conversion is inferred.

The simulator app was stopped after each handoff. The agent removed its 11 known temporary screenshot source files and confirmed they no longer exist; the five original JPEGs listed above are retained in this evidence directory. The parent verified the restored template and unchanged unrelated preferences in `ios-fixture-cleanup.json`, then removed the private preference recovery snapshots.
