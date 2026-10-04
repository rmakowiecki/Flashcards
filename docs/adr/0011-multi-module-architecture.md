# Multi-module architecture: feature modules with shared core

## Decision
The project is structured as a multi-module Gradle build with one module per user flow (`:feature:*`), a thin `:app` shell, and three shared core modules. Each feature declares its own typed entry-point route; `:app`'s `NavGraph` is the sole wiring point — features never reference each other's routes or classes.

## Context
Portfolio project designed to demonstrate enterprise-grade Android development. A single-module structure works at the current scale but does not showcase the architectural skills expected in professional Android teams. Modularization is the de facto standard in large Android codebases (see Google's Now in Android). This ADR establishes the target architecture before the codebase grows further. Migration is incremental — not a one-shot rewrite.

## Module structure

| Module | Contents | Depends on |
|---|---|---|
| `:app` | `MainActivity`, `NavGraph`, splash/startup, `@HiltAndroidApp` | all modules |
| `:feature:auth` | `LoginScreen` + VM | `:core:domain`, `:core:ui` |
| `:feature:home` | `HomeScreen` + VM | `:core:domain`, `:core:ui` |
| `:feature:browse` | `BrowseScreen` (bottom-nav "Study" tab root; category/subcategory browse + search), `CategoryDetailsScreen`, `SubcategoryDetailsScreen` + VMs | `:core:domain`, `:core:ui` |
| `:feature:study` | `PreviewStudySessionScreen`, `StudySessionScreen`, `SessionSummaryScreen` + VMs | `:core:domain`, `:core:ui` |
| `:feature:progress` | `ProgressScreen` + VM | `:core:domain`, `:core:ui` |
| `:feature:settings` | `SettingsScreen` + VM | `:core:domain`, `:core:ui` |
| `:feature:account` | `AccountScreen` + VM (reached from Settings through a navigation callback wired in `:app`) | `:core:domain`, `:core:ui` |
| `:core:domain` | models, repository interfaces, use cases — pure Kotlin, no Android deps | nothing |
| `:core:data` | repository impls, DTOs, mappers, Firestore sources, Hilt bindings | `:core:domain` |
| `:core:ui` | theme, colors, typography, spacing tokens, shared composables (buttons, cards, chips, etc.) | `:core:domain` |

**Strict rule:** no `:feature:*` may depend on another `:feature:*`. No `:core:*` may depend on `:app` or any `:feature:*`. Features import `:core:ui` for theme and shared composables.

## Build convention plugins

A `build-logic/` included build holds precompiled script plugins:

| Plugin | Used by |
|---|---|
| `android-feature.gradle.kts` | all `:feature:*` modules |
| `android-core-kotlin.gradle.kts` | `:core:domain` (pure Kotlin, no Android) |
| `android-core-data.gradle.kts` | `:core:data` |
| `android-core-android.gradle.kts` | `:core:ui` |
| `android-app.gradle.kts` | `:app` |

Each plugin sets `compileSdk`, `minSdk`, Kotlin options, and Compose compiler config in one place. Individual `build.gradle.kts` files only declare dependencies and apply the matching plugin — ~5 lines each.

The concrete implementation under AGP 9.2.1 + Kotlin 2.3.0 (classic Kotlin, KSP instead of kapt, body-applied plugins, shared classloader) is non-trivial — see ADR-0018.

## Navigation
Each feature module declares its own `@Serializable` route data class (e.g. `StudyRoute(subcategoryId: String)`) as its public entry point. `:app`'s `NavGraph` imports feature modules and navigates via `navController.navigate(StudyRoute(id))`. Features never reference each other's route types — only `:app` does.

Intra-feature navigation (e.g. `PreviewStudySessionScreen` → `StudySessionScreen`) uses `() -> Unit` lambdas hoisted to the feature's internal nav graph or wired directly in `:app`. Route types for internal screens may live in the feature module or inline in `:app` — no strict rule needed since they have exactly one caller.

## Key rationale

**Hilt bindings belong to the module that owns the implementation** — data-layer bindings (repository interfaces → `Default*` implementations) live in `:core:data`. Feature-local bindings (e.g. `TtsPlayer`, `AudioFocusManager` in `:feature:study`) live in that feature's own `@Module`. `:app` owns no Hilt modules. XP, Level, Streak, DailyGoal repositories and their DataStore/Firestore sources live in `:core:data`, organized by internal package (`data/progress/`, `data/flashcard/`, etc.).

**`BrowseScreen` lives in `:feature:browse`, not `:feature:study`, despite being the bottom-nav "Study" tab's root screen** — the tab label is a UX concern (`:app`'s `MainScreen`), decoupled from module ownership. `BrowseScreen`'s job is listing/searching categories and subcategories, identical in kind to `CategoryDetailsScreen`/`SubcategoryDetailsScreen`, not session logic. `:feature:study` starts only once a subcategory selection reaches `PreviewStudySessionScreen`.

**`:feature:study` holds all three session screens** — `PreviewStudySessionScreen`, `StudySessionScreen`, `SessionSummaryScreen` form one user journey. State is passed between screens via nav arguments — session settings from PreviewStudySession into StudySession, summary data from StudySession into SessionSummary. Current session state is small enough that nav arg serialization is safe. Splitting into separate modules buys no isolation.

**`MediaSessionService` and `TtsPlayer` live in `:feature:study`** — Fast Study Mode's TTS service is exclusively consumed by the study flow. No other feature reads or controls playback state. `:app` picks up the service declaration via manifest merger.

**No `:core:navigation` module** — each feature owns its own typed route entry point. A shared navigation module would only be needed if features navigated to each other directly — they don't. `:app` remains the sole wiring point.

**Splash/startup in `:app`** — an `AppStartViewModel` in `:app` checks auth state and determines the start destination (home vs login). This is assembly logic, not an auth feature concern. The VM depends on `:core:domain` directly via a use case.

## Alternatives considered
- **Layer modules** (`:presentation`, `:domain`, `:data`) — rejected. Doesn't enforce feature isolation; a developer can still freely import across features. Not how enterprise teams structure Android projects.
- **All Hilt bindings in `:app`** — rejected. Creates a merge conflict hotspot; every new repository requires touching `:app`.
- **Big-bang migration** — rejected. Incremental migration follows a forced dependency order: (1) `build-logic/` convention plugins, (2) `:core:domain`, (3) `:core:design` + `:core:ui`, (4) `:core:data`, (5) features one at a time starting with `:feature:settings` (fewest dependencies, lowest risk), ending with `:feature:auth` (highest risk — startup routing). `:feature:progress` does not exist yet and will be created directly in the target structure.
- **Version catalog only for build sharing** — rejected. Handles dependency versions but leaves `android { }` blocks duplicated across every module. `build-logic` convention plugins eliminate that duplication.

## Testing

Fake repositories and test doubles are shared via `:core:domain`'s `testFixtures` source set (Gradle `java-test-fixtures` plugin). Feature modules declare `testImplementation(testFixtures(project(":core:domain")))`. Fakes live next to the interfaces they implement — no separate test module needed.

## Consequences
- Parallel Gradle compilation reduces incremental build times as the project grows.
- Illegal cross-feature imports are compile errors, not code review findings.
- Each module gets its own isolated test source set.
- Each new module requires its own `build.gradle.kts` and Hilt wiring — expected overhead.
