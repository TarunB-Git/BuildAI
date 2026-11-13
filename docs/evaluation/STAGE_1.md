# Stage 1 evidence — durable interaction candidate

Date: 2026-10-03. Stage 1’s **local implementation gate passed** on 2026-10-04. Stage 2 may now begin. Physical A35/Android 16 acceptance remains unverified.

## Implemented candidate

- Screen view models observe application-owned sessions; detaching/clearing a view model does not cancel its request.
- Room stores project/message snapshots, drafts, queued input, immutable request inputs, statuses, progress stage and saved build drafts. Legacy JSON import is idempotent; IDs and originals remain intact. The legacy synchronous repository API currently uses Room main-thread compatibility; larger datasets require IO/Flow migration and physical latency measurement.
- Separate Chat/Create conversations; local inference defaults for new installations; local voice requires the platform on-device recognizer. The older Create planning/readiness flow is retained until Stage 3.
- Local JNI runs through Messenger/Binder in a non-exported `:inference` foreground-service process. A native failure becomes an interrupted request. A main-process foreground service anchors user-started orchestration and holds a wake lock only while jobs are active.
- Local builds run outside WorkManager; legacy/cloud generation remains compatible. Saved input is distinct from future queued messages. Publication rejects a changed plan/design and retains the previous working app.
- Native load, processed prompt and decoded token counters drive 300 s loading/prefill and 120 s decoding inactivity budgets. Queued time/status text do not reset them. A real native stall can restart only the inference process. No total three-minute local build timeout remains. Token limits and two repair attempts remain bounded.
- Fair model queue prioritizes interactive requests between steps, with a waiting build admitted after at most three interactive turns. Cloud calls are bounded to two.
- Apps/Assistant/Activity navigation and cross-project job status; explicit Stop; indeterminate build indicator instead of invented percentages.

No native AppSpec runtime or capable native builder is claimed yet. Legacy HTML checks retain their limited meaning.

## Executed checks

| Trial | Outcome | Limits |
|---|---|---|
| Initial native progress build | 35 JVM tests passed; APK/test APK compiled | Six watchdog tests include productive work beyond 180 s and genuine/repeated-counter stalls. |
| First durable device pass | 13/14 passed, 55.526 s; no skips | Back/ViewModel detachment, import, interrupted partial data, all six recovery and four real-GGUF tests passed. Queue test observed reply text before the terminal job transaction; corrected to await persisted completion. |
| Lifetime + wider workflow pass | 10/14 passed, 353.751 s; no skips | Forced native-process death/retry, real native counters, all four durable checks and a productive controlled legacy build beyond 180 s passed. Four legacy workflow failures remain: three invalid draft/library assumptions in existing tests and one preview window-focus failure. |
| Fair queue / corrected workflow build | 37 JVM tests passed, zero failures/errors/skips; summed test time 2.707 s; Gradle 79 s | Debug/test APK and lint pass. First corrected workflow trial: 5/6 passed, 131.501 s; failure captured an Android System UI ANR. |

Baseline measurements and model hash are in [BASELINE.md](BASELINE.md). All emulator trials used one Android 15/API 35 x86_64 emulator, one guest CPU and 2048 MB RAM, low priority on two host cores, no simultaneous compilation, explicit shutdown. The lifetime fixture uses controlled streamed output and independently exercises the real generator/validator; it proves continuation beyond 180 seconds, **not** AI app correctness or phone speed. Exact fixture elapsed time was not captured in the runner log; the passing monotonic-clock assertion verifies >180 s.

The first durable candidate APK SHA-256 was `2fa19664333ca0567c525ccc79e6e763916694069f9738fe8e1dd070882c6954`. Source hashes for that tested candidate are in `stage1-candidate-source-hashes.json`. Later changes need their own final hash/results.

## Latest trials

The final fair-queue/connection/cancellation candidate passed 37 JVM tests, APK/test APK and lint in a 68-second Gradle run. A second device trial used two guest CPUs on the same two host cores and 2048 MB RAM: 13/14 passed in 115.864 seconds. All four real-GGUF and four durable tests passed; five of six UI workflows passed. The remaining preview-focus check again coincided with an Android System UI ANR. Both screenshot and window dump identify `com.android.systemui`; the result does not establish a BuildAI layout defect. This failure is unresolved, and the next check uses the existing phone-shaped API 35 AVD instead of the resizable AVD.

Connected streaming, additive Room progress storage (schema 1→2), voice cancellation isolation, service-owned build checks and explicit force-stop/cold-restart checks are newer candidate changes. Three controlled streaming JVM tests passed. Their final APK/device verification is pending.

## Outstanding device/release acceptance (historical local items resolved below)

- Resolve preview focus failure and pass the corrected end-to-end workflow regressions.
- Check final fair-queue/connection/cancellation changes through the real inference path.
- Exercise service-owned local build navigation, screen-off, cancellation/retry and process recovery independently of the legacy WorkManager tests.
- Verify actual force-stop/cold-restart behavior, permission denial, resource pause, layout and Android 16 behavior on A35; no physical device is connected yet.
- Connected streaming adapter remains to be completed/verified with controlled responses. Live Gemini remains manual, never counted as automated success.

These are open gate items, not completed capabilities or release claims. Do not move to the native runtime stage while this gate remains open.

## Fresh-phone regression and recovery investigation

The existing Medium Phone AVD booted but refused APK installation with `INSTALL_FAILED_INSUFFICIENT_STORAGE`; no tests ran. No existing AVD was wiped. A temporary phone AVD under `/tmp/buildai-test-avd` used the installed API 35 Google APIs x86_64 system image, 4 GB sparse data capacity, two guest CPUs, 2048 MB RAM, and the same two host cores. It booted in 115 seconds. All 17 selected tests passed in 187.203 seconds: six UI workflows, four durable conversation checks, three service-owned local-build checks and four real-model checks. This resolves the emulator preview-focus regression on a fresh phone configuration; it does not establish every physical layout or the originally reported margin symptom.

That candidate APK was `088b3ffa93b5f61779e2a8bcaa659b6f4da608afcd98e0461d2be38e8a244fde`; source hashes are in `stage1-stream-candidate-source-hashes.json`. Its 40 JVM tests passed with no failures/errors/skips in 2.696 seconds of test time; the Gradle/APK/lint run took 81 seconds.

The subsequent explicit force-stop preparation failed before force-stop: its throttled MockWebServer fixture also throttled reading the incoming request, so no partial response arrived. The fixture now separates request reading from response throttling. A new independent early-chunk check then exposed a real cancellation edge case: closing a blocked streamed read could report `Socket closed` as a failed coroutine. Cancellation is now checked before propagating that IO error. Final verification is pending; this investigation is not counted as a passed recovery check.

## Final local gate

Assistant entry text and Chat/Create selection now persist in Room and survive view-model recreation. The entry is separate from visible user projects. Legacy import errors are visible in Activity. The final candidate passed 41 JVM tests with zero failures/errors/skips (5.744 s summed test time); APK/test APK and lint passed in 95 s. APK SHA-256: `02156fd486b20601a5b9688f53d55a459f2c9e9674f7e0b9fbabd32e97baab62`. Exact results/source hashes: `stage1-entry-jvm-result.json` and `stage1-entry-source-hashes.json`.

The temporary AVD was configured natively at 540×1200, density 210, rather than resizing Android immediately after each boot. Resources stayed at two guest CPUs, two bounded host cores and 2048 MB. Boot: 68 s. Final workflow/durable/storage migration run: **12/12 passed**, 119.228 s, no skips. Actual running HTTP reply seed: 5.157 s; force-stop; cold restart recovery: 0.027 s. Each recovery phase executes one test and intentionally filters the other (two executed passes and two phase-filter skips overall); skips are not counted as acceptance. Explicit shutdown completed. The larger/natively different display matrix and the original reported margin symptom are not yet verified.

Prior native-process death/retry, real native progress, fair queue, all real GGUF regressions, service-owned screen-off/cancellation/stale-plan checks and the >180 s productive controlled build remain independently recorded above. Those results satisfy the Stage 1 local demonstration; they do not establish arbitrary app quality or A35 results. Follow [A35_STAGE_1_CHECKLIST.md](A35_STAGE_1_CHECKLIST.md) for physical acceptance.
