# BuildAI

BuildAI is an early-alpha Android app for creating personal tools from a conversation. It uses Kotlin/Compose, local GGUF inference through pinned llama.cpp, and optional explicitly enabled cloud providers. New Create projects produce versioned native app definitions with local records. Existing HTML projects retain their legacy runner and storage origins.

This is an old version of the app, the public repository is updated once monthly.

The [requirements](docs/SYSTEM_REQUIREMENTS.md), [implementation handoff](docs/IMPLEMENTATION_HANDOFF.md) and [architecture](docs/ARCHITECTURE.md) describe the intended product, implemented foundation and remaining gates. **Autonomous local app creation has not passed its quality gate in this repo.** Installing a model or passing a short-reply check does not establish builder reliability on that phone.

## Current repair candidate

The debug app is **BuildAI Dev**, package `com.buildai.app.debug`, version `0.3.0-dev.20261019`. It can coexist with the release package. The [19 October repair record](docs/evaluation/reliability-20261019/REPORT.md) distinguishes executed checks from compiled Android scenarios and outstanding Qwen3 4B phone verification.

This pass addresses the reported meal-logging selection failure, typed planning output, repeated invalid repairs, retry/model handling, shared-action edit scope, incomplete change reviews, stale selected records, numeric evaluation, malformed collections, provider drafts, chat plan tags and export filenames. It does not establish complete model quality, the approved visual redesign, scheduling or portable native bundles.

## Use the app

- **Chat** keeps ordinary conversations separate from app creation. Drafts stay editable during replies; speech inserts text for review. Sending can queue another input. Stop cancels explicitly.
- **Create** starts a saved project. If AI needs setup, the idea remains in its draft. Planning tracks answers, questions, assumptions and optional suggestions, then offers an editable plan. No special phrase or fixed turn count is required.
- Choose appearance, then **Build**. Native creation selects capabilities, configures each feature and executes checks in isolated storage. Checked features form resumable checkpoints. Navigation detaches the screen; it does not cancel the job. **Activity** shows job status.
- **Apps** opens completed tools in a full-screen runner. App records and ordinary actions work without a model loaded. Launcher shortcuts and launch-card widgets are available where the Android launcher supports pinning.
- **Edit app** prepares a scoped change for preview and review. Applying checks the base revision. Earlier behavior can be restored while retaining current records. Shared actions must be cloned when an edit would affect unselected controls.

Local inference is the default. Settings manages model imports/downloads, selection and a short chat-compatibility check. Cloud AI requires a saved key and explicit selection. Keys stay in protected storage and are resolved at execution time. No new external UI service is required by the [generative UI investigation](docs/ARCHITECTURE.md#generative-and-intelligent-ui-investigation--19-october-2026).

Manual tools and ready-made starters remain an optional **Settings → Advanced** choice. They are separate from AI-generated results. A failed AI build preserves the previous app; it must not silently substitute a starter.

## Run locally

Use JDK 17, Android SDK 35, NDK 28.2.13676358, CMake 3.31.6 and the checked-in Gradle wrapper. Minimum Android version: Android 12/API 31. Clone with submodules to retain the pinned inference source.

```sh
git submodule update --init --recursive
bash scripts/gradle-bounded.sh --offline :app:assembleDebug
```

`--offline` uses already installed dependencies; omit it for an intentional initial dependency download. Install `app/build/outputs/apk/debug/app-debug.apk`. Settings → About shows the repair version above. Phone RAM guidance in the model catalog is an estimate, not a measured performance promise.

## Verify changes

```sh
bash scripts/gradle-bounded.sh --offline :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
bash scripts/emulator-checks.sh com.buildai.app.NativeBuilderTest,com.buildai.app.NativeRelationshipTest,com.buildai.app.NavigationWidgetRegressionTest
```

Build first, then run one emulator. The scripts share a lock and enforce resource bounds. The emulator script requires its configured AVD and sufficient host memory; do not bypass its reserve to force a run. The [QA guide](docs/QA.md) and dated evaluation records retain historical evidence. Controlled replies verify orchestration; they do not prove that an actual model understands a request. Live Gemini and physical A35 evaluation remain separate checks.

## Current limits

Native widgets currently launch projects; custom summaries/actions, scheduled reminders/routines, `.buildai` native app/data transfer, a screen-connection editor, long unique-history budgeting and the complete visual redesign remain unfinished. Appearance still uses example content before a native app exists. Legacy HTML styling/export differs from native projects. No standalone APK export is implemented.

Device backup/transfer is disabled because protected keys cannot be restored reliably with encrypted preferences. Conversation export now includes the current native plan, but it is not a native app/record backup. Preserve important data until portable bundle support is implemented.

## Source and release

`builder` owns requirements, capability composition and scoped edits; `runtime` owns AppSpec, records, actions, navigation and revisions; `data/jobs` owns screen-independent work; `data/local` and `app/src/main/cpp` own isolated GGUF inference. Compose screens and widgets consume these contracts.

[Release instructions](docs/RELEASING.md) describe signing. CI does not publish or sign releases automatically. BuildAI uses Apache 2.0; [NOTICE](NOTICE) retains third-party attribution. Downloadable model licenses are separate from the app license.
