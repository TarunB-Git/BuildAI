# Feature preservation contract

The availability column is the **pre-migration audit snapshot**. The Android implementation disposition below records what changed on 4 October 2026. Availability describes the audited Android code, not a promise that a model can reliably produce every requested app. **Current** = reachable behavior; **underlying** = helper/schema exists but not exposed; **new support** = redesign requires implementation; **future** = absent. Prototype interactions are simulations and never upgrade availability.

This matrix is the acceptance checklist for the Android migration. Every row must have an implemented destination or an explicit, accurate capability limitation before release. Grouped options retain their complete enumerations, not only the examples shown in the prototype.

## Library, conversation, and plan

| ID | Feature / current owner | Current availability | Proposed destination / preservation rule |
|---|---|---|---|
| F01 | List/open finished native and HTML apps; AppsScreen / navigation | Current | Apps → ready card → appropriate standalone runner. Preserve IDs and HTML origin. |
| F02 | List/resume incomplete projects, title/date/status | Current | Apps → Drafts → stable workspace. General CHAT entries excluded from drafts. |
| F03 | General chat history and new conversations | Current | Chats → conversation / New chat. Preserve stored messages. |
| F04 | Create-mode AI conversation | Current | Apps → Create app → project Chat. |
| F05 | Create/Chat entry selection | Current; switching inside conversation currently creates another conversation | Explicit Create app / New chat. No silent context replacement. |
| F06 | Prefill example prompts without submission | Current | Create/empty-chat examples below composer. |
| F07 | Find app/draft by title and clear query | Current | Apps Search / Clear. Purpose matching needs new query behavior. |
| F08 | Project deletion with app, records, history and job cleanup | Current | Named project More → Delete → explicit confirmation; deletion lifecycle retains cancellation protection. |
| F09 | Automatic project title | Current | Keep until user-defined title; persistent ownership/rename is new support. |
| F10 | App/model/backend status | Current | Project/status row and composer connection indicator; readiness accurately derived. |
| F11 | Multiline typed input, saved drafts | Current | Single shared composer, persists across navigation. |
| F12 | Voice permission, on-device speech in Local mode, cloud recognizer in Cloud mode | Current, subject to device availability | Composer voice action with boundary/availability explanation; retain all SpeechState outcomes. Never auto-send. |
| F13 | Stream assistant reply, selectable messages | Current | Chat response area; preserve reader position while streaming. |
| F14 | Stop reply; application-owned/background session | Current | Separate Stop action + Activity. Navigating away doesn't cancel. |
| F15 | Send/queue follow-ups while replying | Current | Composer + readable Queue disclosure. Queue reorder/remove requires new API. |
| F16 | Failed-message retry, error dismiss | Current | Attached failure + Retry/Dismiss, diagnostic Details. |
| F17 | Clarifying questions and remembered answers | Current | Chat questions; Plan → Details → remembered answers. Chips only with actual option metadata. |
| F18 | Draft plan with assumptions before all answers | Current | “Use sensible defaults” and visible/editable assumptions. |
| F19 | Native/legacy proposed plan review | Current | Plan tab, no auto navigation. Engine-specific details stay in shared shell. |
| F20 | Native feature title/workflow/check editor; add/remove (1–12 features) | Current | Plan → feature editor; retain check semantics, limits and validation. |
| F21 | Native screens and saved-data names, editable draft | Current | Plan → Screens & data row editor; preserve valid plan and invalid draft independently. |
| F22 | Assumptions and optional feature suggestions | Current | Assumptions in review; suggestions staged then Update plan. Staging commit behavior is new support. |
| F23 | Legacy name/purpose/audience/features/screens | Current; incomplete fields not separately persisted | Same Plan shell; audience Details; persist incomplete draft (new support). |
| F24 | Legacy Custom/Quick-template selection | Current | Explicit Use starter with feature preview and retained custom draft (retention is new support). |
| F25 | Six offline templates: notes, tasks, habits, expenses, calculator, timer | Current legacy build path | Create → Starters / Plan → Starter. Keep exact declared capabilities; do not imply native equivalents for every template. |
| F26 | Confirm/approve plan and explicit Build | Current | One workspace Build action after review; no redundant wizard confirmations. |
| F27 | Plan revisions by conversation | Current | Same composer with plan context; no separate revise input. |

## Appearance, guided creation, and execution

| ID | Feature / current owner | Current availability | Proposed destination / preservation rule |
|---|---|---|---|
| F28 | Five legacy layout templates: list, tabbed, cards, journal, dashboard | Current | Optional Appearance → Layout; not required before Build. |
| F29 | Eight legacy palettes: ocean, forest, sunset, berry, minimal, midnight, earth, rose | Current | Appearance → named Color choices, selected semantics. |
| F30 | 26 app icon choices, DesignDefaults | Current legacy design | Appearance → labelled Icon picker; chosen icon in library where persisted. |
| F31 | Tab names, add/remove, max five | Current legacy design | Appearance → Navigation; accessible row actions and natural labels. |
| F32 | Per-tab icon changes, DesignViewModel.updateTabIcon | Underlying callback never invoked by current TabEditor | Wire Appearance → tab icon picker; don't call it current reachable functionality. |
| F33 | Design reset and style skeleton | Current | Pending Appearance draft Reset/Cancel/Apply; correctly labelled Style preview. Pending persistence is new support. |
| F34 | Guided no-AI name, up to 12 fields: text/number/boolean/date | Current, entry only after a brief | Create/Plan → Build manually; field type and name editor, no AI prerequisite. Existing entry expansion requires routing work. |
| F35 | Guided save-local-records and optional search | Current | Manual builder → Records section; search only with records. |
| F36 | Guided optional add/subtract/multiply/divide; numeric operand selection/result label | Current | Manual builder → Calculation disclosure; retain validation and named operands. |
| F37 | Guided publish/validate/open; editing an existing app restricted | Current | Build manual app; existing ready project routes to scoped edit, never overwrites silently. Show provenance as guided. |
| F38 | Native build with feature verification and saved checkpoints | Current controlled flows; autonomous local model quality not established | Workspace/Activity work state; check scope truthful; retry existing saved checkpoint API. |
| F39 | Local custom/legacy build, deterministic template build, cloud/WorkManager generation | Current | Shared work strip/detail with engine adapters. Retain cancellation/checkpoint semantics per engine. |
| F40 | Continue work in background; stop/cancel/retry | Current | Natural Back + Activity; no blocking wizard required. Retry preserves existing engine behavior. |
| F41 | Actual progress stage, input/decode/check counts, diagnostics | Current | Outcome first, Details retains counts/errors/model provenance. Never invent ETA/percent. |
| F42 | Legacy regenerated output and edits to requirements/design | Current | Plan/Appearance → explicit Rebuild from plan. HTML history retention needed before promising safe version rollback. |
| F43 | Preview WebView reload after preview issue | Current legacy result | Preview error → Reload; preserve isolation/security/origin contract. |

## Native editing, runtime and history

| ID | Feature / current owner | Current availability | Proposed destination / preservation rule |
|---|---|---|---|
| F44 | Scoped native edit request saved with scope | Current | Workspace Chat → describe change. Saved follow-up editable during jobs; durable edit queue is new support. |
| F45 | One screen scope + selected components + allow new controls | Current | “Change on” screen and Advanced scope. Stable IDs remain internal. |
| F46 | Prepare edit, repair, checks, stop, review checkpoint | Current | Work strip → Review change; checks presented honestly, no physical acceptance claim. |
| F47 | Isolated edit preview copied from current app records | Current native edit | Preview → Unapplied change, writes stay in copied store. |
| F48 | Explicit apply + stale-base protection + current data preservation | Current | Review → Apply change; atomic base check retained. Discard proposal is new job transition. |
| F49 | Native standalone app opening/close; HTML standalone opening/close | Current | Apps Open / workspace Open app; creator toolbar minimized, Close retained. |
| F50 | Native screen navigation and Back to start | Current up to 12 screens | Adaptive labelled navigation with selected state and overflow; preserve every screen/action. |
| F51 | Text, number, boolean, date and reference inputs | Current native spec/runtime | Native affordances, field errors. Date picker and searchable reference picker are UI work; types/storage unchanged. |
| F52 | Insert/update/delete record actions; required/reference validation | Current | App form/row actions; same transactional executor. Recoverable delete Undo needs new API; otherwise confirm. |
| F53 | Saved record lists, search/filter/sort, reference relationships, display fields | Current | Useful summaries + Details; all configured values and query semantics retained. |
| F54 | Text and calculated summary components; expression operations | Current | App content/summary; preserve TEXT,NUMBER,BOOLEAN,INPUT,RECORD,COUNT,SUM,NOW,TODAY,DATE_ADD,DATE_DIFF,BEFORE,AFTER,ADD,SUBTRACT,MULTIPLY,DIVIDE,CONCAT,EQ,GT,CONTAINS,AND,IF. |
| F55 | Action navigation, assignment, conditions | Current | Runner controls; preserve NAVIGATE/ASSIGN and action condition semantics. |
| F56 | Timer start/stop/deadline and persisted state | Current | Timer component with truthful state/controls; no implied background reminder/alarm. |
| F57 | Quiz questions/options/answer/scoring and progress | Current | Quiz component with progress/result; restart only when supported. |
| F58 | Inputs/selected record/current screen persisted at runtime | Current | Survive edit/use/rotation/process recovery; same app identity and storage. |
| F59 | Native revisions/history/restore preserving present records | Current; dialog shows at most 12 | Workspace More → scrollable History → inspect → Restore. Full historical sandbox preview is new support. |
| F60 | App missing/definition failure recovery | Current basic error text | Clear recovery state, Return to Apps, project retained. |

## Connections, Activity, export, and launcher

| ID | Feature / current owner | Current availability | Proposed destination / preservation rule |
|---|---|---|---|
| F61 | Explicit selected Local/Cloud backend preference | Current, local blocked until file; removal auto-selects cloud | Settings → AI & privacy; select Local before setup; no implicit provider switch. Requires behavior fixes. |
| F62 | Model catalogue with five pinned publishers/digests, size/RAM/source/guidance | Current | Local AI → Browse models; retain all five and true evaluation guidance. No new quality recommendation. |
| F63 | Model download, byte progress, cancellation, verification and failure | Current | Managed operation Local AI/Activity; no partial file becomes selected. Selection after install explicit (new behavior). |
| F64 | GGUF import and validation/replacement; error state | Current | Local AI → Import file; Android picker, existing verified-file protections. |
| F65 | Short local reply test, timing/reply/failure, Stop test | Current | Local AI → Reply test; never described as builder qualification. |
| F66 | Remove local model | Current | Local AI → Remove, remain Local / Setup needed; apps unaffected. |
| F67 | API key edit, secure save, show/hide, remove confirmation, Google-key link | Current | Cloud AI setup; explicit Save key adjacent, corrected removal copy; no prototype secret retention. |
| F68 | Four selected cloud planning models; existing generation fallback | Current | Cloud AI → Planning model; Advanced generation details, actual job provenance retained. |
| F69 | Installed version | Current | Settings → About. Additional Help/Licences links only with real resources. |
| F70 | Application-owned jobs and WorkManager history/status/actions | Current | Activity → Attention / Running / Recent; never infer navigation from historical job kind. |
| F71 | Stop chat/local/native build/native edit; saved build retries | Current | Context-aware Activity/strip; edit retry uses saved request, not nonexistent edit checkpoint retry. |
| F72 | Saved-project migration errors, original file retention | Current | Apps attention banner + Activity details; keep originals. |
| F73 | Share generated HTML file via Android chooser | Current legacy result | More → Share HTML; explain records not included. |
| F74 | Export transcript/legacy plan helper, text share intent | Underlying; currently no reachable export action | Wire More → Export conversation; update helper deliberately for native plan if desired, never claim bundle export. |
| F75 | Open legacy output in external browser, no-handler error | Current | More → Open in browser; same Android intent and truthful storage limitation. |
| F76 | Pin legacy app shortcut; unsupported launcher handling | Current; opens project editor intent | More → Home screen → Shortcut; correct use deep link. Native support new. |
| F77 | Pin/bind/refresh legacy widget; supported summary and deleted-project state | Current legacy; same project intent issue | More → Home screen → Widget; actual supported preview/summary, use deep link. Native support new. |

## Explicit gaps — do not fake these controls

| Capability | Current state | Treatment |
|---|---|---|
| Portable .buildai import/export with data, history and native definitions | Future; not an existing importer | No active Import app / Share app bundle until format and verified recovery exist. HTML/transcript export preserved. |
| Native visual appearance persisted through AppSpec | New support | Add presentation schema/renderer/persistence/validation first. Legacy Appearance remains available. |
| Builder System/Light/Dark preference | New support | Current theme is light; no inert theme setting. Complete schemes and system bars required. |
| Native launcher widget and shortcut | New support | Implement runner intent, picker/actions, and optional native summary adapter. No universal widget suggestion today. |
| PDF ingestion, selected files in chat, notification scheduling, calendar connections | Future | No working-looking permission toggles or attachment buttons. Point-of-use access UI when real services exist. |
| Launcher/home surface replacement, workflow graph, multi-step phone automation | Future | Extend Plan and a contextual Access page later; don't add empty root tabs. |
| Historical HTML restore, isolated HTML sandbox, native historical preview | New support | Version/preview storage must exist before these promises appear. |
| Queue reorder/delete, edit queue, proposal discard | New support | Show current queue; add durable transitions before enabling richer controls. |

## Source coverage

Navigation: AppNavigation.kt. Builder/settings: HomeScreen, AppsScreen, ConversationScreen, NativePlanScreen, ProjectPlanScreen, DesignScreen, GuidedConfigurationScreen, GenerationScreen, NativeEditScreen, ResultScreen, ActivityScreen, SettingsScreen. Use: NativeRunnerActivity, LegacyRunnerActivity. Supporting contracts: WorkspaceComponents, ChatBubble, AppPreview, GeneratedAppWebView, ConversationViewModel/Session, HomeViewModel, DesignViewModel, SettingsViewModel, ModelCatalog/Store/DownloadWorker, SpeechManager, AppSpec/Validator/NativeAppRepository/RecordQueries/Expressions, ProjectBrief/GuidedComposer/NativeBuildJobs/NativeEditJobs, ConversationRepository, ProjectHomeActions/ProjectWidgetProvider/WidgetSummaryStore.

## Android implementation disposition · 4 October 2026

This table records the actual destinations after migration. The original columns above remain intact as audit evidence. Verification results and supported boundaries are in [ANDROID_IMPLEMENTATION.md](ANDROID_IMPLEMENTATION.md).

| Audit IDs | Actual destination and disposition |
|---|---|
| F01–F10 | Apps and Chats roots; ready app Open/Edit, drafts, direct Create/New chat, search/clear, delete confirmation, owned names, accurate setup status. Existing stored identities are retained. |
| F11–F18 | Chat and shared composer; drafts, voice, streaming, stop, existing follow-up queue disclosure, retry/dismiss, remembered answers, sensible defaults. Queue reorder/removal has no new API. |
| F19–F27 | Workspace Plan before build; native feature/screen/data editor and approval, staged suggestions, incomplete legacy draft storage, Custom/Offline starter, all six starters, direct Build, conversation revision. A running native app's Plan shows its actual current definition; changes go through scoped edits. |
| F28–F33 | Optional legacy Appearance retains five layouts, eight palettes, all app icons and tab names. Tab-icon selection is now wired. Save, reset, and unsaved-exit controls have explicit draft semantics. |
| F34–F37 | Direct Build manually entry with all field types, record/search options, arithmetic operands/result label, validation and publish. Existing ready apps use the edit journey. |
| F38–F43 | Existing native/local/WorkManager execution, checkpoints, background work, progress and stop/retry retained. Workspace work strip and Activity expose them. Legacy Plan/Appearance rebuild is explicit; existing HTML remains available after failed rebuild. Preview reload remains reachable. |
| F44–F48 | Workspace Chat with screen/control scope, saved request draft, stop, checks, review, isolated Preview, Apply and new Discard transition. No durable native-edit queue is claimed. |
| F49–F60 | Separate native/HTML runners and embedded previews; all native input/action/expression/record/query/timer/quiz behavior retained. Navigation and record actions wrap, date/reference assistance is added, deletion requires confirmation, no-match copy is distinct. Full stored revision list and confirmed restore remain available. |
| F61–F69 | Settings → AI & privacy → Local AI / Cloud AI; explicit provider choice, all five catalogue models, download/import/cancel/remove/reply test, secure key save/show/remove and unsaved-key exit choice, planning models/fallback details; Settings → About. Model download progress remains in Local AI; it is not represented as a new Activity job. |
| F70–F72 | Activity retains application-owned jobs, WorkManager builds, cancellation and supported retries, including migration warnings. A dedicated Apps migration-warning banner is not added. |
| F73–F77 | Workspace More retains HTML share/browser, adds reachable conversation export, and offers shortcut/widget actions. New native and legacy launcher intents open the appropriate runner. Widgets use supported legacy summaries or a launch affordance; no universal native summary is invented. |

New supported behavior also includes the three persistent builder/native themes requested by the user: Sea blue, Forest green, and Night gray. The proposed generic System/Light/Dark preference is replaced by these explicit choices. Native per-app styling in AppSpec, portable app bundles, historical HTML restore, historical native sandbox preview, richer queues, attachments, calendar integrations, and phone automation remain explicit unsupported extensions. No existing runtime capability was replaced with a simulated control.
