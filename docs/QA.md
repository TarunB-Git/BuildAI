# BuildAI verification

Automated cloud responses are fixed fixtures. They verify app behavior and an interactive notes example, not the quality of a live model or arbitrary generated features. The live Google API check is a manual follow-up, as requested.

## Planning and editable apps, 2026-10-03

**BuildAI Dev 0.3.0** removes the automatic small-model list-app substitution. Custom local builds use the selected model to write controls and JavaScript in a shared mobile presentation shell. Quick templates are an explicit plan choice with declared features. Local chat remains in clarification until **Review my plan**, with an editable draft available if the model cannot finish planning JSON. My Apps separates completed apps from drafts; requirements/design edits and returning to chat retain the existing output.

- The debug APK, Android test APK, **29 JVM tests**, and debug lint passed. The final APK's v2 signature and 16 KB ZIP alignment passed; packaged ARM64 native libraries also passed their 16 KB LOAD-segment alignment check.
- The final targeted run passed **four checks with no skips in 47.645 seconds**: three WebView regressions for missing result elements, numeric values declared in another handler, and immediately called event callbacks; plus a fresh custom build using the pinned real Qwen3 0.6B Q4_0 fixture. The model's calculator passed **2 + 3 = 5, 7 + 4 = 11, and 1.5 + 2.25 = 3.75** before acceptance. The independent interaction assertion also entered numbers and tapped its button. This establishes a working simple custom calculator, not arbitrary app feature correctness or phone speed.
- Other targeted checks passed during this repair: production Settings/local chat with explicit plan review; requirements editing, returning from design to chat, retaining/opening old output and deleting an app; an offline WorkManager template build without a model or API key, even with cloud selected; and saved-app navigation, interactions, export intents and cold reopen. The template check smoke-tested all six choices and exercised calculator, expenses, habit history/persistence and timer controls. Cloud responses remain fixed fixtures.
- Earlier custom runs exposed actual model errors rather than a connection failure: invalid function syntax, missing output elements, stale/out-of-scope input values and incorrect callback registration. The builder now uses [llama.cpp constrained JSON output](https://github.com/ggml-org/llama.cpp/blob/master/grammars/README.md), WebView diagnostics, supported addition probes and checked local repairs for these known addition bugs. Up to two model repair attempts share the original **three-minute total deadline**. A failed build keeps previous output and never becomes an automatic list template. The real-model assertion was also corrected to decode WebView's JSON-escaped result before checking its displayed text.
- Tests ran after compilation, with a low-priority emulator capped at two host CPU cores, two guest CPUs and 3 GB guest RAM, plus boot/test deadlines and a hard shutdown timer. The full-resolution emulator's System UI intermittently raised an ANR dialog that stole focus. The navigation recheck passed at 540 × 1200 pixels / 210 dpi, preserving its logical layout size while reducing software rendering work. The emulator was stopped after testing; the final host sample had about 9.5 GB available RAM. Physical-phone resource measurements remain a manual follow-up.

Final APK SHA-256: `208169cb2c74f819f010fa76bd43b5041698b942437a2486a7108158a8e86ccb`.

On a phone, use the **BuildAI Dev 0.3.0** install: describe a small app, answer follow-up questions, choose Review my plan, edit the features, and build using **Build my idea**. Check the actual requested behavior. Then use My Apps → Edit app → Design → Edit idea and requirements, add a change, and rebuild; the previous app must remain available throughout. More complex custom code and data migrations still depend on model quality and manual review. Full Gemma 4 E2B inference, launcher pin confirmation and device-specific widget behavior remain unverified on a physical phone.

## Verified locally on 2026-09-28

- The previous 13 instrumented workflow tests passed on a clean Android 15 (API 35) Google APIs emulator. The latest clean-emulator suite passed 16 tests with no failures and two expected skips for the optional real-GGUF checks. It includes widget count updates and a cross-project write rejection. An earlier model-installed run passed all 16 tests with no skips, including real Qwen3 inference and the no-key local background build. The new tests exercise starter add/edit/delete, section isolation and persistence after reload, plus opening a saved app by its home-screen project intent. Launcher pin confirmation and widget rendering on a real phone still need a manual check.
- The 429 MB Qwen3 0.6B catalog file downloaded from its pinned official revision and matched its pinned SHA-256. It streamed tokens through the final Android CPU runtime. At that revision, a first attempt to make this compact model write custom HTML ran beyond five minutes, so that tier produced the labelled starter directly; the complete no-key WorkManager build passed in about one second on the emulator. An interrupted response resumed by HTTP Range. JVM tests cover resumed download and rejection of a wrong digest. The Gemma 4 E2B official GGUF URL responded with its expected 3,349,516,256-byte size, and its first 1 MiB contained the `gemma4` architecture identifier used by the text-chat formatter. Full Gemma 4 inference on a phone has not yet been tested.
- The debug build, 22 JVM tests and lint passed. The interactive notes fixture checks add, input validation, edit, delete, navigation and persistence. Sharing and browser intents are intercepted; no external app receives data.
- The generated-page check rejects empty/hidden-only output, script startup errors and failed local storage. The Result screen reports preview errors and offers reload. These are smoke checks; they do not prove that an arbitrary requested feature was implemented.
- Earlier release signing and package checks are recorded below. CI is configured but has not run from this workspace. Real-phone CPU speed, RAM, battery and thermal results are still needed.

## Baseline verification on 2026-09-27

- 19 JVM tests and 9 instrumented tests passed on Android 15. Debug lint passed with no errors; warnings concerned dependency/tooling versions, target-SDK guidance and the existing encrypted-settings API deprecations.
- A minified release APK and AAB built with a temporary test keystore. APK signature and AAB JAR verification passed. The build correctly rejected missing release credentials. The temporary key/password were removed; the installed release app on the original emulator was preserved.

## Repeat the checks

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest
```

Clone with submodules. Tests use the separate debug package `com.buildai.app.debug` and a local mock API. To run the optional real-model inference and local-build tests, place an instruction-tuned GGUF at `files/models/test.gguf` in that package's private storage before running the instrumented suite. Keep model files and credentials outside the repository.

## Manual live Google API check

1. Install a debug build on a real device or emulator. Enter a test Gemini API key in Settings.
2. Describe a small notes or habit-tracking app. Answer the planning questions, revise a requirement and confirm.
3. Select a layout, palette, app icon and tab icons; generate. Confirm the result visibly matches those choices.
4. Exercise every requested feature, including validation, editing/deletion and navigation. Close/reopen and check project design, output and data.
5. Regenerate after editing the design. A failed generation must leave the previous working output available. Check airplane mode, invalid key, rate-limit feedback, cancellation and retry.
6. Remove the key after testing. Do not commit it.

## Manual phone, voice and export checks

On a 64-bit Android phone, import an instruction-tuned GGUF in Settings and select local CPU chat. Check startup time, tokens per second, peak memory, heat, battery and stopping mid-reply. The emulator token test proves the native runtime can generate text; it is not a phone performance benchmark. GPU acceleration is not implemented. Custom local builds use the selected model to write functional HTML controls and JavaScript inside a shared presentation shell. Failure never silently substitutes a starter. An explicit quick-template choice builds its declared features without a model, API key or network constraint. Check the actual requested features; the starter only supplies lists, notes and checkboxes.

With a speech service, deny microphone permission and confirm typing still works; then dictate an idea and answer. The recognized text should remain in the editable field until Send is tapped. Test a missing recognizer and a recognition error. Automated tests do not verify transcription quality.

Share HTML to a chosen local destination and open it in a compatible browser. Handler availability varies by device. Exported HTML contains app code, but does not copy data from WebView localStorage into the recipient's storage.

## Current product limits

The output remains self-contained HTML opened in BuildAI, not a standalone APK. Project icons launch it; the widget displays offline-starter item/completion counts and opens it. The widget cannot edit items or implement arbitrary model-generated behavior. Built apps have a separate My Apps library with open, edit and delete actions. Requirements/design edits apply on rebuilding, while the previous saved output remains available. Preview pages have a narrow, write-only widget-count bridge; they have no file or network access. The runtime smoke check does not establish business behavior for arbitrary AI-generated apps. More feature modules, richer widget actions, optional APK output, GPU offload and deeper feature checks remain on the roadmap.


## Local runtime repair, 2026-10-02

Validation of the final **BuildAI Dev 0.2.1** APK:

- Debug APK, Android test APK, lint, and all 22 JVM tests passed. The packaged ARM64 and x86_64 native libraries include `libc++_shared.so`; the JNI library links to that shared runtime.
- All **four LocalInferenceTest checks passed, with no skips**, on the Android 15 x86_64 emulator in **74.283 seconds**. The real fixture was the catalog's pinned Qwen3 0.6B Q4_0 GGUF (429 MB), verified against its SHA-256. The arithmetic test required the reply to contain `4`; the planning/history test used the production system prompt and verified warm model reuse; the cancellation test stopped prompt processing and required the next reply to succeed. The malformed-GGUF test confirmed an actionable error without a process crash.
- The emulator used one guest CPU and 2 GB RAM, ran at low host priority on two cores, and had separate boot/test deadlines plus a hard shutdown timer. No build ran concurrently. Host monitoring showed about 8.6 GB of available RAM during inference and no swap activity in the sampled interval. The emulator was shut down immediately after the tests; no emulator remains running.
- These results establish the repaired smallest-model chat path on x86 Android. **Physical ARM phone inference and full Gemma 4 E2B inference remain unverified.** The Settings check is the next way to distinguish library startup, model loading, chat formatting, prompt processing, and reply generation on the affected phone.


The reported failure occurs after selecting/loading a model and before a usable chat reply. The original bridge used the limited `llama_chat_apply_template` formatter, an unoptimized native Debug build, and cancellation that only checked emitted tokens. The repair uses the pinned llama.cpp Jinja renderer with thinking disabled for these short app-planning replies. All packaged native libraries use one shared libc++ runtime, explicitly selected in Android Gradle so the runtime is packaged for each ABI. This avoids separate static C++ runtimes across libraries that exchange objects and exceptions. It preserves JNI callback names from R8, converts Java UTF-16 to real UTF-8, explicitly sets token positions/logits, and keeps one model session on a dedicated background thread. Native cancellation can interrupt loading and CPU decoding. A cancelled/failed decode clears its cache before reuse. Context overflow drops old chat turns while retaining the system prompt and latest user message; a single oversized message receives a clear error.

Settings now has **Test local model**. This runs a 32-token response with a 1,024-token context, shows loading/formatting/prompt-processing stages, supports Stop, and has a 90-second limit. Import/download success means the file was stored/verified; the test confirms actual inference. Use **BuildAI Dev**, version **0.2.1**, when checking the new debug APK, since it can coexist with an older release install.

The instrumented runtime suite now checks malformed-GGUF handling even without a real model. With `files/models/test.gguf`, it checks a complete short reply, the actual app-planning system prompt, a second reply with an oversized older history, warm model reuse, and cancellation during prompt processing followed by another reply. Optional tests must not be counted as passed when their fixture is absent.

Developer builds use one Gradle worker and one native compiler job. Native inference receives `-O3` even in Debug; reduced debug information limits disk use. During this repair, builds are additionally run at low scheduling priority on two CPU cores. Emulator and model testing must run after compilation, with bounded CPU/RAM and a boot deadline. A compiled APK and an x86 emulator check still do not establish ARM phone performance or full Gemma 4 inference.

### Additional recheck, 2026-10-02

The unchanged debug APK was freshly reinstalled and **six checks passed, with no skips, in 94.56 seconds**. This repeated all four native checks and added `LocalChatIntegrationTest`, which selects the actual Qwen3 fixture, runs the production Settings model check, sends an idea through `ConversationViewModel`, and requires a real local reply, a ready project plan at that revision, no failed messages, and an existing saved conversation. `LocalBuildTest` also passed through the background worker and output validator. For this sub-600 MB model, that build uses the explicitly labelled offline starter; it does **not** prove that the model can write arbitrary app code or implement arbitrary requested features.

The actual APK's v2 signature verified. Every packaged model-runtime shared-library dependency was present for both ARM64 and x86_64. APK ZIP alignment and all ARM64 native library LOAD-segment alignments passed the 16 KB checks. APK SHA-256: `7d870b4d671528053fa43c29a6b40ea9451c17df677fc96966a3be29c0b11650`.

The recheck again used a single-CPU, 2 GB emulator at low host priority, boot/test deadlines, and automatic shutdown, with no concurrent build. The emulator was stopped after completion. The new instrumented test compiled in a one-worker, two-core low-priority build. No physical phone was connected, so ARM inference, Gemma 4 E2B, and device-specific speed/memory/thermal behavior remain unverified.

References: [llama.cpp Android guide](https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md), [upstream Jinja chat API](https://github.com/ggml-org/llama.cpp/blob/master/common/chat.h), [Android C++ runtime guidance](https://developer.android.com/ndk/guides/cpp-support), [Android JNI keep rules](https://developer.android.com/topic/performance/app-optimization/keep-rule-examples), [Kotlin cancellable callbacks](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/suspend-cancellable-coroutine.html).
