# Bottom sheet rebuilt on M3's standalone `BottomSheet`, narrowing the no-drag-handle rule to modal sheets

## Decision

`FlashcardsBottomSheet` is rebuilt on Material 3's standalone `BottomSheet` (1.5 line,
`material3 = "1.5.0-alpha23"`), replacing the hand-rolled docked `Surface` it was before.

**Shape:** the caller owns a `FlashcardsBottomSheetState` (built with
`rememberFlashcardsBottomSheetState`), an `onDismissRequest`, and one content slot. No header slot,
no pinned-actions region.

**State is hidden/expanded only** — never `PartiallyExpanded`.

**One flag controls two shapes.** `dismissible`, set once on the state builder, drives everything:

- `true` (default): fully interactive — swipeable, back-dismissible, M3's default drag handle.
- `false`: no gestures, no back-dismiss, no drag handle — a handle that can't do anything is worse
  than none.

**Single source of truth.** `FlashcardsBottomSheetState` bundles the M3 `SheetState` with the
`dismissible` value it was built with. `SheetState.enabledValues` is `internal`, so there is no way
to recover dismissibility from a bare `SheetState` after construction. An earlier draft passed
`dismissible` separately to both the state builder and `FlashcardsBottomSheet`, and the two could
disagree: a state built non-dismissible combined with the composable's own `dismissible = true`
made M3's predictive-back handler call `state.hide()` unconditionally, which throws once hidden is
excluded from `enabledValues` (verified against the M3 1.5.0-alpha23 source). Bundling the flag
with the state it describes makes that impossible.

**Content padding, shape and color are applied internally** — `MaterialTheme.spacing.normal`
around content (`BottomSheet` applies none itself beyond the bottom system-bar inset), top-rounded
`cornerRadius.large` shape, `surfaceContainerLowest` container — the same pairing `FlashcardsDialog`
uses. Predictive back, swipe, and the inset itself come from `BottomSheet` unmodified.

**Drag handle rule narrowed to modal sheets.** The house rule against bottom-sheet drag handles
applies to modal sheets, where the scrim already signals dismissibility. It doesn't apply here: a
dismissible standalone sheet has no scrim, so the handle is the only such signal; a non-dismissible
one shows no handle since nothing moves.

**Height is the caller's problem.** The expanded anchor is derived from content's measured height;
taller content clips rather than scrolling. A caller with variable-length content makes its own
column scrollable.

## Rejected alternative: `BottomSheetScaffold`

Equally experimental, not a stabler fallback, and its `containerColor` is transparent-capable so
"opaque container" isn't a real objection. The real gap: no back/predictive-back handling at all —
no `backHandlerEnabled`, no `onDismissRequest` — so back-dismiss would need a hand-rolled
`BackHandler` calling `hide()` directly, reintroducing the same unguarded-crash risk this rebuild
avoids. It also imposes an arbitrary peek height and takes over the whole screen (top bar, body,
snackbar host).

## Rejected alternative: `ModalBottomSheet`

Brings back exactly what this component avoids: a separate `Dialog` window and a scrim, wrong for
a panel that composes alongside its screen's own content.

## Consequences

- Showkase entry is public (`@ShowkaseComposable`); previews cover both shapes in both themes.
- Every M3 `BottomSheet`/`SheetState` call site needs `@OptIn(ExperimentalMaterial3Api::class)`
  until the API graduates.

## Addendum: content now caps and scrolls instead of clipping

The original decision ("Height is the caller's problem... a caller with variable-length content
makes its own column scrollable") turned out to be harder to execute than it reads. A first attempt
simply added `Modifier.verticalScroll` to a sheet's content column and it crashed — Compose's
"vertically scrollable component was measured with an infinity maximum height constraints"
exception — the moment content grew past the viewport. That attempt was reverted and
`FlashcardsBottomSheet`'s own doc was (wrongly, in hindsight) tightened to say content should just
be kept short instead.

Root cause: `BottomSheet`'s own internal `draggableAnchors` modifier measures its content at
unbounded height on purpose, to learn the content's true natural size for its `Expanded` anchor math
(`fullHeight - sheetHeight`). Any `verticalScroll` placed on content *inside* that measurement — which
is where a caller's `content` slot lives — inherits that unbounded constraint and throws.

Fix: `FlashcardsBottomSheet` now wraps its entire body (including the `BottomSheet` call) in a
`BoxWithConstraints`, positioned *above* that internal relaxation point, where the incoming height
constraint is still the real, bounded one the caller's own outer `Box(Modifier.fillMaxSize())`
provides. `maxHeightFraction` (default `0.8f`) is multiplied against that bound to produce a concrete
`Dp` cap, applied via `Modifier.heightIn(max = ...)` on the content column *before* `verticalScroll`.
`heightIn` intersects constraints rather than scaling a fraction of them, so it clamps correctly even
where the constraint it's intersected against is later relaxed to infinity downstream — the piece the
first attempt was missing. `content` shorter than the cap is unaffected; `verticalScroll` only
engages once actual content exceeds it.

## Addendum: the session panels use it too

The Rated and Fast session panels were built on `BottomSheetScaffold` as fixed, non-swipeable panels.
That broke in two ways. `BottomSheetScaffold` pads no window inset into its sheet, so 3-button
navigation covered the panel's buttons. And because the content's height equalled
`sheetPeekHeight`, M3 created no `PartiallyExpanded` anchor, so the sheet settled `Expanded` and the
scaffold placed snackbars at the screen bottom, over the panel.

Both panels now use a non-dismissible `FlashcardsBottomSheet` through `StudySessionScaffold`, the
same shape as the Session Summary panel: the sheet is an unaligned sibling of a plain `Scaffold`, and
its measured height pads the body and the `SnackbarHost`. No screen uses `BottomSheetScaffold` any
more.

