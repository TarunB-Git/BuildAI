# Requirements and gate evidence

The complete approved plan is preserved in [SYSTEM_REQUIREMENTS.md](SYSTEM_REQUIREMENTS.md). This companion assigns system IDs without rewriting that source.

| ID | Requirement / source section | Gate and evidence |
|---|---|---|
| SYS-01 | Versioned native AppSpec and registered components, §3A | Stage 2: manually composed native examples and independent interactions; HTML smoke checks do not count. |
| SYS-02 | Transactional projects/messages/jobs/records, §3B | Stage 1: Room import, stable IDs, retained originals, drafts, partial replies and queue tests. Stage 2 extends records/revisions. |
| SYS-03 | Jobs outlive screens; separate lifecycle/status, §3B,D / UX-08 | Stage 1: send then Back, clear ViewModelStore, switch projects, reopen, and inspect saved terminal state. |
| SYS-04 | Isolated local inference process and Binder, §3D | Stage 1: native PID differs from UI, kill only native process, observe interrupted result, explicitly retry; A35 confirmation pending. |
| SYS-05 | Genuine progress and inactivity watchdog, §3D / UX-09 | Stage 1: native load/prefill/decode counters; virtual-clock stall tests; controlled pipeline exceeds 180 seconds without a total deadline. |
| SYS-06 | One model instance; fair interactive priority; two cloud calls, §3D | Stage 1: queue fairness/cancellation unit tests; concurrent request integration and phone resource checks remain necessary. |
| SYS-07 | Thermal/memory pauses and honest interruption, §3D | Stage 1: resource checks retain input/partial data and require retry; physical-device/Android 16 tests pending. |
| SYS-08 | Explicit requirements, questions, suggestions and readiness, §2 / UX-01–05 | Stage 3: no phrase/turn-count unlock; changed/remembered answers; independent acceptance checks per feature. |
| SYS-09 | Incremental bounded tools/repairs/checkpoints, §3C | Stage 3: at most two repairs; inspect actual interactions; publish only verified revisions. |
| SYS-10 | Scoped stale-checked edits and retained records, §3G / UX-11,12 | Stage 2: base revision checks, additive migrations, unrelated behavior preserved, undo retaining current records. |
| SYS-11 | Full-screen safe app runner, §2 / UX-10 | Stage 2: layout/keyboard/cutout/large-text matrix; reproduce reported margin symptom separately. |
| SYS-12 | Native shortcuts/widgets/reminders/routines, §3E | Stage 4: real Android actions with runner closed; reboot/time-zone/permission reconciliation; no continuously loaded model. |
| SYS-13 | Local default and explicit external access, §3F | Stage 1: local preference default/on-device speech; Stage 4: strict-private network checks, explicit connector/project permissions. |
| SYS-14 | Open inspectable bundles and capability review, §3F | Stage 4: `.buildai` round trips, unknown-version rejection, no executable code/credentials/private grants, records opt-in. |
| SYS-15 | Preserved legacy data and native foundation, §3A,B / §4 | Every stage: retained source/index, original legacy JSON/IDs/origins, real GGUF/cancellation tests. Conversion requires explicit rebuild. |
| SYS-16 | Physical evaluation and usability, §4 | Stage 5: 20 held-out tasks ×3 on A35; all regressions; zero data loss/substitution; ≥90% supported success; p95 target; 4/5 beginners. |
| SYS-17 | Licensing, CV evidence and reproducible releases, §5 | Apache 2.0 and NOTICE added; model/dependency notices separate. Stage 5 signs/releases only after checks, with source/checksums/results and beginner demonstrations. |
| SYS-18 | Bounded development resources, §5 | All stages: one worker, 1536/768 MB heaps, two JVM processors, single native compilation, build/test lock, one bounded emulator and explicit shutdown. |

`docs/evaluation/BASELINE.md` maps prototype failures B01–B09 to these requirements. Targets are not current success claims. [IMPLEMENTATION_HANDOFF.md](IMPLEMENTATION_HANDOFF.md) records the current stage and outstanding checks.
