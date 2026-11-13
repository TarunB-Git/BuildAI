# A simpler BuildAI

Proposed interaction and implementation specification · 4 October 2026

## Product contract

BuildAI helps someone make and use small personal tools on their phone. General chat is useful, but it is a second destination rather than a second interpretation of every button. The builder should organize work around the person's app, not around a model session, job type, or output engine.

The existing three-tab requirement is superseded by this proposal in response to the user's request to reconsider tabs and settings. Native apps and saved HTML apps remain supported. Neither is silently converted into the other.

Five invariants:

1. A project always reopens at the same workspace address. Its title and selected local tab survive navigation and process recovery.
2. Typing, changing a view, or changing an appearance draft never applies an app update. Build and Apply change are explicit actions.
3. A working app stays available while a replacement or edit is prepared. Records belong to the app, independently of its definition/history.
4. The selected AI connection is explicit. Model removal or installation cannot silently select cloud/local, and running jobs retain their original connection snapshot.
5. All existing features remain reachable in an understandable place. Optional complexity moves behind disclosure; unsupported capabilities are not represented by working-looking controls.

## Information architecture

```text
BuildAI
├─ Apps [default root]
│  ├─ Ready apps → Open app [standalone use]
│  ├─ Drafts → Project workspace
│  ├─ Search / clear query
│  └─ Create app
│     ├─ Describe idea / dictate / example
│     ├─ Build manually
│     └─ Use starter [six existing templates, engine-aware]
├─ Chats [general conversations only]
│  ├─ Recent chats / New chat
│  └─ Conversation / saved composer / voice / retry / stop / queue
├─ Activity [top-bar action, accessible at all times]
│  ├─ Needs attention / review / setup
│  ├─ Running and queued
│  └─ Recent completed / failed / interrupted
├─ Settings [root gear; workspace More]
│  ├─ AI & privacy
│  │  ├─ Local AI → selected model / install / reply test / remove
│  │  └─ Cloud AI → key / planning model / connection details
│  └─ About → version [licences/help only with real destinations]
└─ Project workspace [one ID, independent of engine]
   ├─ Chat → requirements / questions / revisions / queued replies
   ├─ Plan → purpose / features / assumptions / screens & data
   │  ├─ Edit feature / planned checks
   │  ├─ Suggestions → stage → Update plan
   │  ├─ Details → audience / remembered answers
   │  └─ Appearance [only when implemented for that engine]
   ├─ Preview → runnable saved app or pending scoped edit
   ├─ Work strip → Activity detail / stop / retry
   └─ More → Rename / History / Share / Home screen / Rebuild / Delete
```

### Navigation decisions

**Two destinations, no central fake tab.** Create app is an action in Apps; New chat is an action in Chats. On phones show a labelled two-item navigation bar on root pages. Inside a project, show local Chat/Plan/Preview tabs and a labelled Back to Apps. On tablets use a root navigation rail plus a chat-and-output split when space permits. Never stack the global navigation bar, local tabs, and a second app navigation bar in one phone viewport.

**Activity remains a product feature.** The root top bar has a labelled Activity control with active/attention count. Workspace work strips open the same full-height Activity view filtered by project. Close returns to the originating screen without resetting scroll or draft. Zero-count Activity is still reachable. REVIEW, WAITING, failed/interrupted jobs requiring action count as attention; completed jobs do not. Runtime logs are not an equal root destination.

**No ambiguous mode switch in existing conversations.** A general chat stays a chat; a project conversation stays attached to its project. New chat and Create app start explicitly named new work. A future “Make an app from this chat” must create an explicit draft with copied user-reviewed context; it must not silently relabel the conversation. Existing Create/Chat mode selection remains available through these separate entry points.

**Back is deterministic.** Close keyboard → dismiss sheet/dialog → leave app/subscreen → previous workspace/root. Pressing Back never stops a job or clears a draft. Opening an app preserves the creator's workspace behind it. Launcher use links open the runner; edit links open the workspace. Missing/deleted projects get an explanation and Return to Apps.

## Screen specifications

### Apps

Top bar: BuildAI mark, Activity count, Settings. Heading Apps and one Create app action. Ready list first; Drafts second. Do not mix general conversations into drafts. Cards display chosen icon (initial fallback), name, one-line purpose, and status. Whole ready card/title opens the app; a labelled Edit action opens its workspace Chat. Draft cards open the workspace's remembered tab. More uses human-readable project name in accessibility labels.

Search uses a labelled expandable field for short lists and persistent field for long lists. Search matches display name and purpose; existing name search is preserved, purpose search is new. No results offers Clear search. Empty library offers Create app and Build manually. Interrupted migration offers a recoverable attention message without discarding original files. “Ready” must be based on a valid published output, not merely an optimistic model claim.

No mandatory folders, tags, accounts, favorites, or metrics. Library scale can justify those later; they are not simplification today. Delete is last in More, with a confirmation naming the app and data removed. Undo deletion is not offered until a real recoverable transaction exists.

### Create app and general chat

Create starts with “What would you like your app to do?” and an immediately available multiline composer. Examples prefill, never submit. Build manually bypasses AI setup and goes directly to guided configuration. Starters show exactly what the selected deterministic template implements, engine/availability included; they never quietly replace a custom plan.

AI setup is requested at the first send if needed. Keep the draft and show Local setup / Use cloud / Build manually. Saved apps and guided composition remain usable without any model. Do not force a key or multi-gigabyte download as a prerequisite to entering the product.

General Chats has a compact New chat action and conversation list. Its conversation uses the same accessible message/composer primitives as project Chat. Existing ordinary chat, streaming, history, saved drafts, stop, retry, and queue behavior remain.

### Project Chat

Header: Back to Apps, project title, status, More. Local tabs: Chat / Plan / Preview. Plan exists from project creation; incomplete state explains what is missing rather than showing an empty destination. Preview before first build says “Your app will appear here after a build” with Review plan. It must not display a fake working app.

Messages: readable assistant text, user messages in restrained tinted bubbles, selectable content, failure attached to the relevant request. Structured questions may offer answer chips only when their actual options exist; preserve free text. Keep a single composer for requirements and revision requests. Voice appends editable transcription and never auto-sends. The AI indicator beside input opens connection details, not a giant settings form.

Plan-ready response is a compact card: proposed app name, feature count, unresolved assumptions, Review plan. No automatic navigation. Questions and plan can coexist. “Use sensible defaults” explicitly leaves unresolved decisions as visible assumptions. Reply auto-follow only when the reader is near the bottom; otherwise Jump to latest. Queue count opens a readable pending-message list. Stop cancels the running reply; it is distinct from Send. No claim of queue editing/removal until its API exists.

### Plan

Header summary: app name, purpose, Draft/Saved status. Features appear as concise outcome rows, not always-open forms. Each feature has Edit and expandable How it works / How we'll check it. Title, workflow, all checks, Add feature, Remove feature remain available. Planned features are neutral; verified results show their real status and scope. A passing structure/probe check is not physical-device acceptance.

Assumptions sit adjacent to their affected features and in a reviewable summary. Suggested additions are staged choices; Update plan applies them together without a surprise navigation jump. Screen and data editors use individual labelled rows; add/edit/remove retain current backend limits and validation. Remembered answers and target audience are in Details. Manual guided configuration is reachable through a labelled Build manually action.

Bottom action area has one main Build action, enabled only when the current draft is valid and requirements permit it. Validation gives the field/action needed to continue. Do not hide the reason in a disabled button. Existing output changes the main action to Open app; editing that output proceeds through a pending edit, or explicit legacy rebuild. A running build shows real stage/feature completion; a separately saved next-version draft must not mutate its approved inputs.

### Appearance

Appearance is a sheet opened from Plan or Preview, with sections Layout / Color / Icon / Navigation. The legacy five layouts, eight named palettes, 26 icons and up to five tabs stay available. Tab names and icons have real labelled editors; remove targets are at least 48dp. A style skeleton is labelled Style preview, distinct from runnable Preview.

Pending appearance edits have Apply and Cancel. Reset affects only the draft. Applying to a built HTML app changes its next-build design configuration; it does not pretend to restyle the existing HTML instantly. The current native AppSpec has no DesignSpec presentation field: native appearance controls require renderer/schema/persistence work and stay absent until available. “Dark mode” for the whole builder is similarly new functionality, not a current setting disguised as parity.

### Scoped edits

Describe change in the project composer. For ready native apps attach “Change on: [screen]” with a clear selector. Existing component-level selection and Allow new controls remain under Advanced scope. Do not infer arbitrary global scope where the current edit engine accepts one screen. Friendly component labels map to stable internal IDs without showing JSON.

Preparing work uses the normal work strip; the user may write a follow-up draft. The current backend rejects concurrent edit jobs, so editable saved draft is the minimum release behavior; durable edit queue submission is a separate implementation item. Prepared proposal gets a Review change sheet: summary, readable changed elements/actions, real check results, Preview change, Apply change, Discard, Revise request. The last working app remains accessible throughout.

Preview is labelled Unapplied change and uses the existing isolated Room copy. Prototype inputs are sample data only; Android review uses a copy of current records. Apply rechecks base revision atomically. Stale result gives “Your app changed; prepare this edit again” and retains request/scope. Discard needs a real job transition and clears only the proposal. Applying never deletes/replaces current records.

### Preview and everyday app use

Preview is an output, not another wizard. It can be Current app or Unapplied change. Show the definition/version being tried. Native edit preview already isolates storage; first-build and HTML testing need an explicit sandbox strategy. Until implemented, label a real saved HTML preview as “Uses this app's saved data,” never imply isolation. Open app launches the standalone runner.

Runner has compact title and Close; creator commands move into More. Users should see their tool, not revision administration. Native navigation shows selected state, handles more than five screens through an overflow selector, and retains Back-to-start behavior. Inputs use proper keyboard/checkbox/date/reference affordances. Long labels wrap. Record summary prioritizes useful fields; Details retains all configured display values. Search/filter/sort semantics remain. Distinguish no data, no matches, permission/configuration failure, and invalid fields.

Actions are ordered by meaning: save/submit one primary, navigation/secondary outlined, delete destructive and protected. Derive what is safe from action kind; do not invent semantic icon or role from arbitrary text. Adding richer presentation metadata is separate schema work. Timers keep current persistence and exact deadline behavior; do not imply notifications/background alarm capability. Quizzes keep configured questions/options/scoring and persisted progress. Restart exists only when the definition/actions support it.

### Version history

History belongs primarily to the workspace More menu; runner More retains discoverability. Scrollable revisions show current marker, date, human summary and available check details. Native restore is an explicit action after inspection; current records are retained and the restored definition becomes a new revision. Restoring after a newer update needs fresh base validation. Compatible historical preview is additional runtime work: offer summary + explicit Restore until available. Legacy HTML history does not exist today; safe artifact version retention is prerequisite to promising restore there.

### Sharing and home screen

Workspace More groups available actions: Share → HTML file for legacy outputs; Export conversation → existing text helper, after wiring; Open in browser → legacy output; Add to home screen → shortcut/widget for supported legacy output. Existing Android chooser/launcher requests remain Android-owned actions. Explain what is included: HTML export is not app records, a transcript is not an installable app bundle.

The launcher intent must open app use, not the builder. Widget preview describes actual supported summary or plain launcher behavior. Native launcher shortcut/widget support is not currently reachable; add it deliberately through runner/deep-link/summary APIs. Do not put a universal Import button in the library until a valid portable bundle format and importer exist.

### Settings

Settings is a directory, not one long scroll. First item AI & privacy shows selected connection and ready/setup-needed. Local AI and Cloud AI both remain accessible for configuration regardless of active choice. About shows actual version. No profile avatar implying an account system.

Local AI detail: installed file/name/size, Use local AI, Replace model, Reply test, Remove model. Catalogue shows all five pinned models, download size, estimated RAM tier, publisher source and truthful reliability guidance from ModelCatalog. Do not label a failed native-builder trial as recommended. Import opens Android's picker and validates before replacement. Download shows bytes and total where known, cancel/failure/retry, verification, and completion. Keep old model until verified replacement. Installing does not select a backend without explicit action. Reply test is expressly a short installation/inference check, distinct from app-building validation. Test stop remains available.

Cloud AI detail: provider Gemini, where requests go, masked API key, show/hide, Save key adjacent, Remove key, planning-model selector. Existing generation fallback order is readable in Advanced details and actual job provenance. Keep key edits local until Save key. Removing a key disables cloud readiness but leaves local apps/local configuration available. No “Connected” badge without the corresponding condition; saved key alone means Configured, not Verified. Do not offer a new live connection test unless implemented/authorized.

Preferences like selected planning model persist immediately with feedback. Connection changes apply to future requests; running jobs keep their snapshots. Setup/removal failures retain configuration and actionable diagnostics. Deleting the local model changes readiness, never cloud selection. Secrets never appear in Activity, exported transcript, or prototype state.

### Activity

Sections Needs attention, Running, Recent. Show project, requested outcome, real stage, last update and actionable status. Open project always opens its stable workspace; review action opens the appropriate proposal. Stop is explicit; retries preserve saved checkpoints when existing engine supports it. Show model/backend snapshot in details so a later settings change is not misattributed. Native edit retry is prepare-again from saved request/scope; do not reuse a build checkpoint API.

Legacy WorkManager jobs keep their semantics and identifiers beneath the shared surface. Completed history remains available and scrollable; no destructive automatic pruning introduced by the redesign. Migration/import warnings surface in Apps and Activity. No bogus percentages or time remaining from token count.

## Visual and accessibility system

Use the previous blue as a functional accent, not a color on every container. White/light neutral base, high-contrast ink, subtle dividers, sparse surface cards for actual groups. Reserve colored status for meaning and pair with text. System typography avoids added font weight/downloads. Phone spacing 16dp outer / 12dp group / 8dp inline, with 48dp effective targets. Corners 12–16dp for inputs/groups; avoid repeated giant bubbles and heavy shadows. One visually primary action per decision.

Body/input 16sp; supporting copy 14sp; titles 20–24sp. Use sp/Material typography; avoid fixed-height text containers. User data and feature descriptions wrap naturally. More than two adjacent long actions stack. Status bars and navigation bars must follow chosen theme. The current light-only theme remains until a complete tested dark scheme exists.

TalkBack announces project/card purpose, selected tabs, check result versus planned check, icon choice, progress stage and errors. Use native Compose selected/toggle semantics, merge meaningful row content without duplicating child labels. Live status announcements are throttled, never per token. Dialogs have logical focus and return focus to their launcher. Color choices have names; disabled controls explain unmet prerequisites.

Phone widths 320/360/411dp, landscape, tablet ≥840dp, font scale 1.0/1.5/2.0, keyboard visible, long translated labels and RTL are release gates. Scroll content independently of action regions; action regions may grow with font scale. Plan forms and settings can always scroll to focused fields. Do not horizontally clip multi-screen navigation, result menus, guided field types, calculation choices, history, or record row actions.

## State and data contracts

| State | What users see | Allowed next actions | Preservation |
|---|---|---|---|
| No model/key | Setup needed near Send, no blocking library overlay | Set up local, Use cloud, Build manually, Open saved app | Keep composer draft and chosen privacy mode |
| Unfinished plan | Draft with missing field indication | Edit, ask, use defaults, manual path | Persist incomplete plan separately from valid approved plan |
| Reply running | Streaming response + separate Stop | Type/send queued reply, leave | Draft, queued inputs and job persist |
| Build queued/running | Work strip with actual stage | Open last output, write next draft, stop, leave | Immutable approved input snapshot |
| Build failed/interrupted/waiting | Needs attention + recovery action | Retry saved progress, revise, setup | Last output, records, checkpoint and diagnostic retained |
| Edit preparing | Requested change and scope | Type saved follow-up, stop, use current app | Current output/records untouched |
| Edit review | Unapplied change + review/check evidence | Preview, apply, revise, discard | Isolated preview, base revision preserved |
| Stale edit | App changed; regenerate proposal | Prepare again | Preserve request and scope; prevent stale commit |
| App ready | Open app and secondary Edit | Use, change, available share/history/launcher | Same app ID and record store |
| Model download/import/test | Managed operation with correct stage | Stop/cancel where supported, leave | Old verified model kept until replacement; no backend switch |
| Delete project | Named explicit irreversible confirmation | Delete or cancel | Transaction cancels dependent work then removes project/data; no fabricated undo |

The project presentation model should combine existing Conversation, native head/revision, and job states into explicit `ProjectWorkspaceState`. It must not serialize inferred navigation decisions back into project data. Route: `workspace/{projectId}?tab=chat|plan|preview`; root Apps/Chats; Activity and settings are separate routes/sheets. Keep engine adapters for current local builds, native builds, native edits and legacy workers; UI shouldn't branch into a new product for each.

Add persisted fields for user-owned title, selected workspace tab, incomplete legacy plan draft, pending appearance, and (if implemented) follow-up edit drafts. Existing approved model/input snapshots remain authoritative. Version/migration handling must read existing projects without rewriting origins or destructive conversion. Queue edits/removal, pending-review discard, native appearance, history preview, native launcher and HTML version retention require explicit backend work, not just clickable controls.

## Implementation sequence and acceptance

| Phase | Concrete work | Exit gate |
|---|---|---|
| 1. Trust and navigation | Fix mode-switch behavior, no auto cloud on model removal, key save semantics, incomplete draft persistence; add stable workspace state/route; Apps/Chats separation and Activity access. | Reopen chat/plan/preview consistently across all job kinds; Back never cancels; no prompt after removal goes to cloud; interrupted draft survives process death. |
| 2. Core workspace | Compact conversation, plan rows, no forced navigation, assumptions, staged suggestions, job strip, current/pending preview adapters; preserve every engine route. | Existing requirements/plan/build/edit/queue/retry tests pass against new semantics; current HTML origin/records unchanged; last native app works during builds. |
| 3. Settings and recovery | Focused connection pages, truthful model catalogue/test, managed-operation progress, action-oriented Activity. | Import/download/verify/failure/cancel/replace/remove/key flows preserve state; jobs use original selected model snapshot. |
| 4. Output/edit/history | Human scope/diff, native Apply/Discard, history list/explicit restore; overflow sharing/launcher; adaptive runtime inputs/actions/nav. | Apply and restore preserve current records; stale edits rejected; preview writes isolated; legacy chooser/browser/widget/shortcut reachable; launcher intent opens runner. |
| 5. Optional capabilities | Native presentation schema, portable bundles, broader phone integration only as independently delivered features. | Hide unavailable controls until actual persistence/runtime/permission/export tests pass. No feature counted as preserved merely because a prototype simulates it. |

Do not couple a UI migration to a new model, live provider call, or new phone integration. Use existing controlled adapters to verify behavior. Repair the four stale workflow tests around their actual product contracts before claiming the full device suite passes. Respect handoff: compilation and emulator runs are sequential, not concurrent.

Verification scenarios: launch/use ready app; create with typing/voice; incomplete plan and back; six starters; manual save/search/calculation; native build and interrupted checkpoint; HTML build/background/cancel/error; stream and queue while reading older messages; scoped edit/review/stale/discard; history restore with current data; legacy sharing/browser/widget/shortcut; model import/download/test/remove; cloud key save/hide/remove; missing/deleted project; process kill/rotation/large text/RTL. Preserve supported features with tests matched to behavior rather than old screen labels.

Usability targets to measure after implementation (not measured results): returning user opens an app in one tap from Apps; a novice locates Create, manual route and connection setup without explanation; no user loses context by switching views; a change's affected screen and apply boundary are clear before execution. Compare task completion, wrong-turn count and recovery success with the current version using real users. No artificial UX score or time-saved claim.

## Prototype scope

The companion prototype demonstrates Apps/Chats, project tabs, a staged plan, scoped edit review, a working sample expense form, separate app use, guided composition, Appearance for a legacy sample, focused settings, model operations, Activity and failure recovery. Its AI responses, verification results, background jobs, key/model changes, and launcher/share requests are simulations. The simulation controls sit outside the product surface. Advanced exhaustive feature and engine parity is specified in FEATURE_PARITY.md, not claimed from sample interaction alone.
