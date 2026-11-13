# Release preparation

The release build shrinks code/resources and requires an explicit signing key. No private key, password or production credential belongs in Git. `.gitignore` excludes local configuration, keystores and `.env` files.

Use JDK 17 and the Gradle wrapper. Before preparing a release, run:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest
```

Provide these environment variables through your own local secret manager or a protected CI secret store:

| Variable | Value |
|---|---|
| `BUILDAI_KEYSTORE_PATH` | Absolute path to the release keystore |
| `BUILDAI_KEYSTORE_PASSWORD` | Keystore password |
| `BUILDAI_KEY_ALIAS` | Signing key alias |
| `BUILDAI_KEY_PASSWORD` | Signing key password |

All four are required together. Signing configuration does not print their values. Release packaging fails when they are absent; a debug signing key is never silently used.

```sh
./gradlew :app:bundleRelease
# Or, for direct distribution:
./gradlew :app:assembleRelease
```

Verify the resulting signature with the Android SDK's `apksigner verify --verbose` for an APK, or `jarsigner -verify` for an AAB. Preserve your real release key securely; replacing it affects whether users can install updates. The test-only signing verification uses a temporary keystore, not a distribution identity.

Before publishing: increment `versionCode`, set `versionName`, complete the manual live/voice checks in QA.md, test the minified build, prepare screenshots and privacy/data disclosures, and check the distribution channel's current target-SDK requirements. The checked-in target SDK is 35 and may need updating for the intended release channel. This repository does not publish automatically.
