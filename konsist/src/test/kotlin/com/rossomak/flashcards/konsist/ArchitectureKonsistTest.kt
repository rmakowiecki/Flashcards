package com.rossomak.flashcards.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/**
 * Architecture rules enforced across the whole project (ADR-0020 arg-order stays in the
 * Python script for now). Rules are intentionally conservative in this first pass so the
 * suite is green on existing code; tighten in follow-up PRs.
 */
class ArchitectureKonsistTest {

    private val projectScope = Konsist.scopeFromProject()

    @Test
    fun `domain module has no Android framework imports`() {
        projectScope
            .files
            .filter { it.path.contains("/core/domain/src/") }
            .assertTrue { file ->
                file.imports.none { import -> import.name.startsWith("android.") || import.name.startsWith("androidx.") }
            }
    }

    @Test
    fun `no class uses the Impl suffix`() {
        projectScope
            .classes()
            .assertFalse { it.name.endsWith("Impl") }
    }

    @Test
    fun `DTO classes reside in a data-layer package`() {
        // Firestore DTOs use reflection-based mapping, not kotlinx @Serializable, so we
        // enforce location (data layer) rather than a serialization annotation here.
        projectScope
            .classes()
            .filter { it.name.endsWith("Dto") }
            .assertTrue { koClass -> koClass.resideInPackage("..data..") }
    }

    @Test
    fun `HiltViewModel classes have the ViewModel suffix`() {
        projectScope
            .classes()
            .filter { koClass -> koClass.annotations.any { it.name == "HiltViewModel" } }
            .assertTrue { it.name.endsWith("ViewModel") }
    }

    @Test
    fun `design-system composables use no raw dp literals`() {
        // Reusable components in :core:ui composables/ must read dimensions from theme
        // tokens (spacing / sizes / cornerRadius), never hardcode `dp` (see core/ui/README.md
        // and ADR-0020). Opt out per-function with @RawDimensions("reason"). Colors are never
        // exempt. Token *definitions* live in the theme/ package, so scoping to composables/
        // keeps them out of this check. @Preview/@PreviewLightDark functions are exempt too:
        // their job is to show one illustrative example, not to model a reusable token.
        val rawDpLiteral = Regex("""\b\d+(\.\d+)?\.dp\b""")
        val previewAnnotations = setOf("Preview", "PreviewLightDark")
        projectScope
            .functions()
            .filter { function -> function.path.contains("/core/ui/") && function.path.contains("/composables/") }
            .filter { function -> function.annotations.none { it.name == "RawDimensions" } }
            .filter { function -> function.annotations.none { it.name in previewAnnotations } }
            .assertFalse { function -> rawDpLiteral.containsMatchIn(function.text) }
    }

    @Test
    fun `core modules never import feature modules`() {
        projectScope
            .files
            .filter { it.path.contains("/core/") && it.path.contains("/src/") }
            .assertTrue { file ->
                file.imports.none { import -> import.name.startsWith("com.rossomak.flashcards.feature.") }
            }
    }

    @Test
    fun `use case classes implement UseCase or NoParamUseCase`() {
        projectScope
            .classes()
            .filter { it.resideInPackage("..domain.usecase..") && it.name.endsWith("UseCase") }
            .filter { !isExempt(it.annotations) }
            .assertTrue { koClass ->
                koClass.hasParentInterface { it.name.substringBefore("<") in setOf("UseCase", "NoParamUseCase") }
            }
    }

    @Test
    fun `no file uses Timber directly outside AppLog`() {
        // AGENTS.md mandates AppLog (logv/logd/logi/logw/loge) over raw Timber calls, so the
        // wrapper's caller-attribution fix (inline fns) stays the only place touching Timber.
        projectScope
            .files
            .filter { !it.path.endsWith("/AppLog.kt") && !it.path.endsWith("/FlashcardsApplication.kt") }
            .assertTrue { file ->
                file.imports.none { import -> import.name.startsWith("timber.log") }
            }
    }

    // --- Second pass (grilled 2026-09-18): opt out per-class/function with
    // @ArchConventionExempt("reason") from core:domain.annotation. ---

    private fun isExempt(annotations: List<com.lemonappdev.konsist.api.declaration.KoAnnotationDeclaration>) =
        annotations.any { it.name == "ArchConventionExempt" }

    @Test
    fun `Repository interfaces reside in a domain package`() {
        projectScope
            .interfaces()
            .filter { it.name.endsWith("Repository") }
            .filter { !isExempt(it.annotations) }
            .assertTrue { it.resideInPackage("..domain..") }
    }

    @Test
    fun `Repository implementations reside in a data package`() {
        // Fake*Repository test doubles live in src/test/ and don't need to sit in a data
        // package — only real production implementations are checked here.
        projectScope
            .classes()
            .filter { koClass -> koClass.parents().any { it.name.endsWith("Repository") } }
            .filter { !it.path.contains("/src/test/") }
            .filter { !it.name.startsWith("Fake") }
            .filter { !isExempt(it.annotations) }
            .assertTrue { it.resideInPackage("..data..") }
    }

    @Test
    fun `UiState classes are immutable data classes`() {
        projectScope
            .classes()
            .filter { it.name.endsWith("UiState") }
            .filter { !isExempt(it.annotations) }
            .assertTrue { koClass ->
                koClass.hasDataModifier && koClass.properties().none { it.hasVarModifier }
            }
    }

    @Test
    fun `feature modules never import other feature modules`() {
        projectScope
            .files
            .filter { it.path.contains("/feature/") && it.path.contains("/src/") }
            .assertTrue { file ->
                val ownFeature = file.path.substringAfter("/feature/").substringBefore("/")
                file.imports.none { import ->
                    import.name.startsWith("com.rossomak.flashcards.feature.") &&
                        !import.name.startsWith("com.rossomak.flashcards.feature.$ownFeature.")
                }
            }
    }

    @Test
    fun `DataSource classes reside in a data-layer package`() {
        projectScope
            .classes()
            .filter { it.name.endsWith("DataSource") }
            .filter { !isExempt(it.annotations) }
            .assertTrue { it.resideInPackage("..data..") }
    }

    @Test
    fun `use cases are not referenced from the data layer`() {
        projectScope
            .files
            .filter { it.path.contains("/data/") && it.path.contains("/src/") }
            .assertTrue { file ->
                file.imports.none { import -> import.name.contains(".domain.usecase.") }
            }
    }

    @Test
    fun `HiltViewModel classes do not expose public MutableStateFlow or MutableSharedFlow`() {
        val mutableFlowInitializer = Regex("""=\s*Mutable(StateFlow|SharedFlow)\b""")
        projectScope
            .classes()
            .filter { koClass -> koClass.annotations.any { it.name == "HiltViewModel" } }
            .assertTrue { koClass ->
                koClass.properties().none { property ->
                    !property.hasPrivateModifier &&
                        !property.hasProtectedModifier &&
                        !property.hasInternalModifier &&
                        (
                            property.type?.name == "MutableStateFlow" ||
                                property.type?.name == "MutableSharedFlow" ||
                                mutableFlowInitializer.containsMatchIn(property.text)
                            )
                }
            }
    }

    @Test
    fun `HiltViewModel classes do not inject Repositories, Gateways or DataSources`() {
        // ADR-0051: a ViewModel reaches data seams through use cases only. *Controller
        // collaborators are presentation-side helpers, not data seams, so they're out of scope.
        val dataSeamSuffixes = listOf("Repository", "Gateway", "DataSource")
        projectScope
            .classes()
            .filter { koClass -> koClass.annotations.any { it.name == "HiltViewModel" } }
            .filter { !isExempt(it.annotations) }
            .assertTrue { koClass ->
                koClass.primaryConstructor?.parameters.orEmpty().none { parameter ->
                    val typeName = parameter.type.name.substringBefore('<').removeSuffix("?")
                    dataSeamSuffixes.any { typeName.endsWith(it) }
                }
            }
    }

    @Test
    fun `Route classes are Serializable data classes`() {
        // Scoped to nav-arg Route classes only — core:voice's CaptureRoute is an unrelated
        // audio-routing concept that happens to share the suffix.
        projectScope
            .classes()
            .filter { it.name.endsWith("Route") }
            .filter { !it.path.contains("/core/voice/") }
            .filter { !isExempt(it.annotations) }
            .assertTrue { koClass ->
                koClass.hasDataModifier && koClass.annotations.any { it.name == "Serializable" }
            }
    }

    @Test
    fun `composables outside the theme package never import Color-kt tokens directly`() {
        // core/ui SYSTEMDESIGN.md: "Do not read Color.kt tokens directly in composables" —
        // go through MaterialTheme.colorScheme / MaterialTheme.brandColors instead.
        projectScope
            .files
            .filter { it.path.contains("/core/ui/") && it.path.contains("/composables/") }
            .assertTrue { file ->
                file.imports.none { import -> import.name.contains(".theme.Color") }
            }
    }

    @Test
    fun `functions do not instantiate Random directly outside constructor defaults`() {
        // Randomizing logic takes an injected kotlin.random.Random defaulting to
        // Random.Default in the constructor signature — never called ad hoc in a function body.
        // Test doubles/fixtures and Hilt *Module providers are exempt: that's exactly where
        // Random is legitimately constructed to be handed out via DI.
        val directRandom = Regex("""\bRandom\.Default\b|\bRandom\s*\(""")
        projectScope
            .functions()
            .filter { it.path.contains("/core/domain/") || it.path.contains("/core/data/") }
            .filter { !it.path.contains("/src/test/") }
            .filter { koFunction -> !koFunction.path.substringAfterLast("/").removeSuffix(".kt").endsWith("Module") }
            .filter { !isExempt(it.annotations) }
            .assertFalse { function -> directRandom.containsMatchIn(function.text) }
    }

    @Test
    fun `unit test files are named after the class under test`() {
        // Fake*.kt test doubles live alongside tests but aren't themselves a test class.
        projectScope
            .files
            .filter { it.path.contains("/src/test/") }
            .filter { !it.path.substringAfterLast("/").startsWith("Fake") }
            .assertTrue { file -> file.path.substringAfterLast("/").removeSuffix(".kt").endsWith("Test") }
    }

    @Test
    fun `unit test functions are not prefixed with test or written in snake case`() {
        projectScope
            .functions()
            .filter { it.path.contains("/src/test/") }
            .filter { function -> function.annotations.any { it.name == "Test" } }
            .assertTrue { function ->
                !function.name.startsWith("test", ignoreCase = true) && !function.name.contains("_")
            }
    }

    @Test
    fun `ViewModel test classes declare a MainDispatcherRule`() {
        // `@get:Rule val mainDispatcherRule = MainDispatcherRule()` is always written with an
        // inferred type, so `property.type` is null here — match on the initializer text instead.
        projectScope
            .classes()
            .filter { it.path.contains("/src/test/") && it.name.endsWith("ViewModelTest") }
            .filter { !isExempt(it.annotations) }
            .assertTrue { koClass ->
                koClass.properties().any { property -> property.text.contains("MainDispatcherRule(") }
            }
    }

    @Test
    fun `unit test files use Kotest assertions instead of JUnit Assert`() {
        projectScope
            .files
            .filter { it.path.contains("/src/test/") }
            .assertTrue { file ->
                file.imports.none { import -> import.name.startsWith("org.junit.Assert") }
            }
    }
}
