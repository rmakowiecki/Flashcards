# The website is a Compose Multiplatform wasmJs app over shared design tokens

> Status: accepted

## Context

Firebase Hosting serves the app's website: a home page, the Privacy Policy and the account deletion
steps. The Play Console listing links to the last two, so their paths (`/privacy`,
`/delete-account`) must keep working. They were three hand-written static HTML files with their own
CSS, a light and dark palette that matched nothing in the app, and no shared code with it.

The site is about to grow (a real landing page, screenshots, motion), and the project is also a
portfolio piece, so it should look like the app and be built with the same tools rather than drift
as a second, unrelated codebase.

## Decision

**The site is a Kotlin Multiplatform module, `:web`, with a single `wasmJs` target**, drawn with
Compose Multiplatform. Kotlin compiles it to WebAssembly plus a small JavaScript loader;
`wasmJsBrowserDistribution` produces the static files Firebase Hosting serves. `wasmJs` is the
target Compose Multiplatform for web is built around; the older `js` target compiles to JavaScript
and is supported less well by Compose.

**The design tokens move into a new KMP library, `:core:designsystem`**, built for Android and
`wasmJs`. It holds the palette, the light and dark color schemes, the brand gradients, the semantic
colors, spacing, sizes, corner radii, motion and the Roboto type scale, in the package
`com.rossomak.flashcards.core.designsystem.theme`. `:core:ui` re-exports it through `api`, so feature
modules keep depending on `:core:ui` alone. What needs Android or the domain stays in `:core:ui`:
`FlashcardsTheme` (dynamic color) and the Category, Difficulty and Rating colors. Components stay in
`:core:ui` too; only tokens are shared for now.

**Roboto is a Compose resource** in `:core:designsystem`, so both platforms load the same file. See
the amendment to [ADR-0065](0065-typography-is-the-stock-m3-scale-over-bundled-roboto.md).

**One page per URL path, no navigation library.** Hosting rewrites every path to `index.html`; the
app reads `window.location.pathname`, picks the page, and keeps the URL in step with the History
API (push on a link, `popstate` on back and forward). A trailing slash and the old `.html` suffix
are accepted, an unknown path shows the home page, and either is rewritten in place to the page's
canonical path.

**Every page sits on the brand screen gradient with on-gradient white text**, in both light and
dark mode, because the gradient never flips. The site's `WebTheme` is the shared tokens fixed to the
dark scheme. Text is selectable, since the canvas has no native selection.

**Build-logic gains two convention plugins**: `kmp-library` (Android through AGP's
`com.android.kotlin.multiplatform.library`, plus `wasmJs`) and `kmp-web-app` (a `wasmJs` browser
executable whose unit tests run in headless Chrome, since Compose cannot start under Node). Both apply the existing Compose compiler and static
analysis conventions.

**Deploys build the site.** `firebase.json` serves `web/build/dist/wasmJs/productionExecutable` and
runs the Gradle distribution task as a hosting `predeploy` step, so no generated file is committed.
The Bitrise PR check builds the site and runs its tests.

## Alternatives considered

- **Keep hand-written HTML.** Smallest and best for search engines, but the brand would be restated
  in CSS and drift from the app, and the site could not reuse any of the app's tokens.
- **Kotlin/JS with Compose HTML (DOM).** Real DOM text, but it is a separate component model from
  the Compose UI the app uses, so nothing visual would be shared; it also gets less investment.
- **The `js` target for Compose.** Wider browser support for very old browsers, but slower and a
  secondary target for Compose Multiplatform.
- **Copy the tokens into `:web`.** Less churn now, but two definitions of the brand. Extracting the
  module is the change worth making once, before the site grows.
- **Keep the old package name in the new module.** Avoids touching imports, but splits one package
  across two modules, which is confusing to read and to enforce rules on.

## Consequences

- The site's text is drawn on a canvas: search engines and screen readers read little of it.
  `index.html` carries a title, a description and a `<noscript>` fallback with the contact address.
  If search visibility starts to matter, the legal pages can be prerendered.
- The first visit downloads the WebAssembly runtime and Compose (a few megabytes); later visits use
  the browser cache. `wasmJs` needs a browser with WebAssembly garbage collection, which every
  current major browser has.
- Compose Multiplatform's artifacts resolve to the androidx ones on Android, so the app's Compose
  versions still come from the Compose BOM; `material3` for Multiplatform ships only alphas, matching
  the app's own alpha `material3`.
- About a hundred files changed their theme imports from `core.ui.theme` to
  `core.designsystem.theme`.
- Amends [ADR-0011](0011-multi-module-architecture.md) (module and convention plugin tables) and
  [ADR-0065](0065-typography-is-the-stock-m3-scale-over-bundled-roboto.md).
