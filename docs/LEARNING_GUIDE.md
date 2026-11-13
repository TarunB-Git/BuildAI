# Learning alongside the implementation

Each stage should produce a small demonstration you can explain without reading code.

| Stage | Idea in plain language | Exercise / evidence |
|---|---|---|
| 0 | A requirement says what a person can do; a test checks observable behavior. | Trace Back cancelling a reply to its owner and a regression scenario. Explain why a green HTML opening test cannot prove a budget calculation. |
| 1 | A screen is temporary. A job is stored work with a separate lifetime. A service keeps user-started work active; Binder passes requests between processes. | Send then Back; reopen and see the same job. Kill the inference process and show honest retry. Explain progress versus a repeated status message. |
| 2 | AppSpec describes an app; native components implement it. Records are separate from the definition. A revision saves behavior. | Compose two different tools from shared parts. Edit one feature and undo while retaining today's records. |
| 3 | An agent keeps requirements, chooses supported tools, makes a change, checks it and repairs bounded failures. It does more than one text reply. | Show remembered answers, a supported plan, an independent failing check and the corrected feature. Explain which steps are deterministic. |
| 4 | Android widgets and reminders have their own lifetimes and permission rules. Sharing definitions is different from sharing records or credentials. | Use a widget action with the app closed; demonstrate a denied permission; import a bundle and review capabilities. |
| 5 | Measurements support claims; they do not establish universal correctness. Quantized models still consume memory and CPU. | Explain all three A35 trials, failures, peak memory, latency and usability results. Describe what is supported and what remains uncertain. |

CV evidence should link to the exact source/version, reproducible test procedure, recordings and measured outcomes. Until measured, describe work as implementation or experiments rather than verified phone performance. Local inference means the phone runs the model; it does not guarantee a particular speed, memory fit or successful app creation.

## Reading the Stage 3 evidence

A green test can mean different things. A controlled reply test checks that BuildAI saves jobs, passes repair context, enforces a scope, runs actions and preserves records. It does not show that a local model can produce that reply. An actual-model test checks the pinned GGUF output and then changes inputs in the native app independently. A test with manually supplied capability choices isolates configuration; it does not prove that the AI selected those choices.

The current failures are useful CV evidence when described accurately: the 1.7B model emitted valid selection JSON but chose a timer for a calculator. Typed validation rejected the proposed app, the previous revision remained intact, and the repair attempts were recorded. The claim is that the system detected and contained a measured failure. Successful autonomous creation remains an open gate.

A plan edit is saved work rather than a new build. One approval freezes its current version. A build based on an older version cannot silently overwrite newer requirements or app revisions. A scoped edit similarly prepares a proposal; preview writes to scratch storage, and Apply checks the base revision again.

Repeated text uses space in a model's context window even when it adds no new information. The planning candidate shares identical quotes while keeping the original IDs and sequence; it leaves unique requirements intact. The measured synthetic example reduced 203,080 characters to 8,327. That is a text-size measurement, not a claim that every long conversation fits or that the phone runs faster.

A useful verification check can also prove that an action was refused. For example, a conditional save should reject zero and then save three. The verifier must distinguish that expected refusal from a crash or malformed number, and verify that rejection did not change records. Those negative checks passed their device regression on the latest candidate; actual-model use of this check contract remains unverified.
