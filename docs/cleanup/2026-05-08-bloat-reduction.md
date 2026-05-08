# Android App Bloat Reduction Track

Reviewer: @tai

## Goal

Make the Android app build against the current SDK source and remove duplicated profile/runtime assumptions.

## Findings

- The app uses a nested `octomil-android` submodule through `includeBuild`, while the sibling SDK checkout is newer.
- App dependency declarations still use older published coordinates, then substitute them to the nested SDK project.
- The nested SDK hard-requires runtime AARs that the current sibling SDK gates behind an opt-in property.
- App and SDK profile URL logic are duplicated, with inconsistent `/api/v1` handling.
- Generated contract code in the nested SDK is stale relative to the sibling SDK.
- Ignored Gradle build outputs account for most local app weight.

## Proposed Cleanup

- Point the app at the current SDK source or update the nested submodule to match the sibling SDK.
- Align dependency coordinates with the current published SDK coordinates.
- Normalize profile host/base API URL handling in one shared SDK utility.
- Regenerate or remove stale generated contract code in the nested SDK path.
- Keep Gradle build output out of review and index paths.

## Validation

```bash
git submodule status --recursive
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug --build-cache
./gradlew :app:dependencyInsight --dependency octomil --configuration debugRuntimeClasspath
rg -n 'AppProfile|api/v1|octomil-client|octomil-ui|includeBuild|contract-version' .
```
