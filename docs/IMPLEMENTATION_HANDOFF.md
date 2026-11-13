# BuildAI implementation handoff

## Current continuation — 19 October 2026

Continue from the reliability repair candidate, not the older source-hash snapshots below. The user identifies their model as **Qwen3 4B** and reports “Log meal failed after 2 repairs: actions need a visible button/record row action” plus a planning-check failure. The installed phone binary and exact planning diagnostic are still unknown; no phone is connected.

Action-trigger dependencies are now completed by BuildAI before configuration, rather than spending semantic repairs on a missing button/timer. Planning has a typed grammar, retaining semantic/evidence validation. Repairs retain valid configuration after malformed check generation, reject repeated identical failed replies and honor cancellation. Retry picks up a newly selected model while retaining saved input-version checks/checkpoints.

The same pass addresses the eight reproduced audit defects plus selected-record freshness, settings draft/save/remove behavior, ordinary-chat plan-tag handling, caught stale retries and app-palette button contrast. See [repair evidence and limits](evaluation/reliability-20261019/REPORT.md). Android scenarios are compiled; the emulator launch was refused by the existing host-memory reserve. Do not claim phone or real-model success from these changes. Stage 3 stays open and Stage 4 integrations remain unfinished.

The [architecture investigation](ARCHITECTURE.md#generative-and-intelligent-ui-investigation--19-october-2026) compares A2UI, json-render, DivKit and Flutter GenUI. A2UI is the recommended presentation experiment; no new renderer dependency, networking service, UI-stack migration or adoption decision was introduced.

Debug version: `0.3.0-dev.20261019`; package `com.buildai.app.debug`. Preserve the user's existing index and all prior uncommitted work. No commits existed at entry. The pinned llama.cpp source is unchanged. Next meaningful acceptance: the user's actual meal logger and planning flow with Qwen3 4B on the A35, then scoped editing with retained records. Do not repeat unproductive model trials or add unrelated tests to pad coverage.

Updated 2026-10-04 (Europe/Stockholm; testing began 2026-10-03). Authoritative requirements: [SYSTEM_REQUIREMENTS.md](SYSTEM_REQUIREMENTS.md), retrieved in full from “Assess repository status”, local thread `01a0e452-7635-7332-a34f-5d16244edf28`, plan `01a10316-367d-7ac1-a83d-37b3af78e23c-plan`. The source specification is preserved verbatim. It contains 24,871 characters before file newline normalization.

## Decisions

- Private, phone-first builder for people who cannot program; capability composition and a native structured runtime, rather than a template for each idea.
- Apps, Assistant, Activity; distinct Chat/Create; remembered decisions, relevant questions, optional suggestions, an editable plan, and one Build approval.
- Durable jobs are independent of screens; real progress governs stall detection.
- Local GGUF CPU inference remains the foundation. Cloud AI and connections require explicit enabling.
- Full-screen execution, scoped revision patches, stable IDs, additive migrations, current-record retention on undo, and inspectable `.buildai` sharing.
- Keep legacy HTML projects and their origins. Converting one to AppSpec requires an explicit rebuild.
- Primary acceptance phone: Galaxy A35, 8 GB, Android 16. No phone performance claims without measurements.
- Apache 2.0; preserve third-party notices and separately document model licenses.
- Quality gates in order; no deadline override. Live Gemini remains a manual check.
- MVP and future concept boards are in `docs/design/`; future images do not add MVP scope.

## Preservation and baseline

No commits existed at entry (`main` is unborn). Do not reset, stash, clean, switch branches, or stage the user's work. No applicable AGENTS.md exists at the project root or its ancestors; `third_party/llama.cpp/AGENTS.md` applies if that submodule is edited. The submodule is retained unchanged.

Before production edits, saved 100 working-tree source files and the exact staged/unstaged patches and index listing to `/tmp/buildai-pre-redesign-20261003/`. The baseline source inventory and status are under `docs/evaluation/baseline/`. This snapshot includes the newly retrieved specification; all preexisting project sources are preserved. `/tmp` is a recovery aid, not permanent backup storage.

The index must remain byte-for-byte equivalent in its `git ls-files --stage` listing. Changes made here are unstaged additions to the existing work. Baseline APK SHA-256: `208169cb2c74f819f010fa76bd43b5041698b942437a2486a7108158a8e86ccb`.

## Evidence and stage progress

| Stage | State | Evidence / next exit requirement |
|---|---|---|
| 0 — Establish requirements | Local baseline established | Specification, design references, baseline snapshot, architecture decisions, failure traceability, and evaluation fixtures saved. Fresh JVM run: 29 tests, zero failures/errors/skips; 2.658 s test time, 34 s Gradle wall time. Baseline emulator: 10 tests, zero failures/skips, 76.639 s; automatic shutdown completed. Margin symptom remains unconfirmed. |
| 1 — Durable interaction | Local gate passed; physical acceptance pending | See [stage evidence](evaluation/STAGE_1.md): application-owned sessions, Room import/jobs, isolated native service, genuine progress, local default, and Activity implemented. Back/draft/partial persistence, real native interruption/retry, productive controlled build beyond 180 s and actual force-stop recovery verified. Final 41 JVM checks, APK/test APK/lint, 12/12 device checks and two executed process-recovery checks pass. Physical Android 16/layout/thermal acceptance remains pending. |
| 2 — Reliable app runtime | Local gate passed; physical acceptance pending | Typed AppSpec/components/actions, Room records/revisions, scoped patches and native/legacy full-screen runners implemented. 46 JVM checks/APKs/lint pass; native interactions and usable-field edit/undo retain records. See evaluation/STAGE_2.md for successful checks and failed trials. |
| 3 — Capable builder | In progress; AI quality gate open | Brief memory, editable plan, incremental checks, guided configuration and scoped edits implemented. Latest 74 JVM checks/APKs/lint pass; 12 deterministic device checks pass, including atomic planning persistence, record links/data retention and negative conditional-action probes. Repeated quote context reduces 203,080 to 8,327 characters without losing evidence. Autonomous calculator and diary selection both failed after two repairs; neither published an app. Only a prior manually supplied-capability calculator diagnostic succeeded. See evaluation/STAGE_3.md. |
| 4 — Phone capabilities | Not started | Real shortcuts, actionable widgets, reminders, routines, bundles, and explicit optional connections. |
| 5 — Showcase release | Not started | A35 evaluation, 20 held-out scenarios × 3, usability, layout/background regressions, and release targets. |

Prior QA reports are historical reports, not new measurements. Baseline JVM results are in `docs/evaluation/baseline/jvm-result.json`; each stage has separate candidate results. No phone is currently connected. Physical ARM inference, A35 latency/memory/thermals, Android 16 lifecycle behavior, Gemma inference, other RAM tiers, and live Gemini remain unverified.

## Baseline code evidence (before this implementation)

See [evaluation/BASELINE.md](evaluation/BASELINE.md) for failures and reproducible acceptance steps. Current `ConversationViewModel.leaveProject()` and `loadConversation()` cancel the request; `viewModelScope` owns its lifetime. Local tokens are displayed but only completion is persisted. Composer state is screen-owned. A project switch loses an in-flight response.

`LocalPwaGenerator` wraps all generation/repairs in `withTimeout(180_000)`. JNI has loading cancellation and stage callbacks, but no loading counters or processed-token progress. `GenerationWorker` uses WorkManager/dataSync for inference. The engine serializes native calls in the UI process, so a native crash still kills the UI. SecureStorage defaults to cloud when no preference exists. Plan readiness uses exact text and a user-message count. `ResultScreen` is a preview with builder bars and extra padding. HTML rebuilds replace the artifact and delete older files; no immutable app revisions exist.

These findings justify the redesign; they do not prove the reported margin symptom or arbitrary model behavior.

## Safe continuation

Use `bash scripts/gradle-bounded.sh --offline …` for builds and `bash scripts/emulator-checks.sh …` only after the build finishes. Both share a lock. One Gradle worker, 1536 MB heap, 768 MB Kotlin heap, two JVM processors, one native compiler job, one two-core emulator (2048 MB default, 3072 MB for the measured 1.7B trial) pinned to two host cores (earlier baseline trials used one guest CPU), explicit shutdown and deadlines. Never run compilation alongside the emulator. Avoid parallel agents/builds.

Record commands, fixture/model hashes, executed versus skipped tests, measured durations, and limitations after every gate. Do not mark a stage complete based on source inspection alone. See [ARCHITECTURE.md](ARCHITECTURE.md) and [LEARNING_GUIDE.md](LEARNING_GUIDE.md).

## Latest verified progress

Stages 1–2 passed their local gates; A35 acceptance remains pending. The latest Stage 3 candidate is APK `885ae0148fcdf9091d368175195ae511e7587478f6825829608b096dae142e5e`: 74 JVM checks, APK/test APK/lint and 12 deterministic device checks pass. Planning completion is transactional; strict collection types prevent malformed questions/checks being silently dropped. Identical quotes are shared in model context while preserving every ID and chronological occurrence. Conditional-action probes verify specific rejection and unchanged records/runtime, then a successful save.

Autonomous calculator and diary selection both failed on that binary before configuration/publication, despite bounded repairs. A prior real 1.7B model passed one calculator configuration only with manually supplied capabilities. Stage 3 remains open and Stages 4–5 are not started. Unique-history context overflow, actual-model planning/scoped editing, remaining capability contracts, phone results and live Gemini remain unverified. Failed outputs, exact hashes and timings are retained in [evaluation/STAGE_3.md](evaluation/STAGE_3.md).

Pinned llama.cpp remains unchanged at `6c7a87f7e5e5cd75b8a641c3471f2dee84a6ed17`; the original Git index still matches exactly. Continue from the latest measured failure rather than assuming roadmap prose is evidence. Do not add more time to a semantic failure or count a controlled reply/guided configuration as autonomous success.

Final continuation checks: source hashes match `evaluation/stage3-evidence-source-hashes.txt`, the original staged index matches its Stage 0 snapshot, and the pinned submodule is clean. ADB reports no emulator/device remaining. The task-downloaded duplicate host 1.7B GGUF under `/tmp` was removed after verifying its exact hash to recover 1,834,426,016 bytes; the verified test-only guest fixture and all measured outputs remain. Reuse the guest fixture for sequential checks or redownload the pinned catalog file when copying to a new test environment. No repository/user model file was removed.
