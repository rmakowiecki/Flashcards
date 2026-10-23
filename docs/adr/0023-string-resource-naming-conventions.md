# String resource naming conventions

## Status

Accepted.

## Decision

- **Ownership**: each `:feature:*` module and `:core:ui` own their own `res/values/strings.xml`. `:core:ui` hosts shared/common strings. `:app` keeps only app-level strings (`app_name`, `splash_tagline`).
- **Key pattern**: `screen_element_role` — no feature-name prefix. The module already scopes the feature; the screen name disambiguates within a module that has multiple screens (e.g. `:feature:browse` has `BrowseScreen`, `CategoryDetailsScreen`, `SubcategoryDetailsScreen`).
  - Example: `login_username_label`, `subcategory_details_filter_button`.
- **Role suffix vocabulary (closed set)**: `_label`, `_button`, `_title`, `_hint`, `_error`, `_message`, `_cd` (contentDescription). The suffix always reflects the UI role the string plays, not its meaning — a string reading "Done" is `_button` if it's button text, regardless of what "done" means semantically.
- **Common strings**: prefixed `common_` instead of a screen name, live in `:core:ui`, same role suffixes. Example: `common_done_button`, `common_cancel_button`, `common_loading_message`.
- **Promotion rule**: a string starts screen-scoped in its owning feature module. The moment a second feature module needs the identical string verbatim, it is promoted to `:core:ui` as `common_x` and both local duplicates are deleted in favor of the shared one. `:core:ui` is not pre-seeded with a guessed common-string list.
- **Format args and `<plurals>`**: no separate suffix. A string with `%1$d` is still whatever role it plays (`_label`, `_message`, ...). `<plurals>` resources take a role suffix like any `<string>` (`streak_card_days_label`, `daily_goal_card_cd`): the `<plurals>` tag tells the resource type apart, but not how the text is used, and one element can need both a label and a content description.
- **Enforcement**: Android Lint's `HardcodedText` check is set to `error`, configured once in the `android-feature.gradle.kts` and `android-core-android.gradle.kts` convention plugins (build-logic), applying to all current and future feature/core modules automatically.
- **Migration**: incremental, touch-driven. No big-bang extraction PR. A screen's hardcoded strings get extracted when that screen is touched for other work; the lint rule prevents new hardcoded strings from being added anywhere in the meantime.

## Context

The codebase has almost no extracted string resources — `app/src/main/res/values/strings.xml` has 2 entries (`app_name`, `splash_tagline`); all other UI text is hardcoded inline in composables. Per [ADR-0011](./0011-multi-module-architecture.md), the project is mid-migration to per-feature Gradle modules, each with its own resource namespace and R class, and features may never depend on each other or on `:app`. Any string-naming convention has to account for that module boundary rather than assuming a single shared `strings.xml`.

## Rationale

- **No feature prefix in keys**: the module (`:feature:auth`, `:feature:browse`, ...) already provides that scope via its own R class — `auth_login_username_label` would be stutter inside a file that's already scoped to `:feature:auth`.
- **Screen name kept in the key**: several feature modules host more than one screen (`:feature:browse`, `:feature:study`), so the module boundary alone isn't enough to avoid key collisions.
- **Fixed role-suffix vocabulary over free-form or omitted suffixes**: a closed set keeps the convention mechanically checkable and prevents drift where the same kind of string (e.g. button text) ends up with inconsistent suffixes across modules. It also directly resolves the "Done as button text" ambiguity from the original ask — the suffix encodes usage context, not word meaning.
- **Demand-driven promotion instead of a pre-seeded common set**: guessing which strings are "common" up front tends to either under-populate (missed obvious duplicates) or over-populate (:core:ui strings nobody reuses, coupling unrelated features to a shared value that may need to diverge later). Promoting on actual second use keeps `:core:ui` demand-driven and avoids premature coupling.
- **Lint enforcement centralized in convention plugins, not per-module `lint.xml`**: matches ADR-0011's whole rationale for convention plugins — one place to set a rule that applies to every module, present and future, instead of copy-pasting a `lint.xml` per module (a precedent for per-module `lint.xml` already exists in `feature/study/lint.xml`, but for an unrelated `UnsafeOptInUsageError` opt-in, not something that should apply project-wide).
- **Incremental, touch-driven migration over a one-shot sweep**: mirrors how ADR-0011's own module migration is being done — incrementally, not as a big-bang rewrite. A dedicated extraction PR would touch nearly every Composable file for low value-per-line; the lint rule already stops the problem from growing while existing screens migrate naturally as they're touched for other work.

## Alternatives considered

- **Single `strings.xml` in `:app`, referenced by all features** — rejected; requires every feature module to depend on `:app` (or have string values passed in as plain params instead of `R.string` refs), which directly violates ADR-0011's "no feature depends on `:app`" rule.
- **Dedicated `:core:strings` module** — rejected; adds a new module for what's realistically a small (<30 entries) shared-string set, more ceremony than routing shared strings through the already-existing `:core:ui`.
- **`feature_screen_element_role` (feature prefix kept)** — rejected as the default; redundant with the module name in the common case. Noted as a fallback if repo-wide grep/translation-export tooling ever needs feature identification without module context.
- **`screen_role` only (element name dropped)** — rejected; collides immediately on any screen with two strings of the same role (e.g. two `_label`s).
- **No fixed role-suffix vocabulary** — rejected; reintroduces the exact ambiguity the original ask flagged (a string's usage context wouldn't be encoded anywhere in its key).
- **Pre-seeded `:core:ui` common-string starter list** — rejected in favor of demand-driven promotion (see Rationale).
- **Explicit `_plural`/`_format` suffixes** — rejected; the role suffix already captures what matters (how the string is used), and `<plurals>` is already a distinct XML construct.
- **Bare `<plurals>` names with no role suffix** — rejected; a bare name loses the usage context the role suffix records, and collides when one element has both a plural label and a plural content description (`streak_card_days_label` and `streak_card_days_cd`).
- **Big-bang extraction PR** — rejected; low value-per-line, touches nearly the entire UI layer at once for a cosmetic change.
- **Per-module `lint.xml` for `HardcodedText`** — rejected; duplicates the same rule across every current and future module, the exact duplication convention plugins exist to avoid.

## Consequences

- New string additions must follow `screen_element_role` (or `common_role` for `:core:ui`) with a role suffix from the closed vocabulary — reviewers can check this mechanically.
- `HardcodedText` lint errors block any new hardcoded UI string from merging once the convention plugins are updated, even though existing hardcoded strings remain until touched.
- No tracking mechanism yet enforces the promotion rule (moving a string to `:core:ui` on second duplicate use) beyond code review — same caveat as ADR-0020's regex-based `check-arg-order.py`, this is a convention backed by review, not by tooling, until/unless a script is written.
- Screens with many strings will have larger per-module `strings.xml` files rather than one giant app-wide file — expected and intentional given ADR-0011's module isolation.
