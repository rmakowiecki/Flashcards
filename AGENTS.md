# Android Flashcards — Code Guidelines

@CONTEXT.md

## Project Overview
- **Package Name**: `com.rossomak.flashcards`
- **Architecture**: Clean Architecture with MVVM
- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 36

## Architecture Guidelines

### Clean Architecture Layers
```
presentation/   → ViewModels, Compose UI, UI State classes
domain/         → Use Cases, Domain Models, Repository Interfaces
data/           → Repository Implementations, Data Sources (Remote/Local), DTOs, Mappers
```

**Dependency Rule**: Outer layers depend on inner layers only
- Presentation depends on Domain
- Domain has NO dependencies (pure Kotlin)
- Data depends on Domain (implements repository interfaces)

### MVVM Pattern (Presentation Layer)
- **ViewModel**: Holds UI state, orchestrates use cases, survives configuration changes
- **UI State**: Single data class representing screen state
- **Composables**: Stateless when possible, receive state and emit events

Example structure:
```kotlin
@HiltViewModel
class FlashcardViewModel @Inject constructor(
    private val getFlashcardsUseCase: GetFlashcardsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(FlashcardScreenState())
    val state: StateFlow<FlashcardScreenState> = _state.asStateFlow()

    fun loadFlashcards() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            getFlashcardsUseCase()
                .onSuccess { cards -> _state.update { it.copy(cards = cards, isLoading = false) } }
                .onFailure { error -> _state.update { it.copy(error = error.message, isLoading = false) } }
        }
    }
}
```

### Use Cases (Domain Layer)
- One use case = one business action
- Return `Result<T>` for operations that can fail
- No Android framework dependencies
- Located in `domain/usecase/` package

### Repository Pattern (Data Layer)
- Interface in domain layer, implementation in data layer
- Coordinate between remote and local data sources
- Use mappers to convert DTOs ↔ Domain models

## Kotlin Code Style

- Descriptive variable names — no single letters except loop indices
- `val` over `var`; `when` over `if/else` chains for 3+ branches
- Avoid `!!`; prefer `?.let`, `?:`
- Use `with(receiver) { ... }` when repeating same receiver 2+ times in a block (e.g. multi-branch `when` accessing several props of same object) — cuts repetition, no functional change

### Function Signatures
- Single-line for < 250 characters; multi-line (one param per line) for 250+
- Prefer expression body (`= ...`) for short, non-complex functions returning a non-Unit value

### Naming Conventions
- Classes/Objects: PascalCase (`FlashcardViewModel`)
- Functions/Variables: camelCase (`getFlashcards`)
- Constants: UPPER_SNAKE_CASE
- Composable functions: PascalCase (`FlashcardScreen()`)
- ViewModel event handlers (UI → ViewModel callbacks): `onXxx` prefix, present tense (`onCategoriesRefresh`, `onCardSelect`)
- **Dialogs are the one exception**: a screen's dialogs are hoisted into a single sealed `activeDialog` field and report back through a single `onDialogEvent: (XxxDialogEvent) -> Unit`, rendered by a dedicated `XxxDialogHost` composable. See [ADR-0036](./docs/adr/0036-sealed-dialog-state-and-dialog-events.md). Ordinary screen callbacks stay explicit `onXxx` lambdas.
- Interface implementations: `Default` prefix, no `Impl` suffix (`DefaultFlashcardRepository`, not `FlashcardRepositoryImpl`; `DefaultAudioPlayer`, not `AudioPlayerImpl`)

### String Resources
Naming/ownership rules for `strings.xml` — full rationale in [ADR-0023](./docs/adr/0023-string-resource-naming-conventions.md):
- Each `:feature:*` module and `:core:ui` own their own `strings.xml`; `:app` keeps only app-level strings.
- Key pattern: `screen_element_role`, no feature-name prefix (e.g. `login_username_label`).
- Role suffix is one of a closed set: `_label`, `_button`, `_title`, `_hint`, `_error`, `_message`, `_cd`.
- Shared strings live in `:core:ui` prefixed `common_` (e.g. `common_done_button`) — promote a string there only once a 2nd module needs it verbatim; don't pre-seed a common list.
- `HardcodedText` lint is `error` in the convention plugins — new hardcoded UI strings fail the build. Existing hardcoded strings migrate incrementally as their screen is touched.

### Sealed Classes for States
Use sealed classes for finite UI states (e.g. loading / content / error variants of a screen state).

For fallible operations, return `kotlin.Result<T>` and consume with `.onSuccess { ... }` / `.onFailure { ... }`. Do not define a project-local `Result` type — it would shadow the stdlib one.

### No ephemeral planning references in persistent text
Never cite a `spec NN`/`ticket NN`/`docs/temp`/scratch-plan label in KDoc, code comments, commit messages, or any file that isn't itself the ephemeral plan doc. Those numbers/paths are session-local planning scaffolding — meaningless (or actively confusing) to a future reader, since the plan doc they point to is gitignored or long gone. Persistent docs (KDoc, ADRs, `CONTEXT.md`, `SYSTEMDESIGN.md`, README files) describe the *current, standalone* design — reference another persistent doc (an ADR, a class, a file) instead, or drop the citation and just explain the reasoning inline.

## Argument Order

Every signature type below has one fixed parameter order — rationale in [ADR-0020](./docs/adr/0020-argument-order-conventions.md).

**After writing or editing any `XxxScreen`/`XxxContent` composable, `@HiltViewModel` class, or `*Repository.kt`/`*DataSource.kt`/`*UseCase.kt` file, run:**
```
python3 ./scripts/check-arg-order.py
```
Exit code `0` + `check-arg-order: no violations found.` means clean. Exit code `1` prints one `path/to/File.kt:LINE: message (ADR-0020)` line per violation — fix each one and re-run before considering the task done. It's a regex/paren-depth heuristic (not yet ported to Konsist), so it can miss unusual formatting; don't treat a clean run as a substitute for actually following the rules below, only as a backstop.

**Composable Screens** (`XxxScreen`, nav entry points) — `modifier` → `viewModel` → nav callbacks (`onNavigateBack` first if present, then remaining callbacks happy-path-first):
```kotlin
@Composable
fun ExampleScreen(
    modifier: Modifier = Modifier,
    viewModel: ExampleViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (id: String) -> Unit,
) { ... }
```
Nav callbacks have no default, so they sit after defaulted params (`modifier`, `viewModel`) — every call site must use named arguments (already the case throughout `NavGraph.kt`).

**Composable Content** (`XxxContent`, stateless) — `modifier` first → `state` → callbacks:
```kotlin
@Composable
fun ExampleContent(
    modifier: Modifier = Modifier,
    state: ExampleScreenState,
    onRefresh: () -> Unit,
) { ... }
```

**ViewModel constructors** — `SavedStateHandle` (if present) → use cases → gateways/controllers/other collaborators:
```kotlin
@HiltViewModel
class ExampleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getExamplesUseCase: GetExamplesUseCase,
    private val exampleGateway: ExampleGateway,
) : ViewModel() { ... }
```

**Repository / DataSource / UseCase multi-param methods** — identifiers/keys → payload/action/value:
```kotlin
suspend fun upsertCurationAction(cardId: String, subcategoryId: String, action: CurationAction): Result<Unit>
```

## Jetpack Compose Guidelines

**Composable taxonomy:**
- `XxxScreen` — nav entry point; holds ViewModel, observes state, triggers navigation
- `XxxContent` — stateless UI; accepts state and callbacks, no ViewModel, fully previewable

Best practices:
- State hoisting: lift state to the lowest common ancestor
- Add `@Preview` for all major composables
- `contentDescription` on icon buttons and images
- `remember` for expensive calculations; `derivedStateOf` for computed state
- `LazyColumn` for lists; `key()` for stable item identity

## Dependency Injection (Hilt)

- `@HiltAndroidApp` on Application, `@AndroidEntryPoint` on Activities, `@HiltViewModel` on ViewModels

### Module Organization
- `NetworkModule`: Retrofit, OkHttp, API services
- `RepositoryModule`: Repository implementations
- `AppModule`: Application-level dependencies

### Scoping
- `@Singleton`: app-wide (database, API client)
- `@ViewModelScoped`: scoped to ViewModel lifecycle
- `@ActivityRetainedScoped`: survives configuration changes

## Asynchronous Programming

Dispatchers:
- `Dispatchers.IO` — network, file operations
- `Dispatchers.Default` — CPU-intensive work
- `Dispatchers.Main` — UI updates (default in Compose)

StateFlow / SharedFlow rules:
- `StateFlow` for UI state in ViewModels (single source of truth per screen)
- `SharedFlow` for transient one-time events such as snackbars and toasts
- **Navigation is a one-time event, not state**: dispatch it through a `Channel<XxxDestination>(Channel.BUFFERED)` exposed via `receiveAsFlow()` and collect it once in the UI with `ObserveAsEvents(viewModel.events) { … }` (`core:ui`). Destinations stay type-safe sealed interfaces implementing `NavigationEvent` (no route strings). Never put navigation in persistent screen state; there is no `onNavigationHandled()` reset. See [docs/navigation-pattern.md](./docs/navigation-pattern.md) and [ADR-0019](./docs/adr/0019-navigation-as-one-time-events.md).
- Handle errors with `.catch()` operator on upstream flows

## Data Layer Standards

- DTOs use `@Serializable`; named with `Dto` suffix (`FlashcardDto`)
- Mappers: `toDomain()` (DTO → Domain), `toDto()` (Domain → DTO)
- **Firestore string constants**: All Firestore collection names, document names, and field names used in queries must be extracted into `const val` constants in a `companion object` of the data source class. Never pass raw string literals to `.collection()`, `.document()`, `.orderBy()`, `.whereEqualTo()`, etc.

```kotlin
object FlashcardMapper {
    fun FlashcardDto.toDomain(): Flashcard = Flashcard(
        id = id,
        question = question,
        answer = answer,
        createdAt = Instant.parse(createdAt)
    )
}
```

## API Integration

Error handling pattern for repository methods:
```kotlin
override suspend fun submitResponse(cardId: String, audioFile: File): Result<EvaluationResult> {
    return try {
        Result.success(mapper.toDomain(api.submitResponse(cardId, audioPart)))
    } catch (e: IOException) {
        Result.failure(IOException("Network error: ${e.message}", e))
    } catch (e: HttpException) {
        Result.failure(e)
    }
}
```

## Logging

Use `AppLog` (`core/common`, `com.rossomak.flashcards.core.common.AppLog.kt`), never `Timber` or `println` directly. Lambda-message helpers, one per level: `logv`, `logd`, `logi`, `logw`, `loge`. `logw`/`loge` take an optional leading `Throwable?`.

```kotlin
logd { "App start: scheduling session submission drain for recovery" }
loge(exception) { "Failed to submit response for card $cardId" }
```

`AppLog` wraps Timber (`Timber.plant(Timber.DebugTree())` only in debug builds, in `FlashcardsApplication.onCreate`). Lambda form defers string building until the log level is actually enabled.

## Static Analysis

Four tools, one job each, wired via the `android-quality` convention plugin (applied by every module's convention plugin). Config lives at the repo root (`.editorconfig`, `config/detekt/detekt.yml`).

| Tool | Job | Existing violations |
|------|-----|---------------------|
| **Spotless** (ktlint) | Formatting (`.kt`, `.gradle.kts`) | Autofixed — no baseline |
| **detekt** (typeless) | Code smells | Frozen in per-module `detekt-baseline.xml` |
| **Konsist** | Architecture rules (layer deps, naming) — `:konsist` module, runs as JUnit tests | Rules scoped to pass |
| **Android Lint** | Android correctness | Frozen in per-module `lint-baseline.xml` |

Commands (local only — CI wiring is a later PR):
```shell
./gradlew staticAnalysis   # spotlessCheck + detekt + :konsist:test + lint (the gate)
./gradlew formatCode       # spotlessApply — autofix formatting
```
`staticAnalysis` is NOT wired into `check` (keeps test runs fast). Any NEW (non-baselined) finding fails it.

Burning down a baseline: fix the smells, then regenerate with `./gradlew detektBaseline` / `./gradlew updateLintBaseline`; delete a baseline file once it reaches empty to fully enforce that module. Deferred to follow-up PRs: detekt type-resolution + Compose ruleset, arg-order → Konsist migration, lint rule tightening, CI.

`LargeClass` is excluded repo-wide for `*Test.kt` (`config/detekt/detekt.yml`) — test classes may grow unbounded rather than being split by concern; a new finding there is never baselined, it's already excluded by config.

## Testing Standards
See [TESTING.md](./TESTING.md) for full conventions: file/method naming, MainDispatcherRule usage, MockK + Kotest patterns, the "extract repeated literals" rule, and coverage targets.

## Security

- **NEVER** commit API keys, tokens, or secrets to Git
- Use `local.properties` for local secrets (gitignored)
- Use `BuildConfig` fields for compile-time config
- Use `EncryptedSharedPreferences` for auth tokens
- **NEVER** read/open/print gitignored secret files (`local.properties`, `app/google-services.json`, `*service-account*.json`, `*.cred.json`, keystore files) — path/filename references are fine, contents are not

### New worktrees

Creating a new `git worktree` gives you a checkout without the gitignored local secrets (Firebase config, signing keystores, service-account JSONs) needed to build/run the app, and without the `graphify-out/` knowledge graph. You're allowed to run `scripts/copy-worktree-local-state.sh -f <path-to-worktree>` to bring both over from the current checkout — do this right after creating a worktree, without asking. Always pass `-f` so existing secret files in the target worktree are overwritten (keeps stale copies from lingering); `graphify-out/` is symlinked back to this checkout rather than copied, and the script refuses to touch it if the destination already has a real (non-symlinked) `graphify-out/` directory, `-f` or not. Do not open/read the secret files yourself; the script copies them by filename pattern only and never prints contents.

## Project Documentation

- `SYSTEMDESIGN.md` — product design, screens, flows (being split into smaller docs under `docs/design/`)
- `docs/design/firestore-schema.md` — Firestore data schema. Read this if task needs current Firestore data schema knowledge — single source of truth, don't reconstruct schema from ADRs
- `CONTEXT.md` — domain vocabulary glossary
- `TESTING.md` — testing conventions
- `docs/navigation-pattern.md` — state-based navigation pattern (why no SharedFlow)
