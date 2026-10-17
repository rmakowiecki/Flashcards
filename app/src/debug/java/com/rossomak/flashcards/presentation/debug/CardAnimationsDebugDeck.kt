package com.rossomak.flashcards.presentation.debug

import com.rossomak.flashcards.core.domain.model.CodeBlock
import com.rossomak.flashcards.core.domain.model.Flashcard

// Mock deck for the card animations harness: one card per card layout edge case.

internal const val DEBUG_SUBCATEGORY_ID = "debug"

private fun debugCard(
    id: String,
    question: String,
    answer: String,
    difficulty: Int,
    questionCode: List<CodeBlock>? = null,
    answerCode: List<CodeBlock>? = null,
    extendedContext: String? = null,
): Flashcard = Flashcard(
    id = id,
    subcategoryId = DEBUG_SUBCATEGORY_ID,
    tags = listOf("Debug"),
    question = question,
    answer = answer,
    difficulty = difficulty,
    questionCode = questionCode,
    answerCode = answerCode,
    questionSpoken = null,
    answerSpoken = null,
    extendedContext = extendedContext,
)

private val SHORT_KOTLIN = CodeBlock(
    language = "kotlin",
    code = """
        val count = remember { mutableStateOf(0) }
        Button(onClick = { count.value++ }) { Text("${'$'}{count.value}") }
    """.trimIndent(),
)

private val LONG_KOTLIN = CodeBlock(
    language = "kotlin",
    code = """
        @Composable
        fun Ticker(viewModel: TickerViewModel) {
            val ticks by viewModel.ticks.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) {
                while (true) {
                    delay(1.seconds)
                    viewModel.onTick()
                }
            }
            LazyColumn {
                items(ticks, key = { it.id }) { tick ->
                    Text(text = tick.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        class TickerViewModel : ViewModel() {
            private val _ticks = MutableStateFlow(emptyList<Tick>())
            val ticks: StateFlow<List<Tick>> = _ticks.asStateFlow()

            fun onTick() {
                _ticks.update { it + Tick(id = it.size, label = "Tick ${'$'}{it.size}") }
            }
        }
    """.trimIndent(),
)

private val LONG_PARAGRAPH = "Recomposition is optimistic and may be cancelled: Compose starts recomposing as soon as it " +
    "suspects state changed, and if state changes again before it finishes, it throws the work away and restarts. " +
    "Side effects inside a composable body can therefore run for a recomposition whose result is never applied, " +
    "which is why effects belong in LaunchedEffect, DisposableEffect or SideEffect, where they run only after a " +
    "successful composition. Composables can also run in any order and in parallel, so they must not rely on each " +
    "other's side effects or on shared mutable variables. "

internal val debugCards = listOf(
    debugCard(id = "tiny", question = "What is `val`?", answer = "Read-only.", difficulty = 1),
    debugCard(
        id = "medium",
        question = "What is the difference between remember and rememberSaveable?",
        answer = "remember keeps state across recompositions only; rememberSaveable also survives configuration " +
            "changes and process death by storing the value in a Bundle.",
        difficulty = 3,
    ),
    debugCard(
        id = "inline",
        question = "When does `LaunchedEffect(key)` restart its coroutine, and what happens to the old `Job`?",
        answer = "Whenever `key` changes between compositions; the previous coroutine is cancelled first.",
        difficulty = 5,
    ),
    debugCard(
        id = "long-word",
        question = "Explain `rememberUpdatedStateWithLifecycleAwareness`",
        answer = "A made-up name that tests how one unbreakable word wraps at the large style.",
        difficulty = 2,
    ),
    debugCard(
        id = "long-question",
        question = "Your screen collects a StateFlow from a ViewModel, and a list item reads one field of the state in a " +
            "lambda that runs during layout. Profiling shows the whole list recomposing on every emission even though " +
            "only that one field changed. Walk through why this happens, which phase reads the state, and what you " +
            "would change so only the affected item does work.",
        answer = "The state is read during composition, so every emission invalidates the composable. Defer the read " +
            "to layout or draw with a lambda-based modifier, and pass stable, narrow parameters to the item.",
        difficulty = 8,
    ),
    debugCard(
        id = "question-code",
        question = "What does this print after three taps?",
        answer = "3: the state is remembered across recompositions.",
        difficulty = 4,
        questionCode = listOf(SHORT_KOTLIN),
    ),
    debugCard(
        id = "answer-code",
        question = "Show a composable that ticks once a second and lists every tick.",
        answer = "Run the timer in a LaunchedEffect and keep the list in the ViewModel:",
        difficulty = 6,
        answerCode = listOf(LONG_KOTLIN),
        extendedContext = "LaunchedEffect(Unit) runs once per entering the composition and is cancelled when it leaves.",
    ),
    debugCard(
        id = "huge-answer",
        question = "Why must composables be free of side effects?",
        answer = LONG_PARAGRAPH + LONG_PARAGRAPH + LONG_PARAGRAPH,
        difficulty = 7,
        extendedContext = LONG_PARAGRAPH,
    ),
    debugCard(
        id = "huge-both",
        question = "Review this code. " + LONG_PARAGRAPH + "What would you change?",
        answer = LONG_PARAGRAPH,
        difficulty = 10,
        questionCode = listOf(LONG_KOTLIN),
        answerCode = listOf(LONG_KOTLIN, SHORT_KOTLIN),
    ),
    debugCard(id = "last", question = "Last card: does next stay put?", answer = "Yes. The real session opens Summary here.", difficulty = 1),
)

internal val debugCardLabels = mapOf(
    "tiny" to "Tiny question and answer (min height)",
    "medium" to "Medium question, medium answer",
    "inline" to "Inline code in question (still large)",
    "long-word" to "One long unbreakable word",
    "long-question" to "Long question (grows before reveal)",
    "question-code" to "Question code block (starts small)",
    "answer-code" to "Long answer code + Learn more",
    "huge-answer" to "Huge answer (eats top padding, scrolls)",
    "huge-both" to "Huge question and answer (scrolls unrevealed)",
    "last" to "Last card",
)
