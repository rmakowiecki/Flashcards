# Testing Conventions

Authoritative reference for JVM unit tests. All test-writing agents and contributors must follow these rules. Examples drawn from real tests under `app/src/test/java/com/rossomak/flashcards/`.

> Scope: JVM unit tests only (`app/src/test/` and each `feature/*/src/test/`). Instrumented/Compose UI conventions not yet documented.

---

## 1. Scope and Stack
- **Frameworks**: JUnit 4.13.2, MockK 1.14.11, Turbine 1.2.1, Kotest Assertions 5.9.1, kotlinx-coroutines-test 1.11.0. No Robolectric — tests run on the plain JVM.
- **Nav-arg ViewModels**: read routes via `SavedStateHandle.decodeRoute<T>()` (`core:ui`, `core/ui/.../navigation/RouteDecoder.kt`), not the bare `toRoute` (whose `android.os.Bundle` decode can't run off-device). Tests stub the seam: `mockkObject(RouteDecoder)` + `every { RouteDecoder.decode(any<() -> MyRoute>()) } returns fakeRoute`, with `unmockkObject(RouteDecoder)` in `@After`. For navigation assertions, collect `viewModel.events` with Turbine instead of checking a `navigationDestination` state field.
- **Location**: `app/src/test/java/…` and `feature/*/src/test/kotlin/…`, mirroring production package structure.
- **Test wiring**: feature-module test deps (junit, mockk, turbine, kotest, coroutines-test) are applied centrally by the `android-feature` convention plugin — do not re-declare them per module.
- **Shared utilities**: `MainDispatcherRule` and the `assertValue` helper live in `core:domain` **testFixtures** (package `com.rossomak.flashcards.testutil`); consume via `testImplementation(testFixtures(project(":core:domain")))`. Domain fakes (`FakeAuthRepository`, `FakeFlashcardRepository`) live there too. See section 5 for when to write a fake and when to mock.
- **Coverage targets**: use cases 90%+, ViewModels 80%+, repositories 80%+.

---

## 2. File and Test Naming
- **File**: `{ClassUnderTest}Test.kt` (e.g. `MainViewModelTest.kt`).
- **Test method**: backtick-quoted natural-language sentence, pattern `` `<action/state> <condition> <expected result>`() ``.
- Do **not** prefix test functions with `test`, and do **not** use snake_case (`methodName_condition_result`).

**Why:** backtick names render verbatim in IDE/CI output. Failures read like specifications.

```kotlin
@Test
fun `onSignInFailed with message sets error and stops signing in`() { ... }

@Test
fun `display name falls back to email when displayName is blank`() { ... }
```

---

## 3. Test Class Layout
- Mocks declared as `private val` at class level via `mockk()` or `mockk(relaxed = true)`.
- For ViewModel tests: provide a `createViewModel()` factory and instantiate the SUT inside each test **after** stubbing.
- Use `@Before lateinit var viewModel` **only** when no mock stubbing varies between tests.

**Why:** ViewModel `init {}` blocks read from mocks. Instantiating in `@Before` runs `init` before per-test stubs apply.

✅ Factory pattern (from `MainViewModelTest`):
```kotlin
class MainViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getCurrentAuthUserUseCase: GetCurrentAuthUserUseCase = mockk()
    private val signOutUseCase: SignOutUseCase = mockk(relaxed = true)

    private fun createViewModel(): MainViewModel =
        MainViewModel(getCurrentAuthUserUseCase, signOutUseCase)

    @Test
    fun `init with null user navigates to Login`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { getCurrentAuthUserUseCase() } returns null

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.navigationDestination shouldBe MainDestination.Login
    }
}
```

✅ `@Before` acceptable (no `init {}` reads from mocks, identical setup for every test):
```kotlin
@Before
fun setUp() {
    viewModel = LoginViewModel(SignInWithGoogleUseCase(authRepository))
}
```

---

## 4. Coroutine and Dispatcher Rules
- Every ViewModel test class: `@get:Rule val mainDispatcherRule = MainDispatcherRule()`.
- Use `runTest(mainDispatcherRule.testDispatcher) { ... }` — never bare `runTest {}` — when SUT touches `Dispatchers.Main` or `viewModelScope`.
- Call `advanceUntilIdle()` after any action that launches a coroutine (including `init` and event handlers).
- For virtual-time regression guards, assert on `testScheduler.currentTime`.

**Why:** `viewModelScope` defaults to `Dispatchers.Main`. Without the rule, tests hang or throw `Module with the Main dispatcher had failed to initialize`.

```kotlin
@Test
fun `onSignOutClick success navigates to Login`() = runTest(mainDispatcherRule.testDispatcher) {
    coEvery { getCurrentAuthUserUseCase() } returns AuthUser("u1", "a@b.com", "Alex", null)
    coEvery { signOutUseCase() } returns Unit

    val viewModel = createViewModel()
    advanceUntilIdle()
    viewModel.onSignOutClick()
    advanceUntilIdle()

    viewModel.state.value.navigationDestination shouldBe MainDestination.Login
}
```

Virtual-time regression guard:
```kotlin
// Regression guard for commit 1e181aa: post-animation delay must NOT apply on timeout.
testScheduler.currentTime shouldBeLessThan 7_000L
```

---

## 5. Fakes and mocks
**Use a MockK mock** for a collaborator that returns canned values and whose calls you verify, or that you only call for side effects (a logger, a sign-out). This is the default, in `core:domain` tests as well as feature modules.

**Write a fake** (in `core:domain` testFixtures) only for a collaborator with behavior of its own that several tests share: state that reacts to calls, or values emitted over time. Examples: `FakeUserPreferencesRepository` (`save` updates a `StateFlow`), `FakeAudioInterruptionGateway` (`emit(signal)`), the voice playback and capture gateways. A mock of these needs the same logic re-stubbed in every test. A fake that only returns a canned value and records calls is a mock in disguise: do not add one.

**Why:** a fake per interface costs a class to write and keep in sync with every interface change, and most collaborators need no behavior.

**`kotlin.Result`:** `coEvery { ... } returns Result.success(x)` / `Result.failure(e)` works on MockK 1.14.11 (and 1.13.13) for suspend and plain functions, including `Result<Unit>`, `Result<List<T>>`, `Result<T?>` and `returnsMany`. A relaxed mock that returns a `Result` yields a `Success` wrapping a mock object, not a real value: stub any `Result` the code under test reads.

### MockK
- `mockk()` — mocks whose calls you verify or whose returns you stub.
- `mockk(relaxed = true)` — collaborators called for side effects only (sign-out, logging) where returns don't matter.
- `coEvery` / `coVerify` — `suspend` functions. `every` / `verify` — non-suspend.
- Prefer `verify(exactly = N) { ... }` over bare `verify { ... }`.

**Why:** `exactly = N` catches double-invocations; bare `verify` passes for any non-zero count.

```kotlin
coEvery { authRepository.signInWithGoogleIdToken("token") } returns Result.success(user)
coVerify(exactly = 1) { authRepository.signInWithGoogleIdToken("token") }
```

### Verify completeness — every test, every branch
Every test that triggers a collaborator call **must** include a `verify`/`coVerify` for that call, regardless of whether the test asserts a success, failure, or null result.

**Why:** omitting `verify` on failure/null branches means the test passes even if the use case never calls the repository — it proves outcome but not interaction.

❌ Incomplete — missing verify on the null branch:
```kotlin
@Test
fun `returns null when repository has no user`() = runTest {
    every { authRepository.getCurrentUser() } returns null

    val result = useCase()

    result shouldBe null
    // no verify — test passes even if useCase() ignores the repository entirely
}
```

✅ Complete:
```kotlin
@Test
fun `returns null when repository has no user`() = runTest {
    every { authRepository.getCurrentUser() } returns null

    val result = useCase()

    result shouldBe null
    verify(exactly = 1) { authRepository.getCurrentUser() }
}
```

**Rule:** if you wrote `every { ... }` or `coEvery { ... }` in Arrange, write `verify(exactly = N) { ... }` or `coVerify(exactly = N) { ... }` in Assert. No exceptions for failure, null, or edge-case branches.

---

## 6. Assertions — Kotest
Use Kotest matchers (`shouldBe`, `shouldBeLessThan`, `shouldHaveSize`, etc.). Do **not** use JUnit `assertEquals`/`assertTrue`.

**Why:** project standardized on Kotest infix syntax. Mixing styles fragments readability.

```kotlin
state.isLoading shouldBe false
state.displayName shouldBe "Alex"
testScheduler.currentTime shouldBeLessThan 7_000L
```

---

## 7. Extract Repeated Literals
**Any literal that is both passed into the SUT and asserted on later in the same test MUST be extracted to a local `val` at the top of the test body.**

Applies to: strings, IDs, error messages, numeric thresholds, mock-returned domain objects.

**Why:** prevents silent drift. If a future edit changes the input but not the assertion (or vice versa), the test compiles but no longer verifies intended behavior. Single `val` = single source of truth.

**How to apply:** literal appears ≥2 times → extract. Single use → inline is fine.

✅ Good:
```kotlin
@Test
fun `onSignInFailed with message sets error and stops signing in`() {
    val errorMessage = "network down"
    viewModel.onSignInStarted()

    viewModel.onSignInFailed(errorMessage)

    val state = viewModel.state.value
    state.isSigningIn shouldBe false
    state.errorMessage shouldBe errorMessage
}
```

✅ Token forwarded into mock and verified:
```kotlin
@Test
fun `forwards token to repository and returns success result`() = runTest {
    val token = "token-xyz"
    val user = AuthUser(uid = "u1", email = "a@b.com", displayName = "Alex", photoUrl = null)
    coEvery { authRepository.signInWithGoogleIdToken(token) } returns Result.success(user)

    val result = useCase(token)

    result.isSuccess shouldBe true
    result.getOrNull() shouldBe user
    coVerify(exactly = 1) { authRepository.signInWithGoogleIdToken(token) }
}
```

### Shared fixtures across tests
If the same domain object is constructed in multiple tests in a class (e.g. `AuthUser("u1", "a@b.com", "Alex", null)`), hoist to a `companion object` constant or private property — same rationale, broader scope.

```kotlin
class MainViewModelTest {
    private val testUser = AuthUser(uid = "u1", email = "a@b.com", displayName = "Alex", photoUrl = "http://p")

    @Test
    fun `init with authenticated user emits loaded state`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { getCurrentAuthUserUseCase() } returns testUser
        // ...
        state.displayName shouldBe testUser.displayName
    }
}
```

---

## 8. Arrange / Act / Assert Layout
Separate the three phases with blank lines. Do **not** add `// Given / // When / // Then` comments — blank lines and the test name already communicate intent.

```kotlin
@Test
fun `forwards failure result unchanged`() = runTest {
    val error = IllegalStateException("bad token")
    coEvery { authRepository.signInWithGoogleIdToken(any()) } returns Result.failure(error)

    val result = useCase("anything")

    result.isFailure shouldBe true
    result.exceptionOrNull() shouldBe error
}
```

---

## 9. State Assertions on StateFlow
- Assert state after `advanceUntilIdle()`.
- For ≥2 fields, use the `assertValue { ... }` extension (`testutil/StateAssertions.kt`) — the lambda receiver is the state, so fields are referenced bare.
- For a single field, inline `viewModel.state.value.field shouldBe x`.
- Use Turbine (`state.test { ... }`) **only** for emission-sequence assertions, not single-snapshot reads.

**Why:** `assertValue` removes the repetitive `state.` prefix and the temporary `val state = ...` binding. Turbine's `awaitItem()` / `cancelAndIgnoreRemainingEvents()` is noise for single snapshots — reserve for ordered emissions.

✅ Multiple fields — `assertValue`:
```kotlin
viewModel.state.assertValue {
    isLoading shouldBe false
    displayName shouldBe "Alex"
    photoUrl shouldBe "http://p"
    error shouldBe null
}
```

✅ Single field — inline:
```kotlin
viewModel.state.value.navigationDestination shouldBe MainDestination.Login
```

✅ Emission sequence — Turbine justified:
```kotlin
viewModel.state.test {
    awaitItem().isLoading shouldBe true
    awaitItem().isLoading shouldBe false
    cancelAndIgnoreRemainingEvents()
}
```
