# Baseline and regression traceability

Date: 2026-10-03. The source snapshot/hash manifest freezes the current prototype before production changes. There is no baseline Git commit; use `baseline/source-hashes.json` and `/tmp/buildai-pre-redesign-20261003/working-tree.tar.gz`. Baseline binaries are cached build artifacts until freshly rebuilt; their hashes are recorded in the handoff.

## Reproductions and acceptance checks

| ID / requirement | Prototype evidence and reproduction | Required independent regression |
|---|---|---|
| B01 / UX-08 | `ConversationViewModel.leaveProject` cancels `requestJob`; `showHome` calls it. Send against a controlled delayed reply, wait until the server receives the request, press Back, reopen. | Reply completes and is saved under the original project; switching to another project never receives that reply. |
| B02 / UX-01,03,04 | `planRequested` requires exact `Review my plan` and ≥3 user messages. Local prompts forbid planning otherwise; draft button requires ≥2. | Ask questions explicitly; answer/change decisions; readiness follows essential decisions, no phrase/count gate. Ordinary Chat never produces a build plan. |
| B03 / UX-09 | `LocalPwaGenerator.generate` uses `withTimeout(180_000)` around generation and repairs. | Controlled productive counters/tokens for >180 s finish; fixed/repeated status and counter events expire at the relevant inactivity budget; checkpoint retained. Use a virtual monotonic clock for fast deterministic tests, plus a device-duration test. |
| B04 / UX-11 | Local generator consumes at most 4000 chars of previous HTML and requests a complete replacement. `saveGenerated` deletes older output. | Add search to notes; assert unrelated actions, stable IDs and all records remain, stale patch is rejected, undo keeps records. |
| B05 / UX-10 | Result runner shares builder top/bottom bars and applies 12 dp extra padding. Reported half-screen margin has not been reproduced. | Open a saved app; measure usable bounds and interact with bottom controls on small/large, portrait/landscape, keyboard, cutout and large text. Save screenshots and actual failing geometry before claiming a margin diagnosis. |
| B06 / UX-06 | Partial local messages update only in memory. Composer uses `rememberSaveable`, and new Send invokes Stop. | Draft/message changes persist before navigation; partial text survives process death honestly; queued input never disappears or mutates active input version. |
| B07 / Background | Native inference runs in-process; WorkManager dataSync owns long builds. | Service/native crash leaves UI available, marks job interrupted, and explicit retry resumes a safe step. Screen-off completes with one model request at a time. |
| B08 / Privacy | SecureStorage defaults to cloud. Speech uses general recognition without a strict-private check. | Clean install defaults local; no external requests in strict private; speech refuses unsupported local recognition; cloud enable is explicit. |
| B09 / Capability correctness | HTML opening/storage checks plus narrow addition probes; fixed quick templates exist. | Behavior independently checked for composed native capabilities, not just labels/HTML or model claims. Unsupported ideas have explicit scope/failure. |

Source inspection establishes the branch/ownership causes in B01–B04/B06–B08. It is not a measured phone reproduction. Device workflow reproductions must separately record the observed outcome. B05 remains a symptom to investigate.

## Fresh measurements

- Fresh command: JDK 17, SDK 35, `./gradlew --offline --no-daemon --max-workers=1 :app:testDebugUnitTest --rerun-tasks`, `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2`, `CMAKE_BUILD_PARALLEL_LEVEL=1`, low priority.
- 29 JVM tests executed; 0 failures, errors or skips. Summed test duration 2.658 s; Gradle wall time 34 s. Result: `baseline/jvm-result.json`; raw log `/tmp/buildai-baseline-jvm-fresh.log`.
- Baseline device checks: 10 tests passed without skips in 76.639 s (`baseline/emulator-tests.log`); boot 128 s; shutdown completed. Environment: one Android 15/API 35 x86_64 emulator, one guest CPU, 2048 MB RAM, pinned to two host cores. No concurrent build.
- Qwen3 0.6B Q4_0 fixture: 428,970,080 bytes; SHA-256 `da2572f16c06133561ce56accaa822216f2391ef4d37fba427801cd6736417d4`. Catalog pin `b5f37287796e5be0ea3dab2e7430873fb3f73e49`.
- A35/ARM/Android 16 timing, peak memory, thermal/battery behavior: **not measured**. No derived phone benchmark or other RAM-tier support claim.
- Live Gemini: manual and not performed. Automated cloud tests use local MockWebServer.

## Evaluation protocol

`held-out-scenarios.json` freezes 20 future build/edit acceptance tasks, plus unsupported cases. Keep these out of model prompts and repair-specific examples. Use separate development demonstrations. Compare baseline only on shared supported tasks; report unsupported baseline tasks as such. A task pass requires all independent actions/data expectations, not matching a generated label or claiming “tested”.

On the A35 record Android/app/llama/model versions and hashes, temperature/thermal state, initial memory/storage, cold versus warm state, end-to-end duration, generated/prefill tokens, peak process PSS, every repair, missing/repeated questions, data retention and background completion. Run each held-out task three times; keep individual results and failures. Do not select the best run. Include unsupported requests separately from the success denominator. Record physical device support by configuration.

Targets: ≥90% supported outcomes within two repairs; no data loss or silent generic substitution; all required layout/background cases pass on A35; ordinary interactions p95 <300 ms for demonstration data; 4/5 beginners complete create/edit/launch without help. These are future acceptance targets.
