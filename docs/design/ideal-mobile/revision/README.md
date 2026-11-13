# BuildAI mobile revision · 5 October 2026

Open [the 31-screen gallery](gallery.html). Images are generated visual concepts; [Actual Android captures](device/gallery.html) are listed separately. The concepts illustrate the direction, rather than claim pixel-for-pixel correspondence with the APK. Exact generation prompts and later correction prompts are retained alongside the images.

The application now opens on Chat, with Create for unfinished app conversations and Apps for completed native/web apps. Finished apps can be pinned from their overflow menu. Project work advances from requirements to appearance to build; earlier phases remain reachable through Project options. Appearance opened from the menu saves without starting another build.

Sea blue uses warm sand, teal and seafoam with translucent gradient navigation and composer surfaces. Forest green uses pale and deep greens. Night gray remains available. These are lightweight Compose material treatments, not Apple's proprietary Liquid Glass rendering. Standard Material icons replace decorative text symbols in the app chrome.

Chat keeps the connection caption in the header and uses a compact input with one to four visible lines. Assistant messages support headings, emphasis, lists, links and selectable text. Fenced code has a language label, horizontal scrolling and exact-source copying. Generated HTML stays source text in chat. The small latest-message button stays in the message area. Queued requests can be edited, removed or sent immediately to interrupt and steer a running reply.

Settings has Gemini, OpenRouter, OpenAI and Claude accordions; Gemini starts expanded and only one provider is open at a time. Each provider has its own encrypted key, editable model ID and optional fallback IDs. Generation fallback defaults on, and only uses models configured for the selected provider. Provider streaming implementations follow the official APIs. No paid request or real provider key was used for verification.

Local model installation retains earlier models. Previously imported app-owned GGUF files are rediscovered, with User or Downloaded provenance. Published catalogue files link to their actual pinned publisher source. Android folder access allows discovery in other user-selected folders; the app cannot silently scan all other apps' private storage. Up to ten models use a dropdown; larger inventories use a separate grouped screen. Downloads remain hash-verified. Model information describes text capabilities and memory guidance.

The current JNI runtime supports text only. Gemma's downloaded text weights can be used, but downloading a projector alone cannot enable image/audio input. The UI does not offer misleading multimodal controls.

Manual tools and offline starter entry points are hidden unless Show offline tools is enabled in Advanced. An AI planning response can explicitly recommend one of the existing starters when it covers the requested workflows; accepting that option keeps the custom plan available. Saved apps, native records, history, exports, voice, background work, editing and recovery remain available.

Research and the requested feature map are in [REQUEST.md](REQUEST.md). Implementation validation is recorded in [VALIDATION.md](VALIDATION.md).
