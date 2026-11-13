# Architecture decisions and staged migration

The approved [specification](SYSTEM_REQUIREMENTS.md) defines the destination. This guide distinguishes that destination from today's prototype.

## ADR-001 — Retain the local inference foundation

Keep the pinned llama.cpp submodule, Jinja template rendering, CPU path, cancellable native requests, shared libc++ packaging, UTF-8 bridge, and verified downloads. Changes around execution must retain real-model inference and cancellation tests. GPU support stays experimental. Model memory requirements need measured evidence rather than RAM-tier promises.

## ADR-002 — Jobs outlive screens

Persist the input version and job before execution. Screen/view-model objects observe persisted state and detach on navigation. Only explicit Stop/Cancel cancels work. A job has its own status, distinct from a project's draft/usable lifecycle. Inference runs through Binder in a dedicated `:inference` foreground-service process, with a declared `specialUse` explanation. Native failure must become an interrupted job while the UI stays usable. Partial text/checkpoints persist; retry starts a safe step rather than claiming to restore native memory. No automatic force-stop bypass or boot-time inference restart.

One model request runs at a time. Interactive work can take priority between bounded build steps; builds must not starve. Cloud work is limited to two requests. Keep WorkManager for downloads/maintenance; local AI must not depend on its Android 16 execution quota. See [foreground-service types](https://developer.android.com/develop/background-work/services/fgs/service-types) and [long-running workers](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running).

## ADR-003 — Transactional storage without deleting legacy files

Room will own projects, messages, drafts, immutable input versions, jobs, revisions, and app records. Import legacy conversation JSON transactionally and idempotently, retaining IDs and originals. Use a process-safe database, not independent in-memory repositories across Binder processes. Large models/assets stay in private files. Never enable destructive database migration to get past a schema mismatch. Import errors are visible, not silently skipped.

## ADR-004 — Progress represents productive work

Separate loading, prefill, decoding, and deterministic tools. Native callbacks report increasing model-load progress, successfully processed prompt tokens, and successfully decoded tokens. Stage text, keepalives, queued time, and repeated equal counters are not productivity. An inactivity clock uses monotonic time: initially 300 s loading/prefill, 120 s decoding; measured A35 results may adjust these. Output and repair attempts remain bounded. A failed/stalled step retains its completed checkpoint and prior working app. Validation itself has independent bounded timeouts.

## ADR-005 — Native app composition

AppSpec is versioned, typed data with stable screen/component/entity/action IDs. Registered components supply native Compose behavior. Bounded expressions/actions provide calculations and control flow. Imported definitions cannot execute JavaScript, shell, or native code. Each capability declares contract, configuration, permissions, and independent interaction tests. Notes, forms, calculations, summaries, timers, quizzes and routines can be combined in different apps. Manually composed apps pass Stage 2 before model-generated plans are connected in Stage 3.

Legacy HTML keeps its current WebView origin and separate runner. Rebuild to AppSpec is explicit. Do not automatically copy arbitrary localStorage into native typed records.

## ADR-006 — Publish verified revisions atomically

An AppPatch names a base revision and affected stable IDs. Reject stale patches. Validate a draft before publishing it atomically, retaining the prior working revision. Initial record-schema changes are additive. Removing a component hides unused records rather than deleting them. Undo restores behavior while retaining current records; restoring a data backup is a separate operation. Editing a project does not cancel its currently usable app.

## ADR-007 — Permission and sharing boundaries

Local operation is the default; strict private blocks external requests. Connections and cloud AI require specific, visible enabling. Download consent is distinct from inference consent. Strict-private speech requires on-device recognition; typed input remains available. Connector responses are untrusted data and cannot alter permissions. Credentials remain protected and never enter prompts unnecessarily, bundles, logs or diagnostics.

`.buildai` bundles include schema/capability versions, definitions and assets; records require explicit inclusion. Import previews declared capabilities and rejects unknown versions. No credential, device permission, private grant or executable-code import. Android widgets use their own supported renderer/actions, not arbitrary Compose layouts; timer deadlines and scheduled reminders survive screen closure.

## Migration order and gate ownership

Stage 0 freezes source/evaluation evidence. Stage 1 replaces screen-owned execution incrementally and tests recovery. Stage 2 introduces native manual examples/records/revisions. Stage 3 connects brief memory and bounded builder tools. Stage 4 adds native capabilities/sharing/connectors. Stage 5 measures held-out outcomes and phone behavior. Earlier HTML smoke tests remain useful for legacy compatibility, not native runtime or agent reliability evidence.

## ADR-008 — Separate selection, configuration, and executed checks

Each approved feature first selects registered native capability kinds. The selection is an explicit typed contract, not a permission grant or an idea-specific template. Configuration then supplies real controls, data bindings and bounded expressions; checks refer to the actual resulting IDs and run in isolated Room storage through the production runtime. Only checked features enter checkpoints, and final publication remains atomic against the approved brief version and current app head.

Every failed tool output, including selection, must reach the next bounded repair alongside its concrete error. Generic schema validity is insufficient: a selected timer must bind a timer control, assigned inputs must be observed after the action, and generated behavior still needs independent interaction checks. Controlled responses establish orchestration contracts; successful independent actual-model behavior is a separate Stage 3 gate. Current Qwen trials have failed that gate.

A test-only grammar switch helped distinguish protocol confusion from model capability. It does not disable production structural validation, change the inference foundation, or authorize publication of malformed output. Accepted user suggestions remain durable decisions and cannot be revoked or renamed by assistant output alone.

## ADR-009 — Planning completion is a transaction

Final planning replies commit the project brief, assistant message and completed job together before the live brief exposes readiness. The transaction uses the repository's existing lock order, preserves current drafts/queued input, and checks the project input version and running job. A cold reader cannot observe the old answer behind a newly ready plan. Late progress updates are restricted to running jobs.

## ADR-010 — Keep capability choices separate from stored schemas

The internal selector returns a short list of registered capability names directly from the approved workflow and acceptance checks. Known names, unique entries, types and visible action bindings are validated before translating into the existing public CompositionSelection schema. Persisted plans and app definitions retain their schema. No choice enables permissions or cloud access.

The earlier intermediate outline invented saved data, and the mandatory boolean protocol omitted required arithmetic in an actual-model trial. The current candidate removes that outline and compulsory negative fields to reduce irrelevant context/output. It still requires configuration to implement selected kinds and operations, followed by executed probes and independent interaction checks. A shorter protocol is a hypothesis until actual-model behavior passes; failed trials remain in evaluation/STAGE_3.md.

## ADR-011 — Preserve planning evidence while reducing exact repetition

The model context consolidates only byte-identical user quotes, retaining all corresponding message IDs and their original chronological sequence. Unique text, remembered answers, questions, plan and persisted ProjectBrief remain intact. This reduces repeated context without asking the model to invent a summary or erasing a decision. It does not promise that arbitrary unique histories fit the local context window. Optional collections reject wrong types rather than silently dropping questions or checks. Its exact reconstruction and malformed-array regressions pass on JVM; planning persistence passes on the latest device candidate. Actual-model long-conversation performance remains unverified in evaluation/STAGE_3.md.


## Generative and intelligent UI investigation — 19 October 2026

Requested during the reliability repair pass. Recommendation: retain AppSpec/Room as the durable app and data contract; evaluate A2UI as a presentation adapter. No framework dependency or external runtime service has been added.

Generative UI means that the model chooses a layout, controls and bindings from a declared catalog. Intelligent UI adds task-sensitive choices: ask a consequential question with suitable controls, show the relevant preview, and offer a scoped change. The generated app should then run normal actions locally without consulting a model for each tap. These are proposed product behaviors, not implemented claims.

| Open-source option | Fit and limitation for BuildAI |
|---|---|
| [A2UI / AndroidX Compose renderer](https://developer.android.com/develop/ui/compose/agentic) | Closest fit to the Kotlin/Compose app: catalog-defined native controls, reactive bindings, custom components and validation. The official renderer supports protocol 0.9.1. [AndroidX release notes](https://developer.android.com/jetpack/androidx/releases/a2ui-compose) list 1.0.0-alpha01 and a compileSdk 37.1 dependency requirement. BuildAI currently uses SDK 35, AGP 8.6.1 and Compose BOM 2024.10.00, so direct adoption needs a separately measured toolchain experiment. |
| [Vercel json-render](https://json-render.dev/docs/renderers) | Strong catalog/spec/binding pattern; React and React Native renderers. Its [introduction](https://json-render.dev/docs) also describes streaming patches and conditional visibility. No Kotlin/Compose renderer is listed. Good design reference or a future web export candidate; a React Native migration would add a second UI stack here. |
| [DivKit](https://divkit.tech/docs/en/concepts/faq) | Native Android/iOS/Web JSON layouts; descriptions can be stored on device. [Its runtime](https://divkit.tech/docs/en/) supports variables, state, animations and updates. Worth comparing for richer layouts, but a UI renderer does not provide BuildAI's record migrations, build ownership or revision verification. |
| [Flutter GenUI](https://docs.flutter.dev/ai/genui) | A generative-widget approach for Flutter. Useful reference; changing this existing Compose app to Flutter would expand the repair into a UI migration. |

The A2UI protocol is [transport-independent](https://a2ui.org/specification/v0.9-a2ui/); our inference-process stream could supply messages locally. No hosted agent or new disclosure of project data is implied. Network/image/URL handlers would remain controlled by BuildAI. This is an integration inference, not a measured compatibility result. The A2UI, json-render and DivKit project licenses are Apache 2.0: [A2UI license](https://raw.githubusercontent.com/a2ui-project/a2ui/main/LICENSE), [json-render license](https://raw.githubusercontent.com/vercel-labs/json-render/main/LICENSE), [DivKit license](https://raw.githubusercontent.com/divkit/divkit/master/LICENSE). Dependency notices would still need review at actual adoption.

### Small useful experiment

Use the reported meal logger as the first comparison, with the same saved AppSpec, local records, model and acceptance examples on both paths. Render Meal, Amount, Save and the saved-meals list through a small custom catalog. Bind events to NativeAppRepository, keep IDs stable, and change only a draft surface until the user applies a revision. Compare full form creation, an edit adding a category, retained records, keyboard/large text, and operation with the model unloaded. Measure semantic success, repair count, output tokens, latency and memory; attractive output alone is insufficient.

Try an adapter for the existing native renderer first, then an isolated AndroidX A2UI proof if dependency compatibility is acceptable. Do not feed an entire general-purpose catalog to the small local model. Reuse the feature-specific grammar/catalog and supply transitive read-only dependencies for edits. Richer layout primitives and real Appearance previews are the immediate UI gaps; merely changing renderer libraries cannot repair the failed capability or planning decisions.
