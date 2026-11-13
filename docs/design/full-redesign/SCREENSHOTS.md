# Actual Android screens

Captured on 4 October 2026 from the implemented app on an API 35 emulator at 540 × 1200 pixels (411 dp wide). The library retains existing development projects alongside disposable fixtures. No images were retouched.

## Three persistent themes

| Sea blue | Forest green | Night gray |
|---|---|---|
| ![Sea blue Apps](captures/sea-blue-apps.png) | ![Forest green Apps](captures/forest-green-apps.png) | ![Night gray Apps](captures/night-gray-apps.png) |

## Workspace, settings, and native runner

| Screen | Sea blue | Forest green | Night gray |
|---|---|---|---|
| Theme settings | [Open](captures/sea-blue-themes.png) | [Open](captures/forest-green-themes.png) | [Open](captures/night-gray-themes.png) |
| Scoped app change | [Open](captures/sea-blue-chat.png) | [Open](captures/forest-green-chat.png) | [Open](captures/night-gray-chat.png) |
| Current app definition | [Open](captures/sea-blue-plan.png) | [Open](captures/forest-green-plan.png) | [Open](captures/night-gray-plan.png) |
| Saved-data preview | [Open](captures/sea-blue-preview.png) | [Open](captures/forest-green-preview.png) | [Open](captures/night-gray-preview.png) |
| Standalone native app | [Open](captures/sea-blue-runner.png) | [Open](captures/forest-green-runner.png) | [Open](captures/night-gray-runner.png) |

## Review and accessibility

[Proposed-change preview](captures/proposed-change.png) shows a copied existing record and a new test-copy record. The test confirmed that the current app still had only its original record, then discarded the proposal.

[Preview at twice the font scale](captures/night-gray-double-text.png) shows readable wrapping text, reachable workspace navigation, and a scrollable native app. This fixture forces Night gray to inspect large-text rendering; theme persistence is tested separately.

See [the implementation record](ANDROID_IMPLEMENTATION.md) for capabilities and acceptance limits, and [the validation manifest](android-validation.json) for the exact APK and case results.
