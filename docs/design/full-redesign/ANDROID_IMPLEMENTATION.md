# Mobile redesign implementation

Implemented in the Android source on 4 October 2026, following the adversarial audit, feature inventory, competitor research, and the two concept images. This document describes the actual mobile app; the HTML prototype remains an independent design reference.

## Navigation and journeys

- **Apps** is the starting screen. Ready app cards open the standalone native or HTML runner; **Edit app** opens that app's workspace. Drafts stay below ready apps. Search, deletion confirmation, and overflow actions remain available.
- **Chats** is a separate root for general conversations, with search, rename, deletion, and **New chat**. Switching roots does not change the mode of an existing conversation.
- **Create an app** opens a focused idea entry screen. Example prompts fill the editable composer. Manual composition and all six offline starters are direct alternatives to AI setup.
- Every app project has one workspace with **Chat / Plan / Preview**. The selected tab is saved with the project. A completed planning reply does not unexpectedly move the user to another screen.
- Before building, Chat is the planning conversation. After a native app is ready, Chat presents a scoped change request with the original conversation still accessible. Ready native apps' Plan tab shows the current definition and data fields.
- Preview shows the real native runtime or the existing HTML output. Current-app previews explicitly use saved data. Proposed native edits use a separate in-memory database, copied records, and a clear test-copy label. Applying a proposal is an explicit action in Chat.
- Apps open separately from the builder. Home-screen shortcuts and widgets use a runner intent, including native apps, and fall back to the workspace when there is no ready app.

## Themes and accessibility

Settings → Theme offers **Sea blue**, **Forest green**, and **Night gray**. Selection applies immediately, persists across recreation, and updates native runners, builder screens, dialogs, and status/navigation-bar icon appearance. All shared text colors and primary button labels meet the 4.5:1 normal-text contrast threshold on their intended surfaces.

Shared controls use minimum 48 dp targets. Workspace tabs accommodate larger text, the composer has a separate stop control, lists scroll, and native navigation/action groups wrap instead of disappearing off-screen. Both light themes have white surfaces and distinct blue/green accents; Night gray uses dark neutral surfaces and a light gray accent.

HTML apps preserve their authored CSS and storage origins. Their builder and standalone toolbar follow the selected theme; arbitrary generated HTML is not recolored behind the user's back. The legacy app appearance editor remains separate from the builder theme.

## Settings and trust

Settings is a small directory: **AI & privacy**, **Theme**, and **About**. Local and cloud setup have separate pages. API keys have adjacent Save and show/hide controls; leaving an edited key presents Save/Discard. Model selection saves explicitly, connection selection is explicit, and installing/removing a model does not silently switch the provider.

The existing five-model download catalogue, import, source links, progress, cancellation, removal, and short-reply checks remain reachable. Cloud planning model choice and generation fallback explanation remain available. A short-reply check is described as a reply check, not proof that a model can build apps.

Activity is a secondary destination available from Apps, Chats, and the workspace. The icon badges jobs needing attention. Running and review work is shown first; token/character counts are behind Technical details. Stop and supported checkpoint retries remain available. Work continues when the user leaves its screen.

## Drafts, appearance, edits, and records

Incomplete legacy plans persist separately from the last valid build plan. Native plan drafts retain their existing validation/approval contract. User-owned project/chat names survive subsequent replies. Batch suggestion choices submit one combined planning request.

Appearance is optional. It has layout, palette, app icon, tab names, and now reachable tab-icon controls. Changes are drafts until Save; reset and leaving unsaved changes have explicit confirmation. The style preview is labeled as example content, and saved appearance is described as applying to the next build.

Scoped native edits keep their current revision until Apply. Requests remain editable during preparation, but the next request is described as a saved draft instead of pretending it is queued. Proposals can be previewed, applied, or discarded. Version history lists all stored revisions and requires a separate restore confirmation; current records are retained by the existing restore implementation.

Native records have a deletion confirmation, separate empty and no-match states, date-picker assistance, searchable reference selection, and wrapping record actions. Existing arithmetic, related data, filtering/sorting, timers, quizzes, transactions, stale-revision protection, and storage are retained.

## Capability boundaries

This implements the currently supported native, guided, chat, and HTML builders. Future calendar/PDF/file/reminder/phone workflows and `.buildai` bundles remain outside the current runtime. Arbitrary native per-app appearance persistence is also outside AppSpec; the three themes apply through the shared native renderer. Widgets without a supported summary show a launch affordance instead of an invented statistic.

## Validation

The installable debug APK is at `app/build/outputs/apk/debug/app-debug.apk` (BuildAI Dev, package `com.buildai.app.debug`, 95,455,403 bytes). SHA-256: `37fefb57b9cdfd9b8de0d040a0ee036c45424edd940dce4f53c4aeb57a71fe64`.

| Check | Result |
|---|---|
| Debug app and instrumentation APK compilation | Passed |
| JVM unit suite | 77 passed; zero failures, errors, or skips |
| Android lint | Zero errors; 56 warnings remain |
| API 35 device cases | 32 distinct cases passed across the final app runs |
| Three themes | Selection, recreation, Apps, Chat, Plan, Preview, native runner, and system-bar appearance passed |
| Large text | Workspace tabs, Preview, Settings, and theme choices reachable at 2× font scale |
| Proposed edit | A test-copy record was saved; live records and current revision stayed unchanged; discard succeeded |
| Manual and offline creation | Direct entry, actual build, runner opening, and return to workspace passed |

Other device coverage includes background and concurrent builds, chat cancellation/retry, failed rebuilds retaining the existing app, HTML interactions/sharing/browser opening/cold reopening, native plan editing, arithmetic, records and relations, timers/quizzes, revision recovery, and stale-edit protection. Appearance draft/reset behavior, incomplete plan persistence, selected tabs, unsent chat drafts, explicit provider selection, unsaved-key discard, and record deletion confirmation are covered separately.

The broad final-APK run passed 28 of 32 cases. Its four failures were ambiguous/virtualized test selectors and a helper waiting for MainActivity while a runner dialog was open. Only test code changed afterward; all nine affected-class cases passed in the corrected run. The combined result is 32 distinct passing cases, not 41 independent cases. Raw logs and per-case outcomes are retained in [android-validation.json](android-validation.json) and `android-validation/`.

[View 20 actual emulator captures](SCREENSHOTS.md). These are native Android screenshots, rather than prototype renders. Visual review included the light/dark library, theme selection, workspace, saved-data preview, proposed test copy, and large-text preview. Window backgrounds now use the saved theme before Compose draws, avoiding a light startup surface in Night gray.

Validation commands:

```sh
bash scripts/gradle-bounded.sh --offline :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug

# Broad run on the final application APK:
BUILDAI_LOW_STORAGE_INSTALL=true BUILDAI_TEST_OUTPUT=/tmp/buildai-mobile-verified-device BUILDAI_TEST_TIMEOUT_SECONDS=900 BUILDAI_EMULATOR_TIMEOUT_SECONDS=1200 bash scripts/emulator-checks.sh com.buildai.app.WorkflowTest,com.buildai.app.AppEditingTest,com.buildai.app.MobileRedesignTest,com.buildai.app.WorkspaceUiTest,com.buildai.app.NativePlanEditingTest,com.buildai.app.NativeRuntimeTest,com.buildai.app.GuidedRuntimeTest,com.buildai.app.ProjectRecoveryTest,com.buildai.app.NativeRelationshipTest

# Corrected test selectors, followed by their focused rerun:
bash scripts/gradle-bounded.sh --offline :app:assembleDebugAndroidTest
BUILDAI_LOW_STORAGE_INSTALL=true BUILDAI_TEST_OUTPUT=/tmp/buildai-mobile-selector-verified BUILDAI_TEST_TIMEOUT_SECONDS=600 BUILDAI_EMULATOR_TIMEOUT_SECONDS=900 bash scripts/emulator-checks.sh com.buildai.app.AppEditingTest,com.buildai.app.MobileRedesignTest
```

The host ran bounded Gradle and emulator checks sequentially, without model downloads or AVD wipes. Disposable fixtures were cleaned up and prior theme/provider/key preferences restored. The harness temporarily disables animations and lowers the installation storage threshold for this nearly full development AVD, then restores both; it also resets the display overrides and shuts down the emulator. The pre-existing Git index is unchanged. No physical-phone or live production-model acceptance is claimed.
