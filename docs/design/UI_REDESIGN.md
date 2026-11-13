# BuildAI interface redesign

Implemented in Kotlin and Jetpack Compose, 4 October 2026. The two existing concept boards informed the direction; the images in `implemented/` are captures of the actual Android UI with controlled sample projects.

## Research and decisions

| Reference | Relevant pattern | Adaptation in BuildAI |
| --- | --- | --- |
| [ChatGPT projects and chats](https://learn.chatgpt.com/docs/projects?surface=app) | Organize ongoing conversations around work users want to revisit. | Keep recent conversations in Assistant and unfinished projects in the Apps library; reopening uses the existing persisted project. |
| [Claude artifacts](https://support.claude.com/en/articles/17153992-what-are-artifacts-and-how-do-i-use-them) | Give substantial reusable outputs their own space alongside the conversation. | Separate app use from the builder. Provide clear Open and Edit actions, with a readable plan between conversation and implementation. |
| [Lovable chat mode](https://docs.lovable.dev/features/chat-mode) | Discuss and plan an app before implementing it. | Preserve distinct Create and Chat modes. Questions and answers lead to an editable plan and a single Build action. |
| [Lovable preview toolbar](https://docs.lovable.dev/features/preview-toolbar) | Direct manipulation helps with small, precise changes; follow-up input can continue during work. | Expose feature editors at the point of review. Keep text entry active while replies run, with separate Send and Stop controls. |
| [v0 design mode](https://v0.app/docs/design-mode) | Visual editing and conversation can complement each other. | Offer direct plan editors and a return to conversation. Adapt to a phone with one focused screen at a time. |
| [Android Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults) | Minimum 48 dp interactive targets, labeled actions and native semantics. | Use Material controls, 48 dp icon buttons, selected-mode semantics, text scaling, and accessible action descriptions. |

These are design interpretations of the referenced workflows, not claims that BuildAI supports every feature of those products.

The MVP board supplied white surfaces, blue primary actions, three main destinations, compact app cards, an editable plan, and independent app execution. The future board supplied visible connection status and understandable phone access. Existing runtime capabilities determine which controls appear.

## Implemented experience

- **Apps:** a visible Create action, searchable app and draft library, separate ready/in-progress groups, clear Open and Edit controls, and overflow deletion with confirmation.
- **Assistant:** a persistent Create/Chat switch, concise welcome, example prompts that fill the draft for review, recent conversations, and an integrated text/voice composer fixed above navigation.
- **Conversation:** selectable 16 sp text, lightweight assistant messages, soft blue user messages, separate stop and send actions, an explicit queue action during replies, and a summarized plan card.
- **Plan:** a readable feature summary; per-feature editors revealed on demand; separate screen/data editor; collapsible remembered answers; visible assumptions and optional suggestions; a fixed Build action and return to chat.
- **Activity:** readable job types and statuses, real progress, error styling, wrapped actions, an empty state, and Settings access.
- **Settings:** connection selection near the top, plain language local/cloud explanations, and an expandable model catalogue.
- **Design system:** consistent paper surfaces, blue actions, quiet outlines, readable secondary text, shared radii, and dark system-bar icons for the light builder UI. Existing design, generation, and result screens inherit the palette and typography.

## Interaction details

Typing, speech, draft persistence, queueing, cancellation, plan validation, app storage, inference, and build ownership continue to use the existing application logic. Speech results enter the draft; example prompts are never sent automatically. Delete remains a confirmed action. Plans keep validation errors and incomplete edits; Build is disabled until the saved draft is valid. App execution uses the existing standalone runner.

## Validation

Build and Android lint passed. All 74 JVM tests passed. All 21 selected device tests passed across the final run and the corrected UI-selector rerun. This includes 2 new UI tests, native plan editing, legacy app editing, structured Create planning, durable draft/queue behavior, the native runtime, and controlled native build/edit workflows.

The initial 21-case run passed 19 cases and identified two test-selector issues: a saved chat title is derived from its first message after a draft update, and lazy items need scrolling before their semantics exist. Those selectors were corrected, and all 3 UI/plan cases passed on rerun. Logs are saved as `implemented/device-results-initial.log` and `implemented/device-results-recheck.log`. Screenshots were visually inspected after the passing run; the normal-size plan capture was refreshed after closing its editor to avoid an initial-frame capture of the outgoing screen.

The initial broader device run also exposed four failing tests in the older `WorkflowTest` suite. Several fixtures send plain-text questions and the old `<plan>` format to the current structured native Create planner; others retain embedded-preview/navigation assumptions. These failures are recorded in `implemented/legacy-workflow-results.log`. The current planning/runtime tests are used alongside the new UI checks; the old suite is not claimed as passing.

Screenshot samples demonstrate the builder interface. Sample app titles and plans do not establish functional acceptance of every requested generated-app feature. The large-text check uses a 411 dp phone viewport at 150% text size. Measured palette contrast: main text 15.88:1, secondary text on cards 5.30:1, and white button text on blue 5.38:1. Physical-device usability, TalkBack traversal, and broader tablet layouts still need manual evaluation. This implementation establishes one consistent light theme; a dark-theme preference remains future work.

## Actual screenshots

- [Assistant](implemented/assistant.png)
- [App library](implemented/apps.png)
- [Conversation](implemented/chat.png)
- [Plan](implemented/plan.png)
- [Plan at 150% text size](implemented/plan-large-text.png)
- [Activity](implemented/activity.png)
- [Settings](implemented/settings.png)
