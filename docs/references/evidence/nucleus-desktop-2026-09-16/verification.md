# Nucleus desktop packaging — macOS GUI verification

Date: 2026-09-16 (Asia/Shanghai).

## Scope and isolation

The tested bundle is the final obfuscated macOS release at
`desktopApp/build/compose/binaries/main-release/app/RikkaHub.app`.

The final observed process was PID 56578. It was launched with:

```text
JAVA_TOOL_OPTIONS=-Duser.home=/private/tmp/rikkahub-nucleus-gui/profile7 \
  -Drikkahub.dataDir=/private/tmp/rikkahub-nucleus-gui/data7
```

All GUI work used that private profile and data directory. No other RikkaHub
process or user data was operated on. At the user's request, this instance and
its private directories remain available for manual checking; no later restart,
smoke run, cleanup, or other test action was performed.

## Results actually observed

| Case | Expected result | Actual result and retained evidence |
| --- | --- | --- |
| Final release startup, icon, and title | The packaged app has one usable main window with readable title text. | **Passed.** The final title-color rebuild started successfully. The sole product main window was identified by PID and CGWindow bounds `X=356, Y=132, Width=800, Height=600`; its title reads `RikkaHub` with sufficient contrast. [final-startup.png](final-startup.png) |
| Navigation | Drawer, history, and global settings open without an event-loop failure. | **Passed.** The isolated release instance opened the drawer, empty history, and global settings. No Tao or JVM crash was observed. |
| Clipboard input | Pasting the fixed text appears in the chat input. | **Passed.** The native paste action displayed `NUCLEUS26 clipboard 中文`. The test clipboard was written only for this action and was not read. [input-paste.png](input-paste.png) |
| Direct Chinese keystroke automation | The fixed `NUCLEUS26 中文输入` text appears through a direct automated keystroke. | **Not passed / not a product failure.** The available macOS automation input source converted the Chinese characters to `a a a a`; this is not evidence of an app rendering failure. No claim is made for direct Chinese IME input. |
| FileKit Chatbox import | The native picker opens, selects the offline fixture, and imports it. | **Passed.** FileKit opened the app-owned native `打开` panel. The private `fixture/nucleus-chatbox.json` was selected, then the final release displayed `恢复成功` and its restart-required dialog. [native-picker-fixture-selected.png](native-picker-fixture-selected.png), [import-final-result.png](import-final-result.png) |
| Imported offline HTML, Mermaid, and formula | Imported fixture content can be opened, rendered, returned from, and reopened. | **Not run.** The private fixture was prepared with HTML, Mermaid, and `$$e^{i\\pi}+1=0$$`, but the user requested that checking stop immediately after the import result. |
| Persistence after cold restart | Imported data remains after restarting the same private profile. | **Not run.** Import requested an app restart, which was intentionally left to the user's manual check. |
| Image export | A desktop image-export path completes. | **Not run.** |
| Windows GUI | Windows package interaction is exercised. | **Not covered.** No Windows VM/runner is available on this macOS host. |

## Window-targeting evidence

The macOS helper was corrected to activate the exact PID before the initial
lookup, query `.optionAll` windows owned by that PID, and choose the product
window by bounds. For native-panel interaction and capture it was then changed
to preserve focus rather than raising the main window. The final picker capture
contains only the app-owned native panel and the private fixture name; unrelated
full-screen captures were not retained in repository evidence.

## Stop point

Testing stopped on the user's instruction after the successful import result.
No WebView lifecycle result, restart/persistence result, image export, smoke
result, or Windows GUI result is represented as passed.
