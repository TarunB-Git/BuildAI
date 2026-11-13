# Prototype verification

4 October 2026 · Browser prototype only

114 checks passed in headless Chromium, with no browser JavaScript errors. The final results are in [preview/validation.json](preview/validation.json). The runnable checks are [validate-prototype.cjs](validate-prototype.cjs).

## Interaction coverage

- Library filtering/clear; separate general chat creation and reply.
- Unsent project draft retained through Chat/Plan navigation; another project’s request cannot enter the wrong queue. Settings returns to the original workspace.
- Scoped change preparation, writing/queuing a follow-up during work, and explicit review.
- Isolated preview record writes; explicit Apply retains original records.
- Standalone sample app saves a record; explicit version restoration retains current records.
- Model removal leaves Local AI selected; installation leaves an already selected cloud connection unchanged.
- Explicit cloud selection and demo key save; actual entered key text discarded.
- All five catalogue models present; all four legacy output/launcher action flows present as labelled simulations.
- Prompt examples prefill without sending; creation preserves one project identity.
- Incomplete feature draft saved while Build is blocked; appearance Cancel versus Apply.
- Guided app draft retained when adding an input; manual draft review.
- Failure and retry retain the same project and publish a simulated result.

## Responsive checks

Eleven screen states at 320px, 390px and 1440px, at standard and 150% text size; review sheets checked at all six combinations. No document-level horizontal overflow. Review-sheet keyboard focus stays inside the dialog, and Escape closes it.

An initial 320px/150% run found overflow in the Preview tab label. Content-sized tab allocation fixed it. A settings return-route bug was also corrected during review. Screenshots were inspected for library, plan, desktop split workspace and AI settings; clean screenshots were regenerated after separating the unrelated job state from workspace captures.

These checks are not a screen-reader audit, a complete keyboard accessibility certification, Android font-scale tests, or physical-device acceptance. The prototype is in-memory and resets when reloaded. Only one simulated operation is modeled at a time. Sample app output and model/check results do not demonstrate model reliability or arbitrary generated app parity. Android 200% text, RTL, landscape, process recovery, all runtime capabilities and all 77 matrix rows remain implementation acceptance gates.

## Local preview

The prototype is self-contained and can be opened directly as a local HTML file. During this task a server bound to `127.0.0.1:4173` serves the design directory, and the app browser has been opened at `/prototype.html`. It is not published externally. If that process has ended, open the file or start a local static server from this directory.

To rerun with the bundled Playwright dependency on this machine:

```bash
NODE_PATH=/home/tar_guest/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules node docs/design/full-redesign/validate-prototype.cjs
```

Chromium requires execution outside this filesystem sandbox on this host. The initial sandbox launch failed with an operating-system permission error; the allowed local browser check then ran successfully. No service calls, downloads, messages, keys, Android builds, or emulator runs were part of validation.

## Screens

The PNGs show the browser proposal, not implemented Android screens. Phone captures are full-page inspections; sticky navigation/action regions appear at the capture viewport position. Use the interactive prototype to evaluate their scrolling behavior.

| Surface | Phone | Desktop |
|---|---|---|
| Apps | [apps-390.png](preview/apps-390.png) | [apps-1440.png](preview/apps-1440.png) |
| Chat | [chat-390.png](preview/chat-390.png) | — |
| Plan | [plan-390.png](preview/plan-390.png) | [plan-1440.png](preview/plan-1440.png) |
| Preview | [preview-390.png](preview/preview-390.png) | — |
| Create | [create-390.png](preview/create-390.png) | — |
| General chats | [chats-390.png](preview/chats-390.png) | — |
| Manual builder | [guided-390.png](preview/guided-390.png) | — |
| AI & privacy | [settings-390.png](preview/settings-390.png) | — |
| Local AI | [local-ai-390.png](preview/local-ai-390.png) | — |
| Cloud AI | [cloud-ai-390.png](preview/cloud-ai-390.png) | — |
| Activity | [activity-390.png](preview/activity-390.png) | — |
