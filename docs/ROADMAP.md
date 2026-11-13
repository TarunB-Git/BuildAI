# BuildAI product roadmap

> Historical prototype roadmap. The approved [systems requirements](SYSTEM_REQUIREMENTS.md) and [implementation handoff](IMPLEMENTATION_HANDOFF.md) supersede this roadmap. Claims below require fresh validation and do not establish redesign acceptance.

The product goal is to let a nontechnical Android user describe an idea, refine it by text or voice, and get a useful, customizable local app for free. Offline web apps are an acceptable main format when they have reliable features, a launcher icon and widget access. A green HTML smoke test does not establish that the requested features exist.

## Status on 2026-10-03

Step 1 has editable chat and speech drafts, Stop/revise, project-scoped background builds, parallel jobs and emulator coverage. Step 2 has five revision-pinned GGUF downloads with SHA-256 verification for suggested 4/6/8/12 GB phones, import, optimized CPU streaming and the pinned llama.cpp Jinja renderer for GGUF chat templates, including Gemma 4. Step 3 can build self-contained offline web apps with a local model and add per-project launcher icons or an Android widget that shows the offline starter’s item/completion counts and opens the project. Custom builds use every selected model; a failed custom build never silently substitutes a list starter. Six explicit quick templates cover notes, tasks, habits, expenses, calculators and timers. Step 4 checks page structure, script startup, storage and visible content, with an interaction check for supported two-number addition plans and up to two bounded local repair attempts; arbitrary feature correctness and real-phone resource behavior still need verification. There is no exported standalone APK or GPU acceleration yet. Local chat stays in clarification until explicit plan review. Requirements can be edited, design can return to the same chat, and My Apps separates completed apps from drafts. A draft-plan fallback is available only after explicit review; previous output survives revisions.

## 1. Keep the conversation and build usable

- Let people type a draft while the assistant replies. Speech recognition inserts editable draft text and never sends without a deliberate tap.
- Save drafts and incoming replies across navigation and process restart. Offer an explicit stop action, then queue or revise further input without losing context.
- Run builds as durable project-scoped jobs. Back leaves a build running; Cancel actually stops it. Different projects can build concurrently, and Home shows their state.
- Verify cold restart, cancellation, retry and concurrent builds on a device.

## 2. Add actual on-device inference

- Import a user-selected quantized GGUF model into app-private storage, validate it, and use a pinned llama.cpp Android runtime. Provide CPU inference first, then GPU offload only on tested supported hardware with a CPU fallback and clear device/memory requirements.
- Stream response tokens, expose reasoning separately when a model emits it, and make generation stoppable. Never silently send a locally selected model's prompt to a cloud API.
- Keep model downloads/imports, selection and backend settings persistent. Benchmark load time, tokens per second, memory and thermal behavior on representative phones.
- TinyAI is a useful reference for the user experience and its MediaPipe/LiteRT-LM engines. Its README explicitly says it does not support GGUF, so GGUF requires a separate llama.cpp integration. Do not copy TinyAI code without reviewing its license and APIs.

## 3. Grow from useful offline apps to standalone Android packages

- Keep the current offline HTML path for quick local apps and add a validated, structured feature specification. Expand the working starter into explicit modules for notes, tasks, trackers and simple dashboards rather than claiming every requested feature from a prompt.
- Extend the current project shortcut/count widget into richer native actions and user-configurable widget content with declared permissions and generated tests.
- Keep app-like installation and launching simple. A standalone Android project/APK is an optional later export, not a prerequisite for useful local apps. A preview must render actual features; a blank or failed build must be reported, never shown as completed.
- Let users adjust the layout, color, icons, feature configuration and widgets before rebuilding. Preserve project revisions and a working previous artifact.

## 4. Use tools to verify and optimize

- Give the builder explicit plan, generate, compile, test, inspect and repair steps with bounded retries and visible progress. Require objective checks before claiming a feature works.
- Optimize for quick first results and phone limits: choose appropriate model/runtime, estimate resource use, use cached templates, measure build and inference time, and avoid unnecessary cloud calls.
- Exercise generated apps on real devices, including widget behavior, accessibility, process death, battery/thermal limits and install/update flows. Keep manual model and distribution checks where automation cannot prove behavior.

## Reference material

- [TinyAI](https://github.com/YashBhadange2006/TinyAI) for model management, local inference UX, streaming and sessions.
- [llama.cpp Android guide](https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md) for GGUF on-device runtime and NDK integration.
- [Android WorkManager long-running workers](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running) for durable build jobs.
