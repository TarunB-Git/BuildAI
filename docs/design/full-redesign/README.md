# BuildAI: full product redesign

Design and Android implementation · 4 October 2026. See [the mobile implementation record](ANDROID_IMPLEMENTATION.md) for the shipped source changes, validation, and theme captures.

The previous redesign improved color, spacing, and message composition. It retained an information architecture that makes people manage the builder's internal state. This pass questions that architecture and covers the entire current product, including its older HTML path, native app runtime, guided builder, background work, voice, models, sharing, and launcher integrations.

Start with the [adversarial audit](AUDIT.md), then the [design specification](DESIGN_SPEC.md). The [feature preservation matrix](FEATURE_PARITY.md) maps every identified feature to its proposed location and flags missing runtime support. Open [the interactive prototype](prototype.html) to explore the proposal. It runs entirely locally with simulated AI, jobs, settings, and sample data; it does not operate the Android app or call any service.

The recommendation is **Apps + Chats**, with **Create app** as an action, **Activity** as an accessible work queue, and **Chat / Plan / Preview** inside a stable project workspace. Opening an app remains a separate full-screen experience. Settings becomes a small directory with focused Local AI and Cloud AI setup pages.

## Audit coverage and limits

Reviewed all 12 builder/settings screen composables, both standalone app activities, navigation, shared UI, view models, native specifications/actions/records/revisions, guided composition, model management, speech, storage/export, launcher actions, and the existing requirements/handoff. Re-examined both generated concept images and the prior emulator screenshots and validation record. Competitor research used current first-party documentation, not assumptions about their present UI.

The audit and prototype are design artifacts. Prototype verification is documented in VALIDATION.md. The subsequent mobile implementation and new Android verification are recorded in ANDROID_IMPLEMENTATION.md. Prior Android results remain in ../implemented/validation.json as historical evidence. No physical-device or live-model acceptance is implied by emulator verification.

The prototype illustrates the important journeys rather than rendering every catalogue entry or arbitrary generated app. The specification and parity matrix are the completeness contract. Features whose backend does not yet exist are explicitly deferred, including .buildai import/export, arbitrary native per-app appearance persistence, and future phone workflows. The implementation now includes shared native themes and runner intents for native launcher actions.
