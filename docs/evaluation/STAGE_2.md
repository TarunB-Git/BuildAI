# Stage 2 evidence — native runtime candidate

Stage 1 local gate passed first. Stage 2 **local gate passed**; AI was not connected to the runtime for these checks. Physical/release acceptance remains pending.

## Candidate implementation

Versioned `AppSpec` describes registered native text/input/button/list/summary/timer/quiz components, record schemas, typed actions and bounded expressions. Unknown schema/configuration and dangling bindings are rejected. Decimal calculations use decimal arithmetic; imported definitions cannot introduce JavaScript, shell or native executable code.

Room schema 3 adds immutable revisions, current heads/runtime state and app records using an additive 2→3 migration. Records are scoped by app/entity/stable record ID. Patches name an expected revision; publication rejects stale changes. Schema edits are additive. Undo restores previous controls/actions while retaining today's record schema and records. Row deletion is an explicit action that archives a record; app deletion is separate and explicit.

A native full-screen activity renders the actual app, without the builder tabs or HTML preview frame. Record lists use lazy rows. Input/navigation/quiz/timer state persists separately from app definitions. A timer saves a wall-clock deadline; Android reminders are not implemented by that component yet. Manually composed notes/quiz, budget and routine fixtures share the registry; they are not builder templates or fallback substitutions.

Current deliberate bounds: 12 screens, 300 controls, 20 collections, 40 fields per collection, 160 actions, eight expression nesting levels, 5,000 active records per app, 16 KiB per record and 64 KiB unsaved runtime state. These limits are not phone benchmarks. Legacy project APIs still use Room main-thread compatibility; physical latency and larger datasets remain unverified.

## Evidence

Initial candidate: 46 JVM tests passed, no failures/errors/skips; summed test time 5.717 s; debug/test APK and lint passed; Gradle wall time 86 s. APK SHA-256 `7690795b6c8181e88eabbedde3d95cba8d64ac79e774fd2a4202bfc6074fd0aa`. See `stage2-initial-jvm-result.json`.

The device gate independently exercises actual native budget calculations with changed inputs, saved notes/search/tags, quiz scoring/reopen, routine completion, persisted timer deadline, scoped revisions, stale rejection, undo retaining newer records and old-project migration/recovery. Results are pending. Structural tests do not prove arbitrary requested features or AI-generated app correctness.

## Next exit requirement

Pass the manually composed native interactions and editing/data-retention checks before connecting AI. Verify native library/runner routing, legacy storage origins, full-screen launch and UI failures. Record source/APK hashes and limits. A35/Android 16 and the full layout matrix remain physical/release acceptance work.

### Final candidate trial

APK `18e34c3d7f836777a6a73792ac91d757429a487da73a22c1d7e14241cdbdca23`: 46 JVM checks passed; summed test time 5.698 s; APK/test APK/lint succeeded in 98 s. Device trial: 14/15 passed, no skips, 190.553 s test time; boot 70 s. The strengthened scoped-edit test could not find an off-screen lazy input before scrolling it into composition. The test helper now scrolls the lazy list to the matching field before editing it. Its rerun is pending. All legacy workflow, migration and real native inference checks in that trial passed. Emulator shutdown was requested by the script.

The temporary emulator's actual configuration for these Stage 2 trials is API 35, x86_64, 1080×2400 at density 420, 2 guest CPUs and 2048 MB RAM, pinned to two host cores. `BUILDAI_NATIVE_SIZE=true` retains that configuration. This is emulator evidence, not Galaxy A35 evidence.

### Local gate result

The corrected native suite passed all four checks with no skips, including the usable topic field, a new saved record and undo retaining both records while restoring the previous UI. See `stage2-edit-device.log` for exact timings. The unchanged production APK is the final candidate above; only the Android test helper changed. Combined with the 11/11 initial native/recovery trial and 14 passing regressions in the final candidate trial, **Stage 2 local gate passes**. The observed System UI dialog remains a host/emulator limitation; this does not establish the complete layout/lifecycle acceptance matrix or phone performance. Stage 3 can connect the planner and builder to this tested runtime.
