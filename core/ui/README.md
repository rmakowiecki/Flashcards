# `:core:ui` — Design System & Reusable Composables

This module owns the app's **design system**: theme tokens and every reusable `Flashcards*`
composable that features compose into their screens. Components here are built once, with
care, and reused across the whole app — so the bar is higher than for a one-off screen
composable.

Read this **before** adding or editing anything under `composables/`. It is the contract
these components are held to; the checklist at the bottom is the definition of done.

> Design-system components follow the **official** [androidx Compose Component API
> Guidelines](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-component-api-guidelines.md).
> This is a deliberate, documented deviation from the screen/content ordering in
> [ADR-0020](../../docs/adr/0020-argument-order-conventions.md) — see that ADR for why.

---

## 1. What lives here

```text
core/ui/src/main/…/theme/        Tokens: Spacing, Sizes, CornerRadius, Motion, Color, Type, BrandColors
core/ui/src/main/…/composables/  Reusable Flashcards* components (the design system)
core/ui/src/debug/…/showcase/    Showkase root + sample data / PreviewParameterProviders
```

- **`composables/` is the governed package.** The Konsist rule and `check-arg-order.py`
  exemption both key off this package. Dialogs, navigation, theme, and showcase code are
  *not* design-system components and are not held to these rules.
- **A component never depends on a feature, a ViewModel, or DI.** It is stateless, hoisted,
  and fully previewable in isolation.

---

## 2. Naming & structure

- **Every public component is prefixed `Flashcards`** (`FlashcardsListGroup`,
  `FlashcardsTagChip`). This namespaces the design system, makes it greppable, and signals
  "reusable component" versus an ad-hoc screen composable.
- One component family per file, named after the component (`FlashcardsListRow.kt`).
- Components are **stateless**: no `hiltViewModel()`, no `MutableStateFlow`, no business
  state. All state is hoisted to the caller as parameters + callbacks. The only `remember`
  allowed is for *internal, non-business* animation/interaction state
  (`InteractionSource`, animation specs).

---

## 3. Parameter order (official Compose guidelines)

Design-system components use the **official** ordering, **not** ADR-0020's screen ordering:

```kotlin
@Composable
fun FlashcardsListRow(
    // 1. Required params (no defaults) — passed positionally
    title: String,
    onClick: () -> Unit,
    // 2. modifier — first optional param, named `modifier`, default `Modifier`
    modifier: Modifier = Modifier,
    // 3. Remaining optional params (with defaults)
    subtitle: String? = null,
    enabled: Boolean = true,
    // 4. Trailing @Composable content slot(s) — last, so call-site reads Row(...) { ... }
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
)
```

Rules, verbatim from the guidelines:

- **Required params first.** No defaults → caller must supply → they come first.
- **`modifier: Modifier = Modifier` is the first optional param.** Always named exactly
  `modifier`, always defaulted to `Modifier`.
- **Optional params next.**
- **Trailing `@Composable` content lambda last** — main content, so it reads as a trailing
  lambda. A non-`@Composable` trailing lambda (e.g. `onClick`) is misleading — keep
  `onClick` as a required param, never trailing.
- **The `modifier` is singular and forwarded to the root layout**, never consumed
  internally and dropped, never applied to a child. Internal padding/size goes on inner
  modifiers *chained after* `Modifier` locally, never onto the incoming `modifier`.

> `check-arg-order.py` skips `core/ui/**/composables/**` — do not "fix" a component to
> modifier-first.

---

## 4. Theme tokens — no raw values

**Never hardcode `dp` for spacing / corners / sizes / elevation, and never hardcode
colors.** Always read a token:

| Concern            | Token source                                             |
|--------------------|----------------------------------------------------------|
| Gaps & padding     | `MaterialTheme.spacing.*` (`AppSpacing`)                 |
| Component sizes    | `AppSizes.*` when shared across components; a size only one component uses is a private `val` at the top of that component's file |
| Corners            | `MaterialTheme.cornerRadius.*` (`AppCornerRadius`)       |
| Colors             | `MaterialTheme.colorScheme.*` / `MaterialTheme.brandColors.*` |
| Typography         | `MaterialTheme.typography.*`                             |
| Animation timing   | `FlashcardsMotion.*`                                     |

```kotlin
// NO
.padding(16.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF6B4EFF))

// YES
.padding(MaterialTheme.spacing.normal)
    .clip(RoundedCornerShape(MaterialTheme.cornerRadius.card))
    .background(MaterialTheme.brandColors.someTint)
```

**Enforced by Konsist** for the `composables/` package: raw `dp` literals fail
`staticAnalysis`.

### Opt-out — `@RawDimensions`

Genuinely one-off geometry with no semantic token (a bespoke custom component) may opt out
with a **mandatory reason**:

```kotlin
@RawDimensions("Bespoke confetti burst — coordinates are art, not layout tokens.")
@Composable
fun FlashcardsConfetti(modifier: Modifier = Modifier) { … }
```

Konsist skips any function annotated `@RawDimensions`. `grep -r RawDimensions` audits every
escape. **Colors are never exempt** — always `colorScheme` / `brandColors`.

---

## 5. Performance & stability

These components are reused everywhere, so a stability mistake here multiplies. Hard rules:

- **All public params are stable.** Primitives, `@Immutable`/`@Stable` types, or
  `ImmutableList`/`persistentListOf` (kotlinx.collections.immutable) — **never a bare
  `List<T>`/`Map`/`Set`** in a component signature.
- **Lambdas are stable.** Don't capture unstable state in a lambda you pass down. Prefer
  passing the minimal stable value + a callback.
- **Defer fast-changing state reads** to the lowest scope: use lambda-based modifiers
  (`Modifier.offset { }`, `graphicsLayer { }`) and `derivedStateOf` for computed-from-state
  values rather than reading state high and passing the result down.
- **`remember` with correct keys**; **`derivedStateOf`** for state derived from other state.
- **No side effects in composition.** Use `LaunchedEffect` / `DisposableEffect` with
  correct keys.
- **No `LazyColumn` hidden inside a component.** Laziness stays in the screen so it keeps
  control of `key` / `contentType` / `contentPadding` (see §6).

Strong-skipping + stability metrics wiring is deferred; follow the rules by hand for now.

---

## 6. Lists — two regimes

Lists render in one of two ways. The design system serves both and **never swallows the
`LazyColumn`**.

### 6a. Bounded groups → `FlashcardsListGroup`

For short, bounded sets (settings sections, category lists, selection groups). A non-lazy
`Column` inside one rounded (`cornerRadius.card`), 1px-bordered card. Interior seams between
children are a **1dp gap** showing the container background through — no divider line is
drawn — while the group's own top/bottom edges sit flush with the card border.

```kotlin
FlashcardsListGroup {
    FlashcardsSettingsRow( … )
    FlashcardsSettingsRow( … )
}
```

### 6b. Long lists → row composables + shape helper

For long/scrolling lists (cards, search results, 13+ topics), the **screen owns its
`LazyColumn`**. The design system provides the row composables plus:

- `FlashcardsListItemPosition` — `Single` / `Top` / `Middle` / `Bottom`
- `Modifier.flashcardsListItemShape(position)` — applies the correct corner rounding +
  divider so a stack of lazy items reads as one bordered card.

```kotlin
LazyColumn(contentPadding = …) {
    itemsIndexed(cards, key = { _, c -> c.id }) { index, card ->
        FlashcardsCardRow(
            modifier = Modifier.flashcardsListItemShape(index.positionIn(cards.size)),
            …,
        )
    }
}
```

---

## 7. State hoisting for selectable / expandable rows

- **Selection**: hoist `selected: Boolean` + `onClick` / `onSelectedChange`. Selection tint
  animates via `animateColorAsState`. State lives in the ViewModel/screen — never internal.
- **Expansion** (card rows): the row **owns the expand animation** (`expanded` +
  `onExpandedChange` hoisted, but the row renders/animates the reveal itself via
  `animateContentSize()` / `AnimatedVisibility` and rotates the chevron with
  `animateFloatAsState`). Screens must not reimplement expand.
- All timings/easing come from `FlashcardsMotion`, never raw `300`.

---

## 8. Accessibility

- **`contentDescription` is a required `String` param for semantic icons** (icon-only
  buttons, meaningful images). Decorative graphics pass `null` *with a comment* saying why.
- **48dp minimum touch target** on every interactive element
  (`Modifier.minimumInteractiveComponentSize()` or `sizeIn`).
- **Correct semantics role**, merged so a row is a single node
  (`semantics(mergeDescendants = true)`):

  | Row type            | Semantics                                                        |
  |---------------------|------------------------------------------------------------------|
  | Nav / drill-in      | `clickable(role = Role.Button)`; chevron `contentDescription = null` |
  | Multi-select        | `toggleable(value = selected, role = Role.Checkbox)`; inner checkbox `onClick = null` |
  | Settings toggle     | `toggleable(role = Role.Switch)` on the row                      |
  | Expandable card     | `clickable` + `stateDescription` = "expanded"/"collapsed"        |
  | Stepper             | row not clickable; −/+ are separate labelled buttons             |

---

## 9. Strings

- **No hardcoded user-facing strings inside components** (`HardcodedText` lint = error).
- Components **accept text as `String` params**; the caller resolves `stringResource`. Leaf
  components do not call `stringResource` themselves. `contentDescription` is likewise a
  passed-in `String`.

---

## 10. Previews & Showkase

Two distinct kinds of preview — keep them straight:

- **Showcase entries** — **public** `@ShowkaseComposable(name = …, group = …)` functions.
  Each browser-worthy state is its own entry (a component may have several: default,
  disabled, loading). Groups are **flat and role-based**: `"Buttons"`, `"Inputs"`,
  `"Cards"`, `"Lists"`, `"Feedback"`, `"Containers"`, `"Text"`. `name` is the unprefixed
  variant (`group = "Buttons", name = "Primary"`). These populate the in-app Showkase
  browser (`Showcase.intentOrNull`, debug-only).
- **IDE previews** — **private** `@PreviewLightDark` functions for noisy theme/state
  variants. `skipPrivatePreviews = true` keeps them out of the browser so it stays curated.
  Use `@PreviewParameter` providers for multi-state components.

Every preview body is wrapped so it renders on a real themed background:

```kotlin
@PreviewLightDark
@Composable
private fun FlashcardsPrimaryButtonPreview() {
    FlashcardsTheme {
        Surface {
            FlashcardsPrimaryButton(text = "Start studying", onClick = {})
        }
    }
}
```

**Sample data** — `PreviewParameterProvider`s and any fake models live in **`src/debug`**,
never `main`, so fakes never ship in the release APK.

---

## 11. Reference component

`FlashcardsListRow` (and the `FlashcardsListGroup` container) are the golden examples —
read them as the canonical implementation of everything above before writing a new
component.

---

## 12. Definition of done — per component

- [ ] Official Compose param order (required → `modifier` → optional → trailing slot)
- [ ] `modifier` singular, forwarded to root, never dropped
- [ ] Stateless / hoisted — no ViewModel, no internal business state
- [ ] Stable params (`@Immutable` / primitives / `ImmutableList`)
- [ ] Theme tokens only — no raw `dp`/color — or justified `@RawDimensions("reason")`
- [ ] Correct semantics role + required `contentDescription` + 48dp targets
- [ ] Text via `String` params — no in-component `stringResource`
- [ ] ≥1 public `@ShowkaseComposable(group, name)` + private `@PreviewLightDark` states, all
      `FlashcardsTheme { Surface { … } }`-wrapped
- [ ] Sample data in `src/debug`
- [ ] `./gradlew staticAnalysis` green (Konsist + detekt + Spotless + Lint)
