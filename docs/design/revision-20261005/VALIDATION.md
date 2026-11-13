# Verification · 5 October 2026

The debug APK contains the redesigned Android app. Generated screen images are illustrative concepts, rather than screenshots of the APK.

- Bounded offline Gradle build: `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` succeeded.
- Unit tests: 83 passed, zero failures, errors or skips. These include the provider streaming protocols, cancellation, configured fallback behaviour, Markdown source copying and existing builder/runtime checks.
- Android lint: zero errors, 34 warnings. Existing warnings remain; this is not a claim of a warning-free project.
- Palette checks: body/background contrast 9.84–16.03; secondary text/surface 5.37–8.91; button label/primary 5.55–10.54. Values are recorded in `contrast.json`; translucent surfaces and disabled controls require visual review separately.
- API 35 emulator: two cores, 2 GB RAM, 411 dp width, animations enabled. Final delivery APK: all 25 UI/workflow checks passed with animations enabled (335.7 seconds). This covers pin persistence, separate libraries, root return and recreation, model inventory/provenance, the >10-model catalogue, provider accordions/fallback, keyboard and HTML copying, editable/steerable queues, background build/retry, appearance, versions, proposed previews, offline tools and scroll restoration.
- The earlier broader run also passed all 15 native/runtime/relationship/recovery checks outside the UI suite. Those components were unchanged by the final navigation/text fixes. Regression and visual checks corrected appearance return, rebuild error visibility, root stack restoration, consumed opening intents and inherited dark text. The final theme test checks the resolved heading colour in all three palettes, rather than only configuration values.
- All 31 concept image paths resolve in both galleries. Browser inspection verified filtering and full-size viewing. Lazy-loaded images load when their cards are exposed.

The APK is 46,943,981 bytes (44.8 MiB), SHA-256 `ebee5a864c3aa958a8f916b03b383e723e899938efc15f0570645f9f9b1ab098`. Debug-only compressed DEX packaging makes an in-place emulator update fit the existing low-storage device. Release packaging is unchanged. No emulator wipe or app uninstall was used; existing models were retained.

The [actual Android capture gallery](device/gallery.html) contains settled frames from the delivery APK. They contain disposable test examples. The large-text check uses 2× system font scale.

Paid provider calls and a physical phone were not tested. Provider requests were checked against local protocol fixtures. External model discovery requires a user-granted folder on Android. The JNI runtime supports text inference; image/audio inference and projector integration are not implemented. Chat Markdown covers headings, emphasis, lists, links and fenced code; tables, math and syntax highlighting are not provided.

The existing Git staging index was preserved. No commits, staging, resets or data wipes were performed.

Validation logs: `/tmp/buildai-revision-validation-delivery.log`, `/tmp/buildai-revision-device7/tests.log`, `/tmp/buildai-revision-device4/tests.log` (broader runtime checks).
