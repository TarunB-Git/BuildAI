# BuildAI adversarial audit — 7 October 2026

> Subsequent [19 October reliability repair](../reliability-20261019/REPORT.md) addresses A01–A08 and additional issues. This document remains the original failure evidence; consult the repair report for current source and verification limits.

> This is the original audit snapshot. A subsequent [navigation and widget repair pass](/home/tar_guest/Documents/BuildAI/docs/evaluation/navigation-widget-20261007/REPORT.md) addresses S01–S06, S09, S17, and part of I01, with updated validation evidence. Source links below reflect the audited version.

This audit contains **33 findings: 8 reproduced defects, 17 source-supported issues, and 8 improvement gaps**. Source-supported issues have concrete code paths and proposed device reproduction steps; they have not been reproduced through the Android interface in this session. Improvement gaps are proposals or unfinished capabilities, not claims that an implemented feature failed.

The user's “screen connections” means navigation between generated-app screens. This is the main audit priority, alongside the missing widget option. The inspected application is the current BuildAI workspace, including its existing uncommitted work. Production source and the Git index were not changed. Only this audit directory was added.

## Prompting and intent: three paragraphs

Give an agent an explicit outcome, the user's workflow, constraints, examples, and observable completion criteria. “Improve this app” leaves important decisions implicit; “a saved note must reopen with its text intact, and selecting it must open the matching detail screen” makes intent testable. Google recommends clear instructions, structured context, and examples; OpenAI's coding guidance directs reviewers toward bugs, regressions, risks, and missing tests. For BuildAI, preserve the user's actual requirements and distinguish confirmed decisions from assumptions. [Google prompt design](https://ai.google.dev/gemini-api/docs/prompting-strategies), [OpenAI Codex prompting guide](https://developers.openai.com/cookbook/examples/gpt-5/codex_prompting_guide).

Intent understanding also depends on the information supplied at each step. Anthropic recommends keeping context focused, tools clear, and memory useful across longer tasks. My application of that advice is to give BuildAI's builder and editor the approved workflow, relevant screen and data dependencies, stable identifiers, and real failure evidence. A valid JSON response is one gate; fulfilling the person's workflow is another. Use bounded repair loops, preserve working versions, and turn observed failures into regression scenarios. [Anthropic context engineering](https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents), [Building effective agents](https://www.anthropic.com/engineering/building-effective-agents).

An adversarial reviewer tries to disprove success using explicit criteria. Anthropic's evaluator work shows why superficial testing and forgiving judgments let defects through. My review method therefore asks what happens with different inputs, shared actions, missing record context, interrupted work, malformed output, and navigation through every entry point. Report expected versus actual behavior and distinguish an executed reproduction from an inference. Independent acceptance examples should challenge the generated implementation; they should not simply repeat its own expected answers. [Anthropic application harness and evaluator guidance](https://www.anthropic.com/engineering/harness-design-long-running-apps).

## What was executed

| Check | Outcome |
|---|---|
| Bounded debug build, test APK build, lint | Successful, 1 minute 44 seconds; 88 tasks, 9 executed and 79 up to date |
| Fresh JVM test execution | **83 tests, 0 failures, 0 errors, 0 skips**; 6.196 seconds aggregate test time; 17 seconds Gradle wall time |
| Audit probes against compiled production classes | **8 defects reproduced**; small standalone JVM program, no model/API calls |
| Lint | 0 errors, 55 warnings; warnings are not counted as 55 additional bugs |
| Connected Android devices | None |
| Live Android UI, launcher pinning, instrumentation | Not executed in this session |
| Real-model generation and provider credentials | Not exercised in this session |

Commands:

```sh
bash scripts/gradle-bounded.sh :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
bash scripts/gradle-bounded.sh --offline :app:testDebugUnitTest --rerun
python3 docs/evaluation/adversarial-20261007/run-probes.py
```

The first command reused cached unit-test results. The second explicitly reran the unit-test task. Debug APK SHA-256: `ebee5a864c3aa958a8f916b03b383e723e899938efc15f0570645f9f9b1ab098`.

The development machine had approximately 483 MB free disk space during the build, full swap, and no connected device. Disk space later rose to approximately 609 MB. The emulator was not started under these conditions. A sandbox command briefly failed with “No space left on device”; subsequent reads and checks succeeded. No unrelated files, installed models, or AVD data were removed. Device screenshots and a live UI reproduction remain outstanding.

The [probe source](/home/tar_guest/Documents/BuildAI/docs/evaluation/adversarial-20261007/AuditProbes.java), [runner](/home/tar_guest/Documents/BuildAI/docs/evaluation/adversarial-20261007/run-probes.py), and [output](/home/tar_guest/Documents/BuildAI/docs/evaluation/adversarial-20261007/probes.log) are retained. `REPRODUCED` means the defect is present; these are characterization probes, not passing fix regressions. A08 exercises the exact filename construction with the JVM filesystem; it does not launch Android's share UI.

## Eight reproduced defects

### A01 · P1 · A scoped edit can change an unselected screen's navigation

Two buttons on different screens can share the same action ID. Select only the first button and change that action's destination. `ScopedEditTools.prepare` accepts the change; the second screen's component remains identical but its behavior changes because actions are global. The probe reproduced this with a shared `NAVIGATE` action. The editor promises to retain unrelated controls and behavior. Clone the action for selected consumers or reject changes that affect unselected consumers, and include all affected screens in the review. [Scoped action authorization](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/builder/ScopedEditTools.kt:17).

### A02 · P1 · An empty edit is accepted as a candidate

An empty response passed to the scoped editor returns the original definition without error. The job layer requires a summary and checks, but does not require a real definition change or call the new-feature coverage validator. Consequently a no-op accompanied by a summary and an assertion about existing behavior has a path to review. The probe confirms acceptance at the scoped-edit boundary, not a live model choosing this output. Require a meaningful diff and checks covering the changed behavior. [Edit preparation](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/builder/ScopedEditTools.kt:12), [job verification](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/builder/NativeEditJobs.kt:52).

### A03 · P1 · New stored fields can disappear from the change review

Adding only an optional field changes the schema but `changedIds` returns an empty list because it compares only controls and actions. The probe added a `category` field to notes and reproduced an empty change list. Review should enumerate collection and field additions, including their effect on existing records. [Change enumeration](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/builder/ScopedEditTools.kt:49).

### A04 · P1 · A “live” calculator can pass capability validation with constant output

A definition with numeric inputs A and B and `ADD(NUMBER(2), NUMBER(3))` passes an implementation selection containing INPUT, SUMMARY, INPUT-expression and ADD. With inputs 10 and 20, its output is still 5. The validator explicitly excludes INPUT from required operation checks and never requires the output to depend on the supplied inputs. Model-generated probes can also choose the same convenient constants. Require dependency checks and independently varied acceptance examples; for example 2+3=5, 10+20=30, and decimal inputs. [Implementation validation](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/builder/CompositionSelection.kt:48).

### A05 · P2 · Valid nested arithmetic fails after division

`(1 / 3) * 3` fails with “Number exceeds supported precision.” Division produces a DECIMAL128 intermediate, then the enclosing operation converts it to text and subjects it to the stricter external-input decimal parser. Computed numeric intermediates should remain numeric and use a consistent calculation precision policy. [Expression arithmetic](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/runtime/Expressions.kt:15), [input decimal limit](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/runtime/AppSpecValidator.kt:118).

### A06 · P2 · Equivalent valid decimal numbers compare unequal

Numeric input `2,50` compared with NUMBER literal `2.5` using EQ returns false, even though both are accepted decimal representations. EQ compares display strings, whereas arithmetic parses decimals. This can break numeric conditions and filters. Use type-aware equality with canonical numeric values; retain deliberate string equality for text fields. [Equality evaluation](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/runtime/Expressions.kt:40).

### A07 · P2 · Malformed AppSpec collections are silently discarded

Setting `actions` to an object rather than an array still parses successfully as a definition with no actions when no button requires one. AppSpec's optional collection helpers use `optJSONArray` and treat incorrect types like absent values. This conceals malformed model output instead of sending it through repair. Reject a present key with the wrong type, as the newer brief parser already does. [AppSpec collection readers](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/runtime/AppSpec.kt:103).

### A08 · P1 · A slash in a project title breaks conversation export

The title `Trip / budget` produces the cache path `BuildAI_Trip / budget.txt`, whose unintended parent directory does not exist. The filesystem probe reproduced the write failure. The workspace constructs the share intent before entering its guarded activity-launch function, so the exception has a path to escape the click handler. Use an ID-based filename and guard export construction; preserve the human title in the subject/content. [Export filename](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/data/storage/ConversationRepository.kt:164), [workspace export action](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/ui/screens/ProjectWorkspaceScreen.kt:104).

## Seventeen source-supported issues

### S01 · P2 · Widget and shortcut actions are missing from the Apps menu

The Apps card menu contains pin-to-top, open-workspace, and delete. Home-screen widget and shortcut actions exist only inside the workspace's Project options menu after the app is ready. The widget feature has not disappeared from the code, but its expected library entry point is absent. Device check: compare Apps → options with Edit app → Project options. Add these actions to Apps and make “Pin to top” clearly different from a launcher shortcut. [Apps menu](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/ui/screens/AppsScreen.kt:64), [existing widget action](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/ui/screens/ProjectWorkspaceScreen.kt:102).

### S02 · P1 · Screen tabs bypass navigation actions and their required context

Both tab clicks and screen chips directly save a new `screen` value. They do not execute NAVIGATE's input bindings or conditions and do not establish a selected record. A detail/editor screen can therefore be entered without the record its actions need, or with a previous record's retained state. Device check: open a record through its row action, return, then enter the detail screen through a chip; also enter it before selecting any record. Define which screens are top-level destinations and route contextual screens through one navigation API with required arguments. [Direct tab/chip routing](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/runtime/NativeRunnerActivity.kt:115), [navigation action behavior](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/runtime/NativeAppRepository.kt:110).

### S03 · P2 · Tab labels connect to screens by array position

The runner pairs `appearance.tabs[index]` with `parsed.screens[index]`. Appearance obtains screen names from the planning brief, while generated screen definitions may be in a different order or have different names. There is no persistent screen ID on `TabSpec`. Device check: use a definition whose screen order differs from the plan; rename a tab and inspect which screen opens. Bind tabs to stable screen IDs and derive defaults from the actual published definition. [Positional mapping](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/runtime/NativeRunnerActivity.kt:115), [Appearance's source of screen names](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/navigation/AppNavigation.kt:149).

### S04 · P2 · Back jumps straight to the starting screen

Native Back does not maintain screen history. For Home → List → Detail, it sets the screen to Home rather than returning to List. Device check: traverse three screens through navigation actions and press Back. Add a bounded navigation stack with record context per entry and deliberate rules for top-level tab switches. [Back handler](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/runtime/NativeRunnerActivity.kt:107).

### S05 · P2 · Manually built native apps receive legacy Appearance controls

A manually configured app has a native published definition but no AI `brief.plan`. The Appearance route therefore passes `nativeScreens = null`, enabling the legacy layout/tab editor. This permits arbitrary added/removed tab labels, Journal and Dashboard options that the native runner does not implement as advertised. A one-screen manual app can show multiple editable tabs even though only one actual screen exists. Determine native status from its published head and use the published screens for native Appearance. [Appearance route](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/navigation/AppNavigation.kt:149), [layout selection](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/ui/screens/DesignScreen.kt:155).

### S06 · P2 · Selecting Tabbed can silently stop producing tabs

AppSpec accepts up to 12 screens, but the runner only renders bottom tabs for five or fewer. The editor does not explain this limit; for a larger app the saved Tabbed choice falls back to chips. Device check: choose Tabbed for six screens and reopen. Either disable that choice with a clear explanation or provide an overflow destination strategy. [Runner limit](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/runtime/NativeRunnerActivity.kt:114), [native tab editor limit](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/ui/screens/DesignScreen.kt:180).

### S07 · P2 · Changed navigation actions get generic review labels

`changedIds` emits `action:shared`; the review looks up `actions[key]`, whose key is `shared`. It consequently displays “Screen configuration” instead of the action label. This is especially unhelpful for reviewing a changed screen connection. Strip or structure the prefix and show the original/destination screen names and changed bindings. [Change-ID producer](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/builder/ScopedEditTools.kt:53), [review consumer](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/ui/screens/NativeEditScreen.kt:97).

### S08 · P1 · Selected-record expressions can keep an outdated snapshot

NAVIGATE/ASSIGN stores the whole selected row in runtime JSON. UPDATE changes the record table but leaves that snapshot unchanged; the runner continues using it for RECORD expressions. A repeated action that increments a selected record from `RECORD(amount)` can write the same value twice rather than accumulating. Device check: select a counter record, increment twice without reselecting, and compare the stored value. Persist entity/record identity and resolve current values before every action; clear invalid selections after deletion. [Selected snapshot](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/runtime/NativeAppRepository.kt:114), [update writes](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/runtime/NativeAppRepository.kt:99), [runner row context](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/runtime/NativeRunnerActivity.kt:81).

### S09 · P2 · Deleted records can enter the runner's initial render

The observed DAO query excludes deleted records, but its `collectAsState` initial value comes from `allRecords`, which includes deletion tombstones. Until the Flow delivers its first result, deleted rows can appear in lists, counts, and reference choices. Initialize from the same filtered query used by the Flow. Device check: delete a row and repeatedly reopen a large app while recording initial frames. [Initial records](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/runtime/NativeRunnerActivity.kt:73), [DAO query difference](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/runtime/NativeStorage.kt:21).

### S10 · P2 · “Use provider” can ignore an edited model ID

After a provider key is saved, changing Model ID leaves “Use provider” enabled. That button calls `useProvider`, which reads the stored model rather than saving the visible draft. Future calls can therefore use a different model from the one shown when selected. Device check: save model A, edit to B, tap Use, and inspect the next request. Require saving first or make activation save a validated draft atomically. [Use button](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/ui/screens/SettingsScreen.kt:263), [stored model selection](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/viewmodel/SettingsViewModel.kt:90).

### S11 · P1 · Removing a key can leave a draft that restores it

Edit an already-saved key, then choose Remove key. `clearProvider` deletes protected storage but does not clear `draftKeys`; the visible field is separately blanked. Back can report unsaved changes, and “Save key” calls `saveProviderDrafts`, restoring the now-hidden draft. Remove should clear the stored key, draft, and displayed state together. This is a source-derived settings sequence; no real credentials were used. [Removal](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/ui/screens/SettingsScreen.kt:266), [draft save/clear behavior](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/viewmodel/SettingsViewModel.kt:81).

### S12 · P2 · The leave-page Save dialog bypasses provider-field validation

The main Save key & model button requires both fields to be nonblank. The leave-page “Save key” action instead saves all drafts without that validation, including an empty model ID. Device check: blank Model ID, press Back, choose Save, then reopen or send a request. Reuse one validation function and keep the dialog open with a field-specific error. [Dialog save](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/ui/screens/SettingsScreen.kt:208), [main button validation](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/ui/screens/SettingsScreen.kt:261).

### S13 · P1 · Activity's retry can throw for stale requirements

Retry native build directly invokes a method that throws if its saved brief is older than the current plan. This is an expected condition after editing requirements, but Activity has no error boundary for the call. Device check: fail a build, revise its plan, then retry the old job in Activity. Display “Your plan changed; review the current plan” rather than allowing the exception to escape the click handler. [Unguarded retry](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/ui/screens/ActivityScreen.kt:80), [stale-input check](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/builder/NativeBuildJobs.kt:33).

### S14 · P2 · Native app palettes can break button-text contrast

The runner replaces `primary` and `secondary` but inherits `onPrimary` from the builder theme. Measured from the exact source colors: Ocean with Night gray's button text is approximately **2.709:1**; Sunset with Sea blue's button text is approximately **3.259:1**. Both fall below the repository's own normal-text test threshold of 4.5:1. The existing test covers builder themes, not app-palette × builder-theme combinations. Select corresponding foreground colors and test all combinations. [Native palette override](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/runtime/NativeRunnerActivity.kt:112), [theme colors](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/ui/theme/Color.kt:27).

### S15 · P2 · Some truncated provider replies count as completed

OpenRouter's nonempty finish reason is treated as success, including an output-length stop. Claude's message_stop is also treated as completion without checking the preceding stop reason. OpenAI's incomplete event is handled separately. A chat response can therefore be saved as finished despite truncation; structured generation may subsequently fail parsing without an accurate provider cause. Check provider stop reasons and preserve partial output with an incomplete status. [Completion handling](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/data/api/CloudProvider.kt:68). Protocol references: [Claude stop reasons](https://platform.claude.com/docs/en/build-with-claude/handling-stop-reasons), [OpenRouter streaming documentation source](https://github.com/OpenRouterTeam/docs/blob/main/api_reference/streaming.mdx).

### S16 · P2 · Ordinary Chat can be misinterpreted as app planning

The repository calls `ProjectPlan.fromResponse` whenever any reply contains `<plan>`, even when `cleanPlanResponse` is false for general Chat. The session's response handler also strips or replaces plan-tag content irrespective of Chat intent. Device check: ask Chat to explain XML containing `<plan>` or quote a planning example. Arbitrary discussion of that tag should remain ordinary content. Gate parsing and transformation on Create mode and use structured response metadata rather than a substring trigger. [Repository parsing](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/data/api/GeminiRepository.kt:79), [response handler](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/data/jobs/ConversationSession.kt:523).

### S17 · P2 · Renaming a project does not refresh its pinned widget

The rename action only updates the project record. Widget periodic updates are disabled, and rename does not call `refreshProject`. A native app also has no HTML-summary callback to incidentally refresh the title later. Device check: pin a widget, rename the project, and inspect the home screen. Refresh widget metadata after rename and appearance saves. [Rename action](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/ui/screens/ProjectWorkspaceScreen.kt:175), [widget update period](/home/tar_guest/Documents/BuildAI/app/src/main/res/xml/project_widget_info.xml:10).

## Eight improvement gaps

### I01 · Native widgets need an explicit supported contract

An unbound widget selects only projects with generated HTML, not native definitions. Live summary updates come from the HTML JavaScript bridge; the native repository has no equivalent summary integration. Actionable native widgets are explicitly an unfinished Stage 4 capability. Add a project chooser, stable per-widget binding, supported native summaries/actions, and record-change refresh. Avoid presenting generic native launch cards as if they implement custom widget behavior. [Widget fallback](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/widgets/ProjectWidgetProvider.kt:35), [stage status](/home/tar_guest/Documents/BuildAI/docs/IMPLEMENTATION_HANDOFF.md:34).

### I02 · Add a usable screen-connection editor

The plan editor contains screen names; scoped edits contain components/actions on one screen. There is no visual list of “this control opens this screen with this record,” no top-level/contextual screen distinction, and no supported scoped operation to add or rename a screen. Show a connection table with source screen/control, destination, required record/input context, condition, and Back behavior. Allow editing those connections with stable IDs and a route preview. [Plan screen-name editor](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/ui/screens/NativePlanScreen.kt:142), [edit scope](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/builder/ScopedEditTools.kt:13).

### I03 · Make acceptance checks independent of the generated implementation

Production probes are useful and isolated, but the generating model also supplies expected values. Matching an approved check's name does not prove its assertion expresses that check. Add deterministic acceptance contracts for supported operations and held-out values, then a separate evaluator of plan-to-behavior fidelity. For navigation, cover every edge through its actual visible control and assert record context as well as the screen ID. [Feature probes](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/builder/FeatureTools.kt:96), [final rechecks](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/builder/NativeBuildJobs.kt:132).

### I04 · Supply dependency-complete context for scoped edits

The edit prompt filters controls to selected IDs while retaining actions/entities. A selected button may reference an input that has been omitted, and a selected list may rely on earlier save/search controls. Reuse the transitive dependency approach already present in `probeContext`, including shared consumers of an action. Mark dependencies read-only while keeping the edit scope precise. [Edit prompt context](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/builder/NativeEditJobs.kt:37), [existing dependency resolver](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/builder/FeatureTools.kt:15).

### I05 · Address unique-history context overflow deliberately

Briefs allow 64 user turns of up to 4,096 characters. Planning uses a 4,096-token context plus prompt and output budget. Exact-quote deduplication helps repeated messages, but cannot make a large unique history fit. Estimate the complete prompt budget before inference and provide a durable compact decision representation with evidence pointers; never silently discard unresolved requirements. This gap is also acknowledged in the handoff. [Brief limits](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/builder/ProjectBrief.kt:44), [planning context size](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/data/jobs/ConversationSession.kt:438).

### I06 · Move persistence and dataset work off the UI thread

Room permits main-thread queries. Native field edits synchronously write the entire runtime JSON on every keystroke, and record rendering queries, parses, filters, and sorts datasets. Widget refresh also constructs a repository that scans legacy project files. Measure large datasets and slow storage, then move these operations to IO/background transformations while preserving ordered durable writes. No measured Android stall is claimed here. [Room configuration](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/data/storage/BuildAiDatabase.kt:74), [runtime input save](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/runtime/NativeRunnerActivity.kt:99).

### I07 · Provide native app and data export/import

The UI exports conversation text and legacy HTML. Native definitions, version history, and saved records have no complete user-facing transfer path, while device backup is disabled. The approved `.buildai` bundle requirement is unfinished Stage 4 work. Add an inspectable native export, optional record inclusion, import validation, and a restore preview before users depend on these apps for important data. Conversation export should also include the current native brief/approved plan rather than only the legacy `projectPlan`. [Current text exporter](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/data/storage/ConversationRepository.kt:178), [backup setting](/home/tar_guest/Documents/BuildAI/app/src/main/AndroidManifest.xml:19).

### I08 · Make capabilities and setup discoverable

Offline tools are disabled by default behind Settings → Advanced, while Create routes an unconfigured local-AI user into setup. Useful manual tools and starters are consequently hidden when they would be most helpful. Provider setup has no connection-test action, and a completed native quiz has no restart control; timers have no native pause/resume or completion notification integration. Introduce a clear capability/setup view and explain supported behavior at the choice point. Keep future scheduling/actionable-widget features distinct from current launch cards and saved deadlines. [Offline-tools default](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/data/storage/UiPreferences.kt:15), [Create setup gate](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/navigation/AppNavigation.kt:95), [quiz completion UI](/home/tar_guest/Documents/BuildAI/app/src/main/java/com/buildai/app/runtime/NativeRunnerActivity.kt:210).

## Recommended fix order and acceptance

1. **Screen connections and editing:** fix shared-action scope, contextual screen routing, stable tab IDs, and selected-record freshness together. Test two records and three screens, tab versus row entry, Back, and a shared navigation action.
2. **Trustworthy acceptance/review:** reject no-op edits; require changed-behavior coverage; show schema and action diffs; introduce varied independent calculator expectations.
3. **Crash and settings paths:** sanitize exports, catch stale retries, and unify provider draft/save/remove behavior. Test slash-containing titles, stale jobs, blank model IDs, and key removal using dummy credentials.
4. **Visible product regressions:** restore Apps' home-screen actions, derive native Appearance from the published AppSpec, fix contrast, and refresh widgets after metadata changes.
5. **Capability completion:** native widget contracts, connection editing, export/import, large-context handling, and responsiveness measurements. Treat these as feature work with their own acceptance criteria.

A device run should additionally cover rotation, large font scale, keyboard/insets, launcher support/cancellation, process restart, screen-off/reboot behavior, and low storage. Existing Stage 3 real-model failures remain historical evidence; this audit did not rerun or resolve them.

## Reusable adversarial-review instruction

> Review BuildAI against the user's observable workflows. Prioritize screen connections, saved-data correctness, intent fidelity, recovery, and discoverability. Try to falsify each success claim with a concrete counterexample. For every finding, record severity, exact entry point/input, expected behavior, actual behavior, source location, and evidence level. Exercise shared actions, different record selections, tab versus action navigation, Back, numeric variants, malformed model output, settings drafts, interrupted work, and reopening. Use independent expected results. Distinguish reproduced defects, source-supported issues, and unfinished capabilities. Preserve existing work and report test limitations explicitly. After a fix, rerun the failing scenario and the directly affected regressions; do not mark UI behavior verified from source inspection alone.
