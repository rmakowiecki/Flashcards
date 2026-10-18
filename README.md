# Flashcards

An Android flashcard app with AI-assisted study session planning, spaced-repetition-influenced card selection, and voice input.

## Tech Stack

- Kotlin 2.3.0 · Jetpack Compose · Material 3
- Hilt · Firestore (offline-first) · Coroutines + Flow
- Retrofit + Kotlinx Serialization
- Min SDK 26 · Target SDK 36
- Website: Kotlin Multiplatform · Compose Multiplatform for the browser (wasmJs), sharing the app's design tokens

## Building

Open in Android Studio. Requires `local.properties` with Firebase config.

The website (`:web`, served by Firebase Hosting):

```shell
./gradlew :web:wasmJsBrowserDevelopmentRun   # local preview with live reload
./gradlew :web:wasmJsBrowserDistribution     # the deployable site, in web/build/dist/wasmJs/productionExecutable
firebase deploy --only hosting               # builds the site first (predeploy), then uploads it
```

## Documentation

| File | Contents |
|------|----------|
| `docs/design/` | Feature design docs (start at `docs/design/INDEX.md`) |
| `CONTEXT.md` | Domain vocabulary glossary |
| `AGENTS.md` | Code architecture, style guide, patterns |
| `TESTING.md` | Testing conventions |
| `docs/adr/` | Architecture decision records |
| `docs/user-flow/` | Per-screen navigation flows |
