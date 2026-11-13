# Reliability repair — 19 October 2026

## Outcome and scope

Implemented the connected creation/edit/runtime repair slice from the latest requirements review. The user reports **Qwen3 4B** with “Log meal failed after 2 repairs: actions need a visible button/record row action” and a separate planning-check failure. This is user-reported phone evidence; the exact installed binary, GGUF quantization/hash and planning validation detail remain unknown.

The action error was a structural selection gate: selecting an action without a BUTTON/RECORD_LIST failed before configuration. The selector now completes the visible-trigger dependency (and timer control where needed). If the plan explicitly includes saved data and the first record action needs a collection, that storage dependency is completed too. Configuration still has to implement selected kinds, visible bindings and the person's workflow; there is no template substitution. Independent phone/model acceptance remains open.

## Changes and evidence

| Problem | Repair | Verification level in this pass |
|---|---|---|
| Reported meal action error | Complete required visible controls before validating internal capability selection. | JVM regression for the exact missing-trigger shape passes. End-to-end controlled meal-build/save scenario compiled; Android execution pending. |
| Planning fails on malformed output | Use a typed planning grammar with known answer/question evidence IDs; retain semantic and evidence validation. | Both new-idea and follow-up grammars accepted by the pinned native parser. Actual Qwen3 4B planning pending. |
| Repairs repeat the same failed work | Retain valid configuration while repairing malformed probes, detect identical rejected responses, and propagate cancellation. Retry uses the currently selected model. | Controlled configuration-reuse and early-repeat-stop scenarios compiled; Android execution pending. |
| A01: shared action escapes edit scope | Reject changes to an action consumed by unselected controls; allow a cloned action rebound to selected controls. Include dependencies/other consumers in edit context as read-only. | Reject-leak and allow-isolated-clone JVM scenarios pass. |
| A02: empty edit reaches review | Require a meaningful normalized definition change; exercise changed controls/actions in checks. | Empty-edit rejection passes on JVM. Proposal/preview/apply regression compiled. |
| A03 / S07: schema/action changes missing or generic in review | Enumerate added collections/fields and label actions with destinations; describe record retention. | Field-only review and readable navigation-label JVM scenarios pass. UI rendering pending. |
| A04: live calculator accepts example constants | Follow observable input dependencies and reject newly added inputs disconnected from output/actions. Require selected INPUT expressions when specified. | Constant-only multiplication with numeric controls is rejected on JVM; real input-based implementation remains accepted. This does not prove every generated formula expresses user intent. |
| A05–A06: nested decimal arithmetic / numeric equality | Keep DECIMAL128 intermediates numeric; compare numeric values without scale/display differences; preserve text equality. Numeric input/record types flow into runtime contexts. | Nested division/multiplication, comma decimals and distinct text-value assertions pass on JVM. |
| A07: malformed collections disappear | Reject a present collection with the wrong type; reject non-string string-array entries. | Malformed entities/actions are rejected on JVM. |
| A08: slash in title breaks export | Use a validated project ID for filenames; guard intent construction on both export entry points. Export current native requirements/plan too. | Slash-title/file/subject/content Android scenario compiled; pending execution. |
| S08: selected records become stale | Resolve record identity against current records before actions/rendering; refresh or clear selection after writes/deletion; retain entity identity in navigation history. | Repeated increments from the same stale snapshot and deletion cleanup scenario compiled; pending Android execution. |
| S10–S12: provider draft/save/remove disagree | Save the visible validated draft on activation; clear hidden drafts on key removal; validate all drafts before leave-page Save. | Dummy-key activation/removal/blank-model regression compiled; pending Android execution. No real credential/provider request was used. |
| S13: stale retry escapes Activity | Catch retry errors and keep the screen usable. Retain input-version checks. | Compiled. |
| S14: palette inherits unreadable button text | Choose matching primary/secondary foregrounds for generated-app palettes. | Every palette with light/dark base schemes passes independent 4.5:1 contrast assertions. Full visual redesign is not implemented. |
| S16: ordinary Chat parses/strips plan tags | Gate plan parsing/transformations on Create. | Chat XML/plan-tag response survives without fallback on JVM; session/UI path compiled. |
| Idea lost before setup | Create/save the project and composer draft before setup is needed; resume the same workspace after visiting Settings. | Compiled; phone workflow pending. |

## Build and validation

Final artifact identity and machine-readable counts are in `result.json`. Build source hashes are in `source-hashes.txt`. Debug version: **0.3.0-dev.20261019**, package **com.buildai.app.debug**. The debug and instrumentation APKs are generated locally; no release was signed/published and nothing was installed on a phone.

Executed bounded offline Android/JVM checks:

```sh
bash scripts/gradle-bounded.sh --offline :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
python3 docs/evaluation/reliability-20261019/validate-grammar.py
```

The grammar check uses compiled production PlanningGrammar output and the unchanged pinned llama.cpp parser (`6c7a87f7e5e5cd75b8a641c3471f2dee84a6ed17`). Its isolated host harness aborts if unused vocabulary-token directives or native assertion paths are reached. It checks syntax/rule references without loading a model. It does not measure model output or sampling performance. New-idea/follow-up outputs contain 130/138 parser rules.

The targeted Android command was attempted after compilation, with no other emulator running:

```sh
BUILDAI_EMULATOR_RAM_MB=1536 BUILDAI_TEST_OUTPUT=/tmp/buildai-reliability-20261019 bash scripts/emulator-checks.sh 'com.buildai.app.NativeBuilderTest,com.buildai.app.NativeRelationshipTest,com.buildai.app.RevisionUxTest#providerDraftActivationAndRemovalAgreeWithProtectedStorage,com.buildai.app.RevisionUxTest#slashContainingProjectTitlesExportUsingSafeIdentityAndCurrentRequirements,com.buildai.app.NavigationWidgetRegressionTest'
```

**Android execution did not start.** The existing safety check reported insufficient available host RAM. Approximately 5,500 MiB was available around the attempt; even the minimum 1,536 MiB emulator requires an additional 4,096 MiB host reserve (5,632 MiB total). The reserve was retained. ADB found no connected device. This was a development-resource limit, not an app/test failure or an automatic-approval rejection. No instrumented pass, phone layout result, Qwen3 4B success or latency claim is made.

The first full candidate passed 93 JVM checks; the added ordinary-chat regression brought the final source to 94, with no failures/errors/skips. Refer to `result.json` for the final run and lint counts rather than treating previous reports as current measurements.

## Remaining work

Stage 3 remains open. First acceptance should use the user's actual meal logger on the A35/Qwen3 4B, including planning, Appearance, build, changed saved values, reopening, a scoped edit and retained records. Record the installed version/model hash and failed step if one remains. The controlled meal fixture demonstrates the reported dependency fix only when its Android run passes; it is not an AI-quality result.

Further gaps include independent plan-to-behavior oracles, unique long-history budgets, UI-thread persistence, truncated-provider replies (S15), an actual Appearance preview before build, the approved icon/theme design, a screen-connection editor, actionable widgets, scheduled reminders/routines and portable native bundles. Those requirements were not marked complete by this repair pass. The older navigation/widget repairs remain in place; their Android regressions are part of the pending targeted run.

The [generative UI investigation](../../ARCHITECTURE.md#generative-and-intelligent-ui-investigation--19-october-2026) recommends a small A2UI presentation experiment using existing records/revisions. It compares AndroidX A2UI, json-render, DivKit and Flutter GenUI using primary project documentation. No new UI framework, cloud dependency or model download was introduced.

Prior uncommitted work and the Git index were retained; no staging/reset/cleanup/commit operation was performed. The inference submodule is unchanged. README and handoff now distinguish the native candidate from historical HTML-prototype behavior.
