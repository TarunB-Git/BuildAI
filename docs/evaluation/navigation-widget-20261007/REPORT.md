# Navigation and widget fixes — 7 October 2026

This pass implements the user's selected priority: fix navigation between generated-app screens and restore widget access. **91 JVM tests and 12 Android tests passed**, including eight new JVM regressions and five new Android regressions. The debug app and test APK built successfully; lint reported zero errors and 55 existing warnings.

The eight source-supported findings addressed below are from the [original audit](/home/tar_guest/Documents/BuildAI/docs/evaluation/adversarial-20261007/REPORT.md). This is a focused repair pass, not a claim that all 33 original findings have been fixed. Existing workspace changes and the Git index were preserved; no commit was created.

## Changes and evidence

| Audit finding | Implemented behavior | Verification |
|---|---|---|
| S01 — missing Apps launcher actions | Apps now exposes Add home-screen widget and Add home-screen shortcut. Apps and workspace use the same request helpers and handle launcher request failures. | Android test asserts both actions are displayed and clickable for a native app; screenshot inspected. Actual launcher pin confirmation was not exercised. |
| S02 — tabs bypass connected actions | Global navigation excludes screens requiring action bindings, conditions, row context, or a contextual predecessor. Those screens open through their explicit actions. Repository-level navigation also rejects attempts to enter them directly. | JVM cases cover bound, guarded, row, and chained destinations. Android cases verify rejection without state changes and successful record-context navigation. |
| S03 — positional tab destinations | Native tabs store screen IDs. Labels and icons follow their screens after reordering. Older tabs migrate using their original screen positions before detail screens are excluded. | JVM serialization/migration tests and Android screen-reorder test passed; screenshot inspected. |
| S04 — Back jumps to start | Navigation persists a bounded history of prior screens, selected-record snapshots, and screen input values. Back restores the previous state, skips removed screens, and gives older runtimes a route home. It also works in embedded previews. | JVM cases cover serialization, input/record restoration, bounded size, removed screens, and older runtimes. Android case covers recreation followed by Back to the detail screen and then the library. |
| S05 — native Appearance uses legacy controls | Appearance loads the published AppSpec, including for manual native apps with no generated brief. Native controls bind tabs to actual top-level screens. Native appearance updates are observed by the runner. | Build and lint verified these paths. The manual-native Appearance page and changing appearance while a runner is open were not separately exercised through the UI. |
| S06 — additional detail screens remove tabs | The five-tab limit counts top-level destinations, not every screen. More than five main screens retain screen buttons, and Appearance explains why bottom tabs are unavailable. | JVM case verifies a six-screen app can retain two main destinations. The over-five-main-screens Appearance message was checked in source, not through Android UI. |
| S09 — initial render includes deleted rows | Initial runner records use the same active-record query as the observed flow. | Source correction compiled; existing record-deletion workflows passed. No dedicated first-frame timing assertion was made. |
| S17 — widget metadata stays stale | Workspace rename and saved appearance changes refresh bound widgets. | Android widget-host test verifies refreshed title and icon. Workspace rename's refresh call was checked in source; the rename dialog itself was not exercised in that test. |

Native widgets added without an explicit project binding can now choose a ready native app, and retain that binding when another app becomes more recent. This addresses part of I01. The Android test allocates and binds a real AppWidget through a test host, displays its RemoteViews, changes project recency, and checks its title and icon after refresh. This is Android widget-host integration, not confirmation of a widget pinned on a physical phone's launcher.

Global destination inference is deliberately conservative: a bound, guarded, row-dependent, or contextual step remains action-only. This avoids bypassing the app's workflow. An explicit screen-role and connection editor is still a separate improvement. History is capped at 20 entries and pruned to respect the existing runtime-state size limit; older entries can be discarded for large inputs.

## Implementation

- [Navigation policy, migration, and history](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/runtime/NativeNavigation.kt:18)
- [Repository navigation guard](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/runtime/NativeAppRepository.kt:63)
- [Runner destinations and appearance observation](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/runtime/NativeRunnerActivity.kt:119)
- [Apps launcher actions](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/ui/screens/AppsScreen.kt:69)
- [Stable native widget binding](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/widgets/ProjectWidgetProvider.kt:33)
- [Published native Appearance definition](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/navigation/AppNavigation.kt:153)
- [JVM regressions](/home/tar_guest/Documents/BuildAI/app/src/test/java/com/buildai/app/runtime/NativeNavigationTest.kt)
- [Android regressions](/home/tar_guest/Documents/BuildAI/app/src/androidTest/java/com/buildai/app/NavigationWidgetRegressionTest.kt)

The [review patch](/home/tar_guest/Documents/BuildAI/docs/evaluation/navigation-widget-20261007/changes.patch) contains only this pass's source/test changes, compared with file contents captured before editing. It excludes the user's pre-existing changes. Final source hashes and the debug APK SHA-256 are retained in [results](/home/tar_guest/Documents/BuildAI/docs/evaluation/navigation-widget-20261007/results.json) and [source hashes](/home/tar_guest/Documents/BuildAI/docs/evaluation/navigation-widget-20261007/source-hashes.json).

## Verification

```sh
bash scripts/gradle-bounded.sh --offline :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
BUILDAI_TEST_OUTPUT=/tmp/buildai-navigation-widget-device BUILDAI_TEST_TIMEOUT_SECONDS=600 BUILDAI_EMULATOR_TIMEOUT_SECONDS=900 bash scripts/emulator-checks.sh com.buildai.app.NavigationWidgetRegressionTest,com.buildai.app.NativeRuntimeTest,com.buildai.app.NativeRelationshipTest
```

The final build completed in 1 minute 49 seconds. The API 35 emulator booted in 60 seconds with two guest CPUs and 2 GB RAM on two low-priority host cores. All 12 instrumented tests passed in 97.518 seconds, with no skips, and the emulator was shut down. Existing notes, budget, routine, revision/undo, relationship, conditional-action, and deletion workflows passed alongside the new checks. No build ran concurrently with the emulator.

Retained evidence: [build log](/home/tar_guest/Documents/BuildAI/docs/evaluation/navigation-widget-20261007/build.log), [device run](/home/tar_guest/Documents/BuildAI/docs/evaluation/navigation-widget-20261007/device-run.log), [Android results](/home/tar_guest/Documents/BuildAI/docs/evaluation/navigation-widget-20261007/device-tests.log). JVM XML results are in the adjacent `jvm-results` directory.

Five screenshots from this run were visually inspected or retained; older cached screenshots collected by the existing harness were excluded from this report:

- [Restored Apps menu](/home/tar_guest/Documents/BuildAI/docs/evaluation/navigation-widget-20261007/screenshots/apps-widget-menu.png)
- [Native widget in the Android test host](/home/tar_guest/Documents/BuildAI/docs/evaluation/navigation-widget-20261007/screenshots/native-widget.png)
- [Back restores the unsent detail edit](/home/tar_guest/Documents/BuildAI/docs/evaluation/navigation-widget-20261007/screenshots/navigation-detail-back.png)
- [Screen-ID-bound tabs](/home/tar_guest/Documents/BuildAI/docs/evaluation/navigation-widget-20261007/screenshots/navigation-stable-tabs.png)
- [Library after Back](/home/tar_guest/Documents/BuildAI/docs/evaluation/navigation-widget-20261007/screenshots/navigation-library.png)

## Remaining work

Native widgets remain launch cards. Custom native summaries, widget actions, and a launcher project chooser are unfinished. Physical-device pin confirmation and launcher-specific behavior remain unverified. The screen-connection editor (I02) is not implemented by this pass.

The selected-record snapshot issue (S08), scoped-edit isolation/review issues (A01–A03), calculator acceptance (A04), number evaluation (A05–A06), malformed collections (A07), export filenames (A08), and provider/settings findings remain open. The original audit's eight reproduced defects were not fixed in this batch.
