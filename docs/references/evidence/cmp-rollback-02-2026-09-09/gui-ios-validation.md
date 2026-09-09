# CMP rollback 02 — iOS GUI validation

Date: 2026-09-09

## Build and launch source

- Built application supplied by the parent agent:
  `/Users/liuzhenhui/Library/Developer/Xcode/DerivedData/iosApp-fpbfsgalslastofwurqoaqnyqkdb/Build/Products/Debug-iphonesimulator/RikkaHub.app`.
- Bundle ID verified and launched: `me.rerere.rikkahub.ios`.
- Simulator: `469B364C-382C-4068-A012-3CD026296BAE`.
- UI operations used XcodeBuildMCP semantic runtime snapshots, `touch`, `long_press`, and `type_text`; application restarts used `stop_app_sim` then `launch_app_sim`.
- The first launch displayed the simulator Apple Account verification alert. It was dismissed with “以后”; its screenshot was deleted and is not retained here.

## Test data and cleanup

- GUI-created folder began as `CMP-Folder02-iOS-Target`.
- Because the fresh default assistant has no usable model, one local-only empty conversation was seeded while the app was stopped: ID `02020202-0909-4000-8000-000000000002`, title `CMP-Folder02-iOS-Session`, and no messages. The schema was not changed.
- The folder and conversation were both removed after validation. Final direct counts were `folders=0` and `conversations=0` for the `CMP-Folder02-iOS-` prefix/ID.

## GUI results

1. Opened **Messages** and used **新建** to create the dedicated folder. The drawer runtime snapshot then exposed `CMP-Folder02-iOS-Target`.
2. Long-pressed that folder and chose **重命名**. The GUI save callback changed the folder name, proving the rename path was invoked.
3. XcodeBuildMCP did not expose the iOS text-selection menu for this Compose field. Its `replaceExisting` call retained unselected suffixes, so its submitted GUI value could not be made exactly `CMP-Folder02-iOS-Renamed` without corrupting the active composition. I cancelled the malformed retry rather than submit it. I restored this **dedicated** folder's name to the requested exact value while the app was stopped, then restarted the app. The post-restart drawer snapshot showed exactly `CMP-Folder02-iOS-Renamed`. This is a UI-automation limitation for the exact-value retry, not evidence of a product rename defect.
4. Long-pressed `CMP-Folder02-iOS-Session`, chose **移动到文件夹**, and selected `CMP-Folder02-iOS-Renamed`. In the default **聊天** view the session disappeared; selecting the folder showed the session, confirming the GUI move persisted.
5. Restarted the app before deletion. The drawer showed both exact folder name and the contained dedicated session.
6. Long-pressed the folder, chose **删除**, and accepted the confirmation. The drawer then contained `CMP-Folder02-iOS-Session` under the default **聊天** view plus **新建**, with no test folder present: the session was retained and returned to unfiled.
7. Restarted again. The same unfiled session and no test folder remained. All three `launch_app_sim` calls succeeded; no DI startup failure appeared in the UI workflow.

## Key screen captures

The steps above summarize the semantic UI observations; separate snapshot dumps are not retained. Two key screenshots
were inspected for sensitive information and copied unchanged from the tool's temporary area into this evidence directory:

- [Folder-with-session view](gui-ios-folder-session.jpg).
- [Post-delete unfiled-session view](gui-ios-deleted-unfiled.jpg).

Neither capture contains Apple Account information. The account-alert capture is not retained.

## Coverage status

- Creation, GUI move, GUI delete, session retention/unfiled fallback, and restart persistence: **passed**.
- Exact target name after restart: **observed as `CMP-Folder02-iOS-Renamed`**, after repairing the dedicated test record because XcodeBuildMCP did not expose the requested system Select All action for the Compose text field.
- Exact-value *GUI retry* after that repair: **not covered**. The original GUI rename action was covered, but its field-replacement behavior is limited by the test tool; do not represent this narrow sub-check as fully passed.
- The local seed was an empty conversation. Message bodies/branches and unrelated control data were not exercised by this iOS GUI run; those behaviors are covered by the real-Room JVM tests, with unrelated control data additionally checked in the Android GUI run.
