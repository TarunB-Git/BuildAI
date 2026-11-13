# Adversarial UX audit

## Verdict

The useful functionality is trapped in several competing products: a chatbot, a native tool planner, an HTML wizard, a scoped patch editor, a technical job log, and a model control panel. Each makes sense to its implementer. Together they make the user reconstruct the app's architecture.

The visual pass made that inconsistency more attractive. It did not remove it. Large promotional headings, rounded containers, repeated AI badges, and optimistic check icons cannot compensate for unreliable navigation or surprising state changes.

The user should need to understand four things: **my apps, my chats, the app I am making, and where the assistant runs**. Everything else should appear when their task requires it.

The rationale column below is an inference about why the current design exists, not a claim about its author's intent. Severity describes user impact: P0 = trust/state risk, P1 = frequent task obstruction, P2 = comprehension/accessibility friction.

## Highest-priority failures

| Priority | Finding and evidence | Why it fails | Required decision |
|---|---|---|---|
| P0 | Conversation mode switch calls `startNewConversation`, ConversationViewModel.kt:68. | A control that looks like a view switch silently changes the conversation. Earlier content is saved, but the current context disappears. | Remove this switch from existing conversations. Use explicit New chat / Create app actions. |
| P0 | Removing the local model calls `selectChatBackend("cloud")`, SettingsViewModel.kt:185–192. | A storage-management action changes the privacy boundary and may make the next request use a configured cloud key. | Stay in Local AI, now “Setup needed.” Require an explicit Use cloud action. |
| P0 | ProjectPlanScreen.kt:42 saves only valid plans; invalid form drafts have no separate persisted representation. | Back can discard the incomplete edit the user most needs to retain. | Persist drafts independently from validation. Block build, never draft preservation. |
| P1 | `openProject` chooses a destination using native job kind, brief, generation, and confirmation, AppNavigation.kt:111–151. | A project has no stable address. A completed edit job can change where it reopens. | One workspace route by project ID; job state changes content, not route identity. |
| P1 | Settings changes backend/model immediately but Save Settings only saves the API key, SettingsViewModel.kt:160,196. | Users cannot infer what will persist or what Back means. | Immediate preferences; explicit Save key directly below that field; no global Save. |
| P1 | Native and HTML paths expose different plans, appearance, jobs, edit, preview, share, and history. | “Edit app” means different things without explanation. | Same workspace shell with explicit per-app capability availability. Preserve the legacy runtime and origin. |
| P1 | Native editor disables the request field during preparation, NativeEditScreen.kt:39. | A background operation takes away the user's ability to think and write. | Allow a saved follow-up draft; label submission “Send after this change.” Backend edit queuing needs implementation. |
| P1 | Scoped review prints changed IDs and raw check results, NativeEditScreen.kt:57–65. | The person must read implementation details to judge an update. | A human-readable change list, affected screen, preview, Apply, and Discard. Technical evidence behind Details. |
| P1 | Native plan suggestions checkbox immediately replans and navigates back, NativePlanScreen.kt:167. | A checkbox acts like a submit-and-navigate button. | Stage selections locally; explicit Update plan commits the batch. |
| P1 | Native runner history immediately restores a revision, NativeRunnerActivity.kt:186–196. | Browsing history changes the app without a preview or explanation. | Read-only history first; select, inspect, then Restore version. Current records remain. |
| P2 | Design preview is a static skeleton labelled Live Preview, DesignScreen.kt:209. | It suggests a working app before there is one. | “Style preview” for the skeleton; “Try app” only for runnable output. |

## Screen-by-screen interrogation

### Entry and global navigation

| Current choice | Likely rationale | Critical question | Redesign |
|---|---|---|---|
| Assistant is the launch destination. | Creation is the central acquisition task. | Why must a returning person pass through an invitation to build before using their own tools? | Apps opens by default; first run has one useful Create action and a no-AI route. |
| Apps / Assistant / Activity as equal tabs. | Mirror the three main implementation areas. | Is checking the job log as important as using an app or having a conversation? | Apps / Chats as destinations; Activity is a top-bar work button with active/attention count. Always reachable, including when empty. |
| Three ways to reach My Apps: bottom tab, recent-list link, empty-state link. | Discoverability. | Why spend scarce phone space repeating navigation? | One global destination; contextual Back within a project. |
| Create button opens Assistant, then user must choose/type again. | Reuse the home screen. | Why does an action land in another navigation hub? | Open a dedicated new-project composer directly. |
| Create/Chat switch on home and inside every conversation. | Shared input implementation. | Is it a mode, a destination, or a new conversation? | Separate task entry points; no ambiguous global mode switch. |
| AI badge and gear both open the same long settings screen. | Expose connection status and settings. | Why do two nearby controls do the same thing? | Small AI status opens focused connection details; gear exists on root pages. Workspace settings sit in More. |
| Global tabs disappear deep in a task. | Reduce chrome. | Where am I, and will Back cancel anything? | Stable project title, Back to Apps, local tabs, and persistent work status. Back closes a sheet before leaving. Jobs continue. |
| Back from Apps navigates Assistant. | Home-root back stack. | Why does the library behave like a child page? | Root destinations restore their scroll/query; Android Back exits at a root after overlays close. |

### Apps, drafts, and chats

| Current choice | Likely rationale | Critical question | Redesign |
|---|---|---|---|
| Large “My Apps” heading plus “Little tools. Made for your life.” | Personality. | Does this help the third daily visit? | Compact Apps title; one-line onboarding only when empty. |
| Promotional two-line Assistant hero and examples consume the first screen. | Inspiration. | Why is typing below a marketing message? | New-project screen starts with a direct question and composer. Three optional examples below, never above an active conversation. |
| Apps shows all unfinished conversations as drafts, including Chat mode. | Both share Conversation storage. | Why is a general question listed as an unfinished app? | Create projects belong in Apps → Drafts; ordinary chats belong in Chats. |
| Home separately lists unbuilt conversations; built project chats disappear from recents. | Avoid duplicate finished apps. | Does finishing a build mean its conversation is no longer useful? | Project Chat stays in its workspace; Chats is for independent conversations. No project conversation becomes unreachable. |
| Finished app cards have two equally large Open/Edit buttons. | Distinguish usage and creation. | Can the most common action be clearer without hiding editing? | Card/title opens the app; labelled Edit secondary action and More retain management. Show this convention in first-use text. |
| Status depends largely on file/head existence. | Cheap derived state. | Is having an output file the same as having a checked, runnable app? | Ready, Needs review, Update running, or Needs attention from explicit project/work state. Keep last working app usable. |
| Card avatars use derived initials/colors, not chosen app icons. | Avoid icon ambiguity and add visual differentiation. | Why choose an app icon if the library ignores it? | Use configured icon when supported; initials as fallback; no decorative random color encoding. |
| Search appears for any nonempty project collection. | Easy future scaling. | Does one item need a large search field? | Expandable Search button at small counts; persistent search once list is long. Retain find/clear capability at all counts. |
| Titles are repeatedly derived from messages/drafts. | Automate naming. | Can a user rely on a name once they have chosen it? | Add explicit title ownership; automatic title only until first user rename. Model plan name and project display name follow a documented sync rule. |
| Delete is the only overflow action. | Minimum management feature set. | How do I rename, revisit history, or find sharing? | Context menu: Rename, Activity, available Share/Home-screen, Delete; contextual only. |
| Delete confirmation differs between screens. | Independent implementations. | Does deleting a conversation also delete records? | Same named-project confirmation enumerates app, conversation, records, history; active work cancelled by the delete transaction contract. |

### Conversation and planning

| Current choice | Likely rationale | Critical question | Redesign |
|---|---|---|---|
| Header says BuildAI and phase, never the project title. | Assistant branding and debugging progress. | How can I tell which app I'm changing? | Project title, subtle Draft/Ready status; AI connection by composer. |
| Completion forces navigation out of chat. | Advance wizard. | Why should a reply move my screen? | Plan-ready card and Plan tab badge; stay until user opens Plan. |
| Native plan card repeats all features; legacy card adds type/complexity chips. | Confidence and confirmation. | Are these labels useful decisions or model metadata? | Short proposed outcome, feature count, assumptions count, Review plan. Detail in Plan. |
| “Looks good!” means confirm then design; native Review leads directly to Build. | Evolved backends. | How many approvals are there and what do they authorize? | Review is navigation; Build is the one explicit execution action. Appearance optional. |
| Revise opens a second single-line composer and hides the main one. | Special plan revision path. | Why make users learn two ways to talk? | One multiline composer; revision intent attached as context. |
| Autoscroll occurs on loading and message-count changes. | Keep newest reply visible. | Should the reader lose their position while rereading? | Follow newest only near bottom; otherwise show Jump to latest. |
| Queue is a count, not inspectable content. | Small surface. | What exactly will be sent next? | Expandable queued requests with text/order. Removal/reordering is new queue API work, not a promised existing feature. |
| Voice is always offered before availability is understood. | Consistent composer. | Does Local AI imply on-device dictation availability? | Keep voice, explain first-use processing boundary, show Listening/Processing/transcript, retain typed text on denial. Device speech availability is distinct from model setup. |
| Configure without AI appears only after a brief exists. | Escape from an AI planning session. | Why must a no-AI path start with AI? | Build manually directly from Create; still accessible from Plan. |
| “Prepare a plan with assumptions” reads like a fallback/debug action. | Avoid endless clarifications. | Can the user move forward without knowing planner mechanics? | “Use sensible defaults” beside the outstanding question; assumptions remain visible/editable. |

### Plan and appearance

| Current choice | Likely rationale | Critical question | Redesign |
|---|---|---|---|
| Name is both a large title and always-open form field. | Editable plan. | Does every review need to look like a form? | Title with Rename; inline read-only overview and Edit controls. |
| All feature workflow/checks are generic textareas, checks split by newline. | Direct serialization. | Must ordinary users author a test specification? | Outcome summary + editable “How it works”; “How we'll check it” in disclosure with individual rows. Preserve every check. |
| Planned features have green/blue check symbols. | Reassurance. | Have these actually passed verification? | Neutral planned icon; checked only after actual results, and explain which kind of check. |
| Screens/data are newline fields. | Flexible simple editor. | Is removing a line a safe way to remove a screen? | Named list rows, Add/Edit/Remove with drafts and validation; stable IDs internal. |
| Remembered answers, assumptions, suggestions, privacy card all stack. | Expose complete context. | Which one matters before Build? | Assumptions adjacent to summary; suggestions explicit staged action; answers/details lower disclosure; concise data/AI boundary. |
| Plans lock completely while a build runs. | Immutable execution inputs. | Must planning the next version stop too? | Running job has immutable snapshot; next-plan draft can be saved but never mutates current job. Backend versioning must enforce this. |
| Legacy form requires Who it's for even for a personal tool. | Prompt context. | Should most people have to type “me”? | Default You; audience under Details. |
| Quick template rewrites features/screens when save occurs. | Deterministic implementation. | Why does a rendering choice silently replace requirements? | “Use this starter” explicitly previews implemented features; preserve original custom draft for switch-back. |
| Design “Step 2 of 3” is compulsory before legacy build. | Conventional wizard. | Is color a required decision before getting a useful tool? | Optional Appearance sheet from Plan/Preview; defaults let the user build immediately. |
| Five layouts, eight palettes, 26 icons all fill the screen. | Complete customization. | Is this a choice hierarchy or a catalogue dump? | Appearance sections: layout, color, icon, navigation; current selection visible, browse on demand. Retain all options. |
| Palette circles are color-only; selected white outlines; icon buttons are glyph-only. | Compactness. | Can TalkBack or a person with color-vision differences know what is selected? | Named choices with selected semantics, outlined check indicator, labels on icon picker; 48dp targets. |
| Tab icon callback exists but TabEditor never calls it. | Partially implemented editor. | Is this feature actually available? | Restore a labelled tab icon picker; mark as currently underlying-only in parity matrix. |
| Tab remove is 32dp; “6 characters max looks best.” | Fit a fragile tab layout. | Why ask the user to compensate for the UI? | 48dp remove targets, natural labels, adaptive navigation, up to five legacy tabs preserved. |
| Reset design immediately changes saved appearance. | Convenient defaults. | Can a stray tap overwrite all deliberate choices? | Reset pending draft, preview result, explicit Apply; cancel retains saved appearance. |

### Building, editing, and version history

| Current choice | Likely rationale | Critical question | Redesign |
|---|---|---|---|
| Blocking generation screen with indeterminate progress versus native plan footer. | Different execution services. | Why does the same task have different escape routes? | Workspace work strip and Activity detail; build stage and completed feature count where available; no invented percent/time estimate. |
| Continue in background is a required-looking button. | Explain durable execution. | Why make background operation a special transition? | It already continues; Back is normal. “You can leave; work continues” once in job detail. |
| Retry labels expose checkpoints/native/connected/legacy. | Accurate internal mechanics. | Which retry is the user supposed to choose? | Retry with plain explanation of saved progress and changed inputs; advanced job details retain technical evidence. |
| Raw exception occupies whole error screen. | Diagnostic usefulness. | What can the person do about it? | Outcome, retained work, next action; exact diagnostic under Details. Inline error tied to failed request/feature. |
| Native scope shows every screen and component checkbox before the request. | Guard against broad changes. | Must a novice understand component kinds to edit a label? | Request first; “Change on: Expenses” defaults to current screen; advanced selected-controls disclosure. Keep bounded scope and add-controls permission. |
| No explicit Discard review action. | Review stays pending for later. | How do I say this proposal is wrong? | Discard proposal (new job transition), Revise request, Preview, Apply change. Existing app unchanged until Apply. |
| Regenerate is a prominent one-tap result action. | Quick iteration. | Why is replacing the entire output easier than reviewing a scoped change? | “Rebuild from plan” in More; describe implications, review current plan, Build. HTML version retention requires backend work. |
| History exposed at all times inside app use. | Easy undo. | Why is creator administration part of every everyday task? | Workspace More → Version history; runner More → Edit / History remain available but secondary. |
| History takes first 12 rows in an unscrollable dialog. | Small implementation. | Why should older working versions become unreachable? | Scrollable history with current marker, human change summary, time, and restoration action. |

### Running and sharing an app

| Current choice | Likely rationale | Critical question | Redesign |
|---|---|---|---|
| Native screens all sit as equal text buttons in a row. | Generic renderer. | Can up to 12 screens fit or indicate selection? | 2–5 main destinations in adaptive nav, More for overflow; single screen has no nav. Navigation actions still support deeper screens. |
| Every action is a full-width primary button. | Uniform generic component. | Which action is most important, and which is dangerous? | Derive safe presentation hierarchy from action kind plus future presentation metadata; one primary per form, delete secondary destructive. |
| Record rows list every “Label: value” pair. | Schema-neutral rendering. | Does a useful tool have to look like database inspection? | Primary value, secondary metadata, labelled row actions; Details preserves every display field. |
| No results and no saved items share empty copy. | One list fallback. | Should the user add a record or clear a filter? | Distinct empty/error/no-match states, clear-query recovery. |
| Date input is raw YYYY-MM-DD; references are long menus. | Minimal native typed inputs. | Why expose storage format and an unsearchable record list? | Native date picker; searchable labelled reference picker; validation by field. Preserve date/reference semantics. |
| General errors say Enter valid inputs. | Expression failure abstraction. | Which field is invalid? | Field-linked errors where derivable; preserve technical detail without pretending the renderer knows unknown constraints. |
| Delete record acts immediately. | Simple action executor. | Can a mistaken tap be recovered? | Prefer Undo if deletion can be restored safely; otherwise concise confirmation. Transaction/API work is needed for reliable Undo. |
| Timer/quiz state persists but presentation is uniform. | Data-first renderer. | Can the UI show what actually happened? | Timer Running/Paused state and labelled controls; quiz progress, selected feedback, result, restart only if supported by definition. No promised background alarm. |
| Result toolbar packs Share, Browser, Shortcut, Widget, Regenerate together. | Expose all output actions. | Will this fit a phone with larger text? | Open app primary; More → Share / Open in browser / Add to home screen / Rebuild. Context capability-gated. |
| Shortcuts/widget open MainActivity's project route. | Reuse existing deep link. | Why does Tap to open your app open its builder? | Separate use/edit deep-link intents; launcher opens runner, Edit opens workspace. Retain old project links as edit links. |
| Native app lacks legacy export/widget actions. | Separate runtime generation. | Can the common shell hide this limitation without misleading people? | Show only actionable supported features; explanatory availability in project Details. Do not imply native bundle export already exists. |
| Remove key dialog says key required to use BuildAI. | Cloud-era text. | Why imply local apps/local AI stop working? | “Cloud replies will need a key again. Local AI and saved apps remain available.” |

### Settings and Activity

| Current choice | Likely rationale | Critical question | Redesign |
|---|---|---|---|
| Settings shows cloud key/model before local setup, even when Local AI chosen. | Older cloud-first screen evolved. | Why show irrelevant secrets and choices? | AI & privacy directory, conditional focused setup pages; both configurations remain accessible. |
| Local radio disabled without a model. | Prevent failed requests. | Why make the desired privacy option impossible to select before setup? | Select Local AI → Setup needed, with download/import/manual options. Send checks readiness, library use does not. |
| Cloud selection doesn't require key setup; Save is far below. | Separate preferences. | How will I know whether cloud is ready? | Explicit configured/not configured status; Save key adjacent; Use cloud is intentional, connection test only if implemented. |
| Model IDs, GGUF/CPU/RAM guidance dominate. | Necessary operational constraints. | What must a beginner understand before making an app? | Plain local/cloud choice first; model catalogue in Models; required size/RAM/reliability guidance retained. GGUF appears on Import file, not the root title. |
| Test local model success is green but only tests a short reply. | Check installation. | Can users mistake this for builder reliability? | “Reply test passed”; build capability status separate and grounded in actual evaluation. No recommended-best-model claim. |
| Download selected/success implicitly activates Local AI. | Convenient setup completion. | Does installing a file justify changing current work's backend? | Installation and active connection are separate; choose Use this model explicitly, snapshot current jobs. |
| Activity calls itself Work in progress but includes all historical work. | Observe raw job tables. | Is this a task surface or an accumulating debug log? | Needs attention, Running, Recent; completed collapsible and dated. Queue record retention unchanged until explicit product policy exists. |
| Job displays tokens/characters and raw stage. | Trust through evidence. | Does token count answer when my tool will be ready? | User outcome/stage first; counts in Details; real feature completion if known. Never fabricated ETA. |
| REVIEW/WAITING jobs aren't treated like active attention needs. | Status strings passed through. | What must I do to finish? | Needs review / Waiting for setup as action-oriented states; badge includes them. |
| Migration warnings at top of Activity only. | Convenient catch-all error surface. | Why hide a missing-app warning in a job log? | Library attention banner with Details; retain source files and recovery information. |

## Applying the two concept images critically

The MVP image's strongest ideas are a recognizable app library, conversational clarification, an understandable editable plan, and a useful full-screen tool. Keep those. Reject the illustration's unconditional Import action because a portable app importer does not exist. Reject its universal widget suggestion until that app type has real widget support. Do not preserve Apps/Assistant/Activity simply because they are drawn in the image.

The future image usefully connects intent, workflow steps, selected files, and explicit phone access. Keep its contextual approach as a future extension of the same workspace. Its launcher replacement, PDF ingestion, reminders, calendar connection, workflow graph, and granular permission switches are not current capabilities. Showing those switches today would be false functionality. Reserve a project Access page when those features exist; use actual Android permission requests at the point of use. No empty Integrations tab.

Both images use decorative color, status cards, and large areas of explanatory text. A production interface should be quieter: visible content first, one primary action, a small number of useful states. The app is a tool shelf and a place to build; it need not look like an onboarding poster on every visit.

## Research and how it changes the proposal

Research checked 4 October 2026. These are design inferences, not evidence that copying a competitor improves this app.

- [ChatGPT Projects and chats](https://learn.chatgpt.com/docs/projects?surface=app) describes durable project context and related conversations. Adopt stable project identity; do not introduce folders/workspaces/accounts without a demonstrated need.
- [Claude artifacts](https://support.claude.com/en/articles/17153992-what-are-artifacts-and-how-do-i-use-them) supports iterating on a persistent output and viewing/exporting it separately. Adopt conversation + artifact continuity; BuildAI's native/local data and export limits remain distinct.
- [Lovable Chat mode](https://docs.lovable.dev/features/chat-mode) distinguishes discussion, planning, and implementation within shared project context. Adopt a clear boundary between talking and applying changes. Avoid importing three global modes into a small mobile app.
- [Lovable preview editing](https://docs.lovable.dev/features/preview-toolbar) attaches selected elements to edit requests. Adopt friendly screen/control scope selection. Pointer inspection and annotations require new support and are not proposed as current mobile controls.
- [v0 Design mode](https://v0.app/docs/design-mode) holds visual changes pending and supports before/after review. Adopt draft appearance plus explicit Apply; its current design mode is unavailable on mobile viewports, so its desktop layout is not a phone blueprint.
- [Android accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults) calls for adequate minimum touch size, semantic labels and selected state. Apply 48dp effective targets and explicit semantics to every editor and runtime control. A good-looking 32dp delete icon still fails the task.

## What this audit cannot prove

No user interviews or timed usability trials were conducted. The proposed two destinations are a defensible product hypothesis, not a measured winner. Source inspection identifies behavior and risk but does not prove a particular device clips a layout; the narrow rows listed above are required test cases. Prototype success cannot establish Android accessibility, process recovery, actual model quality, or data-preserving legacy regeneration. Those need the implementation gates in DESIGN_SPEC.md.
