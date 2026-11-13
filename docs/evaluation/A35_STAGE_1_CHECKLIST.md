# A35 Stage 1 acceptance — not yet executed

Target: Galaxy A35, 8 GB RAM, Android 16. No physical phone was connected during the recorded implementation. Emulator results do not establish ARM performance, Android 16 background behavior, thermal handling, or this phone's layouts.

Use the debug package `com.buildai.app.debug`, which can coexist with the release app. Install the debug/test APKs with `adb install -r`; never clear app data or import a model through a path that deletes existing models. Stop the emulator before phone checks. Live Gemini remains a separate manual check and is not required for controlled API tests.

Record the exact APK/source hashes, phone model, Android/security patch, selected model file hash and quantization, date, executed checks and failures. Do not put keys or private conversation text in the report.

1. Confirm local operation and on-device speech or the typing fallback. Deny the microphone permission and confirm typing still works.
2. Type on the Assistant entry screen, switch to Settings/Apps, and return. Verify the unsent text and selected Chat/Create mode remain. Repeat after a cold restart.
3. Start an ordinary local Chat reply, press Back, open another project and return. Confirm the same reply/job continues, queued input stays separate and no app plan appears in Chat.
4. Start a build, switch apps and turn the screen off. Return after completion; check the actual saved outcome. Stop explicitly and confirm the previous working artifact stays usable.
5. Interrupt only the inference process and confirm the UI remains alive, the partial reply/draft remains, and an explicit retry uses a new inference runtime. Do not describe this as restoration of native model memory.
6. Force-stop the debug app during actual work, reopen it, and confirm honest interrupted status and explicit retry. Force-stop is expected to stop execution.
7. Check small text and large text, keyboard visibility, portrait/landscape, gesture/button navigation and cutouts. Record the reported margin issue if reproduced. Preserve screenshots of failures.
8. Check thermal/memory pauses without deliberately overheating the phone. A paused job must retain input/checkpoints and offer retry. Controlled policy checks are not physical thermal benchmarks.
9. For the long-build regression use the controlled lifetime fixture; record its monotonic elapsed time. It checks continuation past 180 seconds, not model-written app correctness. A real-model run needs its own measured result.

The native runtime, arbitrary agent-generated feature correctness, widgets/routines/bundles and release usability targets belong to subsequent gates. Do not mark them passed from this checklist or legacy HTML smoke checks.
