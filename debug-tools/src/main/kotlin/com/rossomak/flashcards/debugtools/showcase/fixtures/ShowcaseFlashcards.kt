@file:Suppress("MagicNumber")

package com.rossomak.flashcards.debugtools.showcase.fixtures

import com.rossomak.flashcards.core.domain.model.CodeBlock
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.Subcategory

/**
 * Flashcards for the screen showcase, written like the real corpus: inline code in backticks, code
 * blocks with a language, a Difficulty from 1 to 10 and Extended Context on the harder cards.
 */
internal object ShowcaseFlashcards {

    val coroutines: List<Flashcard> = listOf(
        card(
            id = "coroutines-launch-vs-async",
            subcategory = ShowcaseTaxonomy.coroutines,
            tags = listOf("Builders", "General"),
            difficulty = 3,
            question = "What is the difference between `launch` and `async`?",
            answer = "`launch` starts a coroutine that returns a `Job` and no result. `async` returns a `Deferred<T>`, " +
                "whose `await()` suspends until the result is ready and rethrows any failure.",
            answerCode = kotlin(
                """
                val job: Job = scope.launch { refreshCache() }
                val price: Deferred<Int> = scope.async { fetchPrice() }
                println(price.await())
                """,
            ),
        ),
        card(
            id = "coroutines-structured-concurrency",
            subcategory = ShowcaseTaxonomy.coroutines,
            tags = listOf("Structured concurrency"),
            difficulty = 5,
            question = "What does structured concurrency guarantee when one child of a `coroutineScope` fails?",
            answer = "The failure cancels the scope, which cancels every sibling, and `coroutineScope` rethrows it once all " +
                "children have finished. No child outlives the scope that started it.",
            answerCode = kotlin(
                """
                suspend fun loadDashboard() = coroutineScope {
                    val user = async { api.user() }
                    val feed = async { api.feed() } // cancelled if user() throws
                    Dashboard(user.await(), feed.await())
                }
                """,
            ),
            extendedContext = "Contrast with `supervisorScope`, where a failing child does not cancel its siblings. " +
                "This is why `viewModelScope` uses a `SupervisorJob`: one failed request must not tear down the screen.",
        ),
        card(
            id = "coroutines-dispatchers-io",
            subcategory = ShowcaseTaxonomy.coroutines,
            tags = listOf("Dispatchers"),
            difficulty = 4,
            question = "Why should blocking file or network calls run on `Dispatchers.IO` rather than `Dispatchers.Default`?",
            answer = "`Default` is sized to the CPU core count for CPU-bound work, so blocking it starves computations. " +
                "`IO` allows many more threads, because a blocked thread there costs only memory.",
        ),
        card(
            id = "coroutines-cancellation-cooperative",
            subcategory = ShowcaseTaxonomy.coroutines,
            tags = listOf("Cancellation"),
            difficulty = 7,
            question = "Why can a cancelled coroutine keep running, and how do you make it stop?",
            questionCode = kotlin(
                """
                scope.launch {
                    while (true) {
                        crunchNextChunk()
                    }
                }
                """,
            ),
            answer = "Cancellation is cooperative: it only takes effect at a suspension point or a check. A loop that never " +
                "suspends must check `isActive` or call `ensureActive()` itself.",
            answerCode = kotlin(
                """
                scope.launch {
                    while (isActive) {
                        crunchNextChunk()
                    }
                }
                """,
            ),
            extendedContext = "`yield()` also checks for cancellation and lets other coroutines on the same dispatcher run. " +
                "Catching `CancellationException` without rethrowing it breaks cancellation for the whole hierarchy.",
        ),
        card(
            id = "coroutines-exception-handler",
            subcategory = ShowcaseTaxonomy.coroutines,
            tags = listOf("Exceptions"),
            difficulty = 8,
            question = "Where does a `CoroutineExceptionHandler` take effect, and why does it do nothing on an `async`?",
            answer = "Only on a root coroutine started with `launch`, or a direct child of a `SupervisorJob`. `async` holds " +
                "its exception in the `Deferred` until `await()`, so there is nothing for the handler to catch.",
            answerCode = kotlin(
                """
                val handler = CoroutineExceptionHandler { _, error -> log(error) }
                scope.launch(handler) { riskyWork() } // handled
                scope.async(handler) { riskyWork() }  // stays in the Deferred
                """,
            ),
            extendedContext = "Installing the handler on a nested `launch` has no effect, because the exception propagates " +
                "to the parent first. The parent's handler, or the thread's uncaught handler, sees it instead.",
        ),
        card(
            id = "coroutines-with-context",
            subcategory = ShowcaseTaxonomy.coroutines,
            tags = listOf("Dispatchers", "General"),
            difficulty = 2,
            question = "What does `withContext(Dispatchers.IO)` return?",
            answer = "The value of its block. It suspends the caller, runs the block on the given dispatcher and resumes " +
                "with the result.",
        ),
        card(
            id = "coroutines-mutex",
            subcategory = ShowcaseTaxonomy.coroutines,
            tags = listOf("Structured concurrency"),
            difficulty = 6,
            question = "Why use a `Mutex` instead of `synchronized` inside a coroutine?",
            answer = "`synchronized` blocks the thread and cannot hold across a suspension point. `Mutex.withLock` " +
                "suspends while waiting, so the thread stays free.",
            answerCode = kotlin(
                """
                private val mutex = Mutex()

                suspend fun increment() = mutex.withLock {
                    counter = counter + 1
                }
                """,
            ),
        ),
    )

    val compose: List<Flashcard> = listOf(
        card(
            id = "compose-remember-saveable",
            subcategory = ShowcaseTaxonomy.compose,
            tags = listOf("State"),
            difficulty = 3,
            question = "What does `rememberSaveable` keep that `remember` loses?",
            answer = "Its value survives configuration changes and process death, by saving it into the saved instance " +
                "state bundle. `remember` keeps a value only for as long as the composition does.",
        ),
        card(
            id = "compose-derived-state",
            subcategory = ShowcaseTaxonomy.compose,
            tags = listOf("State", "Performance"),
            difficulty = 6,
            question = "When does `derivedStateOf` reduce recompositions?",
            answer = "When the derived value changes less often than its inputs. Readers recompose only when the result " +
                "changes, not on every input change.",
            answerCode = kotlin(
                """
                val showScrollToTop by remember {
                    derivedStateOf { listState.firstVisibleItemIndex > 0 }
                }
                """,
            ),
        ),
        card(
            id = "compose-stable-lambda",
            subcategory = ShowcaseTaxonomy.compose,
            tags = listOf("Performance"),
            difficulty = 7,
            question = "Why can passing `viewModel::onSelect` to a list item be cheaper than `{ viewModel.onSelect(it) }`?",
            answer = "A method reference on a stable receiver is stable and compared by equality, so the item can skip. " +
                "A lambda capturing an unstable value is rebuilt on every recomposition.",
        ),
    )

    private fun kotlin(code: String): List<CodeBlock> = listOf(CodeBlock(language = "kotlin", code = code.trimIndent()))

    @Suppress("LongParameterList") // mirrors the Flashcard constructor, with defaults for the optional parts
    private fun card(
        id: String,
        subcategory: Subcategory,
        tags: List<String>,
        difficulty: Int,
        question: String,
        answer: String,
        questionCode: List<CodeBlock>? = null,
        answerCode: List<CodeBlock>? = null,
        extendedContext: String? = null,
    ) = Flashcard(
        id = id,
        subcategoryId = subcategory.id,
        tags = tags,
        question = question,
        answer = answer,
        difficulty = difficulty,
        questionCode = questionCode,
        answerCode = answerCode,
        questionSpoken = null,
        answerSpoken = null,
        extendedContext = extendedContext,
    )
}
