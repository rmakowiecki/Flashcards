package com.rossomak.flashcards.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoTypeArgumentDeclaration
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import io.kotest.matchers.shouldBe
import org.junit.Test

/**
 * Architecture rules enforced across the whole project (ADR-0020 arg-order stays in the
 * Python script for now). Rules are intentionally conservative in this first pass so the
 * suite is green on existing code; tighten in follow-up PRs.
 */
class ArchitectureKonsistTest {

    // The `morpheus` submodule is a separate project with rules of its own, so these never read it.
    private val projectScope = Konsist.scopeFromProject().slice { !it.projectPath.removePrefix("/").startsWith("morpheus/") }

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
        // the project mandates AppLog (logv/logd/logi/logw/loge) over raw Timber calls
        projectScope
            .files
            .filter { !it.path.endsWith("/AppLog.kt") && !it.path.endsWith("/FlashcardsApplication.kt") }
            .assertTrue { file ->
                file.imports.none { import -> import.name.startsWith("timber.log") }
            }
    }

    @Test
    fun `only the backdrop blur wrapper imports Haze`() {
        // ADR-0062: Haze stays an implementation detail of the blurBackdrop* modifiers.
        projectScope
            .files
            .filter { !it.path.endsWith("/core/ui/backdrop/BlurBackdrop.kt") }
            .assertTrue { file ->
                file.imports.none { import -> import.name.startsWith("dev.chrisbanes.haze") }
            }
    }

    @Test
    fun `stock M3 app bars are used only inside core ui`() {
        // ADR-0072: other modules use the :core:ui bars; TopAppBarDefaults and the scroll behavior stay allowed.
        val stockAppBars = listOf(
            "TopAppBar",
            "CenterAlignedTopAppBar",
            "MediumTopAppBar",
            "LargeTopAppBar",
            "MediumFlexibleTopAppBar",
            "LargeFlexibleTopAppBar",
            "TwoRowsTopAppBar",
        )
        val stockAppBarReference = Regex("""androidx\.compose\.material3\.(${stockAppBars.joinToString("|")})\b""")
        projectScope
            .files
            .filter { !it.path.contains("/core/ui/src/main/") }
            .assertTrue { file ->
                file.imports.none { import -> import.isWildcard && import.name.removeSuffix(".*") == "androidx.compose.material3" } &&
                    !stockAppBarReference.containsMatchIn(file.text)
            }
    }

    @Test
    fun `destinations are declared only through guardedNavComposable`() {
        // A bare composable<T> exposes the raw NavController, which navigates even while the screen is leaving.
        projectScope
            .files
            .filter { !it.path.endsWith(GUARDED_NAV_COMPOSABLE_FILE) }
            .assertTrue { file ->
                file.imports.none { import -> import.name == "androidx.navigation.compose.composable" }
            }
    }

    @Test
    fun `production code navigates only through an EntryNavigator`() {
        // The bottom bar's tab switch has no back-stack entry, so it is the one raw call.
        val sanctionedRawCalls = listOf("tabNavController.navigate(route, tabNavOptions)")
        val rawControllerCall = Regex("""\b\w*[nN]avController\s*\.\s*(navigate|popBackStack|navigateUp)\b""")
        projectScope
            .files
            .filter { file -> !file.path.contains("/src/test/") && !file.path.contains("/src/androidTest/") }
            .filter { file -> NAVIGATION_GUARD_FILES.none { file.path.endsWith(it) } }
            .assertFalse { file ->
                val text = sanctionedRawCalls.fold(file.text) { remaining, call -> remaining.replace(call, "") }
                rawControllerCall.containsMatchIn(text)
            }
    }

    @Test
    fun `a navigation callback makes at most one navigator call`() {
        // After the first call the entry is no longer on top, so a second is always dropped; use a
        // method like navigateStacked. Heuristic: two calls sharing their innermost enclosing brace.
        val navigatorCall = Regex("""\bnavigator\s*\.\s*(navigate\w*|popBackStack)\s*\(""")
        projectScope
            .files
            .filter { file -> !file.path.contains("/src/test/") }
            .assertFalse { file ->
                val enclosingBraces = navigatorCall.findAll(file.text).map { enclosingBraceIndex(file.text, it.range.first) }.toList()
                enclosingBraces.size != enclosingBraces.toSet().size
            }
    }

    /** Index of the innermost `{` still open at [position], or -1 at the top level. */
    private fun enclosingBraceIndex(text: String, position: Int): Int {
        val openBraces = ArrayDeque<Int>()
        text.substring(0, position).forEachIndexed { index, char ->
            when (char) {
                '{' -> openBraces.addLast(index)
                '}' -> openBraces.removeLastOrNull()
            }
        }
        return openBraces.lastOrNull() ?: -1
    }

    private fun typeArgumentNames(typeArguments: List<KoTypeArgumentDeclaration>?): List<String> =
        typeArguments.orEmpty().flatMap { listOf(it.name) + typeArgumentNames(it.typeArguments) }

    private fun isExempt(annotations: List<com.lemonappdev.konsist.api.declaration.KoAnnotationDeclaration>) =
        annotations.any { it.name == "ArchConventionExempt" }

    /**
     * The Compose stability config (`config/compose/stability-config.conf`) declares read-only
     * `List`/`Set`/`Map` stable, trusting that a collection published into screen state or a
     * domain model is never mutated afterwards. The compiler matches `MutableList`/`MutableSet`/
     * `MutableMap` against those entries too, so a mutable collection type would be reported
     * stable while breaking that promise — the rules below reject it instead.
     */
    private val mutableCollectionType =
        Regex("""\b(Mutable(List|Set|Map|Collection)|ArrayList|HashMap|HashSet|LinkedHashMap|LinkedHashSet)\b""")

    private val mutableCollectionFactory = "mutable(List|Set|Map)Of|arrayListOf|hashMapOf|hashSetOf|linkedMapOf|linkedSetOf"

    /**
     * An initializer that builds a mutable collection, for a property whose type is inferred. A
     * trailing `also`/`apply` block returns its receiver, so a copy ending in one stays mutable.
     */
    private val mutableCollectionInitializer = Regex(
        """^\s*(object\s*:\s*)?($mutableCollectionFactory|${mutableCollectionType.pattern})\b""" +
            """|\.toMutable(List|Set|Map)\(\s*\)(\s*\.(also|apply)\s*\{[\s\S]*})?\s*$""",
    )

    /**
     * Private properties are skipped: a private collection only reaches a composable parameter or
     * screen state through another declaration, which these rules check in turn. Konsist leaves
     * `type` null for an inferred property (`val ids = mutableListOf<String>()`), so that one is
     * judged by its initializer instead.
     */
    private fun KoClassDeclaration.hasMutableCollectionProperty(): Boolean {
        val publishedProperties = properties().filter { !it.hasPrivateModifier }
        val propertyTypes = publishedProperties.mapNotNull { it.type?.text } +
            primaryConstructor?.parameters.orEmpty().map { it.type.text }
        val inferredInitializers = publishedProperties.filter { it.type == null }.mapNotNull { it.value }
        return propertyTypes.any { mutableCollectionType.containsMatchIn(it) } ||
            inferredInitializers.any { mutableCollectionInitializer.containsMatchIn(it) }
    }

    private fun isComposeModuleMainSource(path: String) =
        (path.contains("/src/main/") || path.contains("/src/commonMain/") || path.contains("/src/wasmJsMain/")) &&
            (
                path.contains("/feature/") ||
                    path.contains("/core/ui/") ||
                    path.contains("/core/designsystem/") ||
                    path.contains("/app/src/main/") ||
                    path.contains("/debug-tools/") ||
                    path.contains("/web/src/")
                )

    private fun isMainSource(path: String) =
        path.contains("/src/main/") || path.contains("/src/commonMain/") || path.contains("/src/wasmJsMain/")

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
    fun `ScreenState classes are immutable data classes`() {
        projectScope
            .classes()
            .filter { it.name.endsWith("ScreenState") }
            .filter { !isExempt(it.annotations) }
            .assertTrue { koClass ->
                koClass.hasDataModifier && koClass.properties().none { it.hasVarModifier }
            }
    }

    @Test
    fun `Compose modules declare no mutable collection types`() {
        // Class properties, constructor parameters and function parameters (composables included)
        // in :feature:*, :core:ui and :app. Local variables stay free to build a collection
        // mutably before returning it as a read-only type.
        projectScope
            .classes()
            .filter { isComposeModuleMainSource(it.path) }
            .assertFalse { koClass -> koClass.hasMutableCollectionProperty() }
        projectScope
            .functions()
            .filter { isComposeModuleMainSource(it.path) }
            .assertFalse { function -> function.parameters.any { mutableCollectionType.containsMatchIn(it.type.text) } }
    }

    @Test
    fun `no cast to a mutable collection type`() {
        // A cast would sidestep the declared-type rules above and mutate a collection that
        // the Compose stability config treats as stable.
        val mutableCollectionCast = Regex("""\bas\??\s+${mutableCollectionType.pattern}""")
        projectScope
            .files
            .filter { isMainSource(it.path) }
            .assertFalse { file -> mutableCollectionCast.containsMatchIn(file.text) }
    }

    @Test
    fun `no read-modify-write assignment on a value holder`() {
        // `x.value = x.value.copy(...)` loses a concurrent write between the read and the
        // write; `x.update { it.copy(...) }` retries atomically instead.
        val readModifyWrite = Regex("""(\b[\w.]+)\.value\s*=\s*\1\.value\b""")
        projectScope
            .files
            .filter { isMainSource(it.path) }
            .assertFalse { file -> readModifyWrite.containsMatchIn(file.text) }
    }

    @Test
    fun `domain models have no mutable collection properties`() {
        domainModelClasses().assertFalse { koClass -> koClass.hasMutableCollectionProperty() }
    }

    @Test
    fun `domain models have no var properties`() {
        // The stability config trusts the whole domain model package, so a `var` would let a
        // retained instance change under a composable that skipped because it saw the same one.
        domainModelClasses().assertFalse { koClass -> koClass.properties().any { it.hasVarModifier } }
    }

    @Test
    fun `mutable collection rule catches declared and inferred published properties`() {
        val flaggedClasses = Konsist.scopeFromFile(MUTABLE_COLLECTION_FIXTURE)
            .classes()
            .filter { it.hasMutableCollectionProperty() }
            .map { it.name }
            .toSet()

        flaggedClasses shouldBe setOf(
            "DeclaredMutableProperty",
            "InferredMutableProperty",
            "InferredCopiedProperty",
            "ChainedApplyCopyProperty",
            "ChainedAlsoCopyProperty",
            "MutableConstructorParameter",
        )
    }

    private fun domainModelClasses() =
        projectScope
            .classes()
            .filter { it.path.contains("/core/domain/src/main/") && it.resideInPackage("..domain.model..") }

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
    fun `only debug-only sources import the debug-tools module`() {
        // :debug-tools reaches :app only through debugImplementation and profilingImplementation, so an
        // import of it from any other source set compiles in debug but breaks the release build. This
        // fails the import in static analysis instead, long before a release build runs.
        projectScope
            .files
            .filter { file -> !isDebugOnlySource(file.path) }
            .assertTrue { file ->
                file.imports.none { import -> import.name.startsWith("com.rossomak.flashcards.debugtools.") }
            }
    }

    private fun isDebugOnlySource(path: String) =
        path.contains("/debug-tools/") || path.contains("/app/src/debug/") || path.contains("/app/src/profiling/")

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
        // Type arguments are checked too, so wrapping a seam in Lazy<T> or Provider<T> can't hide it.
        val dataSeamSuffixes = listOf("Repository", "Gateway", "DataSource")
        projectScope
            .classes()
            .filter { koClass -> koClass.annotations.any { it.name == "HiltViewModel" } }
            .filter { !isExempt(it.annotations) }
            .assertTrue { koClass ->
                koClass.primaryConstructor?.parameters.orEmpty().none { parameter ->
                    val typeNames = listOf(parameter.type.name) + typeArgumentNames(parameter.type.typeArguments)
                    typeNames.any { name ->
                        val typeName = name.substringBefore('<').removeSuffix("?")
                        dataSeamSuffixes.any { typeName.endsWith(it) }
                    }
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
    fun `text is styled only through theme typography tokens`() {
        // Text takes a MaterialTheme.typography slot or its *Emphasized twin, or a theme constant
        // (Typography.code, InlineCodeSpanStyle, EmphasizedSpanStyle), never an ad hoc weight,
        // size, family or spacing. The website (:web) is held to it like the app; only the token
        // definitions (:core:designsystem, core/ui/theme/) are exempt. Previews are held to it
        // too. FlashcardsAvatar is the one exception: its initials scale with the avatar's diameter, which no fixed slot can do.
        val adHocTypography = Regex(
            """\b(fontWeight|fontSize|fontFamily|letterSpacing|lineHeight)\s*=""" +
                """|\bTextStyle\(""" +
                """|\btypography\.\w+\.copy\(""",
        )
        projectScope
            .files
            .filter { file -> isComposeModuleMainSource(file.path) }
            .filter { file -> !file.path.contains("/core/ui/theme/") && !file.path.contains("/core/designsystem/") }
            .filter { file -> !file.path.endsWith("/FlashcardsAvatar.kt") }
            .assertFalse { file -> adHocTypography.containsMatchIn(file.text) }
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

    private companion object {
        const val MUTABLE_COLLECTION_FIXTURE = "konsist/fixtures/MutableCollectionProperties.kt"
        const val GUARDED_NAV_COMPOSABLE_FILE = "/ui/navigation/GuardedNavComposable.kt"
        val NAVIGATION_GUARD_FILES = listOf(GUARDED_NAV_COMPOSABLE_FILE, "/ui/navigation/EntryNavigator.kt")
    }
}
