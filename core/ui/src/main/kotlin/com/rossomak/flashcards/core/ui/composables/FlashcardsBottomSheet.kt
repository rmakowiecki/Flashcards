package com.rossomak.flashcards.core.ui.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheet
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.designsystem.theme.cornerRadius
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme

/**
 * A non-modal bottom sheet docked wherever the caller places it — built on M3's standalone
 * `BottomSheet`, not `ModalBottomSheet` or `BottomSheetScaffold`. It renders in the composition
 * hierarchy rather than a separate dialog window, so there is no scrim: the caller decides what
 * else is visible while the sheet is open. See docs/adr/0043 for why this shape was chosen over
 * the scaffold and the modal sheet.
 *
 * Deliberately not a scaffold and deliberately thin: it owns no `topBar`, no header slot and no
 * pinned-actions region — most sheets built on this will not need pinned actions at all, and the
 * caller is better placed to lay out its own [content]. This wrapper adds nothing but the app's own
 * design tokens on top of a bare `BottomSheet` call; every gesture, animation and predictive-back
 * behaviour is M3's, unmodified.
 *
 * **Placement is load-bearing — read this before wiring a call site.** `BottomSheet` positions
 * itself: its `draggableAnchors` modifier reads the *incoming* `constraints.maxHeight` (i.e. its
 * parent's bounded height) as the sheet's "full height", and internally translates its content down
 * by `fullHeight - sheetHeight` to land at the bottom — but the layout node it reports back to that
 * same parent is only the *content's own* natural size, not `fullHeight`. So:
 * - **Dock it as a plain, unaligned sibling** of the screen content in a `Box(Modifier.fillMaxSize())`
 *   — `Box(Modifier.fillMaxSize()) { ScreenContent(); FlashcardsBottomSheet(state = ...) { ... } }`.
 *   `Box`'s default alignment (`TopStart`, i.e. no `Modifier.align` at all) is correct: the sheet
 *   already puts itself at the bottom internally.
 * - **Never wrap it in `Modifier.align(Alignment.BottomCenter)`.** Confirmed by reading
 *   `androidx.compose.material3.internal.DraggableAnchors` (M3 1.5.0-alpha23): doing so bottom-aligns
 *   the small *reported* size on top of the sheet's own internal bottom offset — a double offset that
 *   pushes the real content roughly `2×(fullHeight - sheetHeight)` down, off-screen or clipped
 *   depending on content height. This was a real, reproduced bug in this codebase (not an M3 bug) —
 *   every sheet built on this component had it from the first commit, and no amount of tuning the
 *   *content*'s size ever could have fixed a bug at the call site.
 * - The `Box`'s own height must actually be bounded (e.g. `fillMaxSize()` inside a bounded parent,
 *   not `wrapContentHeight()`) — `fullHeight` above comes from that constraint, not the sheet's
 *   content.
 *
 * **Two shapes, one flag.** [state] is built with [rememberFlashcardsBottomSheetState], whose
 * `dismissible` parameter drives everything at once — [FlashcardsBottomSheetState.dismissible] is
 * read here to set `gesturesEnabled`, `backHandlerEnabled`, and whether the default drag handle
 * even renders:
 * - **Dismissible** (the default): fully interactive — swipeable, back-dismissible (including
 *   predictive back), with M3's default drag handle. This is the settings-sheet shape (Preview
 *   study session screen).
 * - **Non-dismissible**: no gestures, no back-dismiss, and no drag handle at all — a handle that
 *   can't do anything is worse than none. This is the permanently-docked-panel shape
 *   (`RatedStudySessionScreen`'s card/rating panel is a named future consumer).
 *
 * An earlier version of this component took `dismissible` (and a separate `draggable`) as
 * independent parameters on *both* [FlashcardsBottomSheet] and the state builder. Review found
 * that unsound — verified against M3 1.5.0-alpha23 source, a `state` built non-dismissible
 * combined with this composable's own `dismissible = true` made the predictive-back handler call
 * `state.hide()` unconditionally, which throws once hidden is excluded from `enabledValues`. Two
 * independent copies of the same fact with nothing keeping them in sync. [FlashcardsBottomSheetState]
 * exists so there is exactly one flag, set once, at the state builder — this composable only reads
 * it back.
 *
 * Everything else this component adds on top of a bare `BottomSheet` call is the app's own design
 * tokens — top-rounded [MaterialTheme.cornerRadius.large] shape,
 * `MaterialTheme.colorScheme.surfaceContainerLowest` container, [MaterialTheme.spacing.normal]
 * padding around [content] (`BottomSheet` applies none itself beyond the bottom system-bar inset —
 * content butts against the sheet's edges otherwise) — the shape/color pairing
 * [FlashcardsDialog][com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardsDialog] also
 * uses. Predictive back, swipe, the bottom system-bar inset, and (for a dismissible sheet) the drag
 * handle's expand/collapse/dismiss accessibility actions all come from `BottomSheet` itself,
 * unmodified.
 *
 * **[content] is capped at [maxHeightFraction] of the available height, scrolling past that —
 * never clipped.** [rememberFlashcardsBottomSheetState] only ever enables `Hidden`/`Expanded` —
 * never `PartiallyExpanded` — so "expanded" always means [content] measured up to that cap, not an
 * unbounded natural height. The cap is read from a [BoxWithConstraints] wrapping this entire
 * composable, deliberately *outside* the `BottomSheet` call it wraps: `BottomSheet`'s own internal
 * `draggableAnchors` modifier (see the placement note above) relaxes the height constraint it hands
 * its content down to effectively unbounded, so it can measure that content's true natural height
 * for its anchor math — measuring *inside* that point (as an earlier, reverted attempt did, with a
 * bare `verticalScroll` and no cap) crashes with Compose's "measured with infinity maximum height"
 * exception the moment content exceeds the viewport. Reading [BoxWithConstraints.maxHeight] here
 * instead, above that relaxation point, sees the real bounded height the caller's own outer
 * `Box(Modifier.fillMaxSize())` provides (see the placement note above) — `Modifier.heightIn(max = ...)`
 * against that concrete value clamps correctly even where the inner measurement is later relaxed to
 * infinity, since an upper bound intersected with infinity is still that bound. See docs/adr/0043's
 * addendum for the fuller account of why the naive attempt broke and what fixed it.
 *
 * @param state Owns the sheet's hidden/expanded value and its dismissibility. Build it with
 *   [rememberFlashcardsBottomSheetState].
 * @param onDismissRequest Invoked when the sheet is swiped, predictive-backed, or drag-handle-tapped
 *   to hidden. Never called for a non-dismissible [state], since none of those paths can reach
 *   hidden in that case.
 * @param maxHeightFraction [content]'s height cap, as a fraction (`0f..1f`) of the screen height the
 *   caller's outer `Box` provides. Past this, [content] scrolls internally rather than clipping.
 *   Defaults to [DEFAULT_MAX_HEIGHT_FRACTION] — generous enough for every sheet built on this
 *   component so far, while still leaving a visible sliver of whatever's behind the sheet, since a
 *   non-modal sheet (no scrim) reading as a full-screen takeover would misrepresent that it's
 *   non-modal at all.
 * @param content The sheet's body. One slot, no header, no pinned-actions region.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardsBottomSheet(
    state: FlashcardsBottomSheetState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeightFraction: Float = DEFAULT_MAX_HEIGHT_FRACTION,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints {
        val cappedHeight = maxHeight * maxHeightFraction
        BottomSheet(
            state = state.sheetState,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            gesturesEnabled = state.dismissible,
            backHandlerEnabled = state.dismissible,
            dragHandle = if (state.dismissible) {
                { BottomSheetDefaults.DragHandle() }
            } else {
                null
            },
            shape = RoundedCornerShape(
                topStart = MaterialTheme.cornerRadius.large,
                topEnd = MaterialTheme.cornerRadius.large,
            ),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            content = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = cappedHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = MaterialTheme.spacing.normal, vertical = MaterialTheme.spacing.normal),
                    content = content,
                )
            },
        )
    }
}

/** Default [FlashcardsBottomSheet] `maxHeightFraction` — see that parameter's own doc. */
private const val DEFAULT_MAX_HEIGHT_FRACTION = 0.8f

/**
 * [FlashcardsBottomSheet]'s single source of truth for dismissibility — a transparent wrapper
 * around the M3 [SheetState] plus the [dismissible] flag it was built with, produced only by
 * [rememberFlashcardsBottomSheetState]. `SheetState.enabledValues` is `internal`, so
 * [FlashcardsBottomSheet] cannot introspect an arbitrary [SheetState] to recover whether it was
 * built dismissible — bundling the flag alongside the state it describes is what makes the two
 * impossible to desynchronize. [sheetState] is exposed directly (not hidden behind delegate
 * methods) for anything a caller needs from raw `SheetState` — `currentValue`, `isVisible`,
 * `show()`/`hide()` — matching this component's own "deliberately thin" scope.
 *
 * Constructor is `internal` and [rememberFlashcardsBottomSheetState] is the sole factory: a public
 * constructor (or a public `copy()`, which a `data class` would generate at the same visibility)
 * would let a caller pair a `sheetState` with a `dismissible` value it wasn't actually built with,
 * reintroducing the exact desync this type exists to rule out. `private` was tried first but a
 * class-member `private` constructor is scoped to the class body, not the file — unlike top-level
 * `private` declarations — so even [rememberFlashcardsBottomSheetState] in this same file couldn't
 * call it; `internal` is the tightest visibility Kotlin allows a same-file-but-not-same-class caller.
 * [ConsistentCopyVisibility] pins the generated `copy()` to that same `internal` visibility — plain
 * `data class` only does this by default in a future Kotlin language version (KT-11914); without the
 * annotation, `copy()` stays public today and reopens the exact hole this constructor closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@ConsistentCopyVisibility
data class FlashcardsBottomSheetState internal constructor(
    val sheetState: SheetState,
    val dismissible: Boolean,
)

/**
 * Builds the [FlashcardsBottomSheetState] [FlashcardsBottomSheet] expects: hidden and expanded
 * only, never partially expanded. Raw `rememberBottomSheetState()` defaults to enabling
 * `PartiallyExpanded` too, which this shape never wants (ADR-0043) — this helper exists so no call
 * site has to remember to exclude it.
 *
 * When [dismissible] is `false`, hidden is dropped from the enabled set entirely — not just
 * blocked at the [FlashcardsBottomSheet] call site — so the state starts at expanded and calling
 * `SheetState.hide()` on it throws, per M3's own contract. A `confirmValueChange` veto is also
 * attached, rejecting a settle at hidden, since `BottomSheet`'s drag physics always define a hidden
 * anchor for the gesture to rubber-band against, independent of the enabled set — moot here since
 * [FlashcardsBottomSheet] also disables gestures for a non-dismissible sheet, but kept as a
 * second line of defense against a direct `sheetState.hide()` call.
 *
 * @param dismissible Whether the sheet can ever be hidden — swiped, back-dismissed, or
 *   programmatically. `false` builds the permanently-docked-panel shape.
 * @param initiallyExpanded Whether a dismissible sheet starts expanded instead of hidden. Ignored
 *   when [dismissible] is `false`, which always starts expanded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberFlashcardsBottomSheetState(
    dismissible: Boolean = true,
    initiallyExpanded: Boolean = false,
): FlashcardsBottomSheetState {
    val enabledValues = if (dismissible) {
        setOf(SheetValue.Hidden, SheetValue.Expanded)
    } else {
        setOf(SheetValue.Expanded)
    }
    val initialValue = if (dismissible && !initiallyExpanded) SheetValue.Hidden else SheetValue.Expanded
    val sheetState = rememberBottomSheetState(
        initialValue = initialValue,
        enabledValues = enabledValues,
        confirmValueChange = { value -> dismissible || value != SheetValue.Hidden },
    )
    return remember(sheetState, dismissible) { FlashcardsBottomSheetState(sheetState, dismissible) }
}

/** Number of mock rows rendered by [MockScreenContent] below the headline. */
private const val MOCK_SCREEN_CONTENT_ROW_COUNT = 3

/**
 * Stand-in for a real screen's body, so the showcase/preview below reads as "a sheet docked over a
 * screen" rather than the sheet floating alone. Not part of the public API.
 */
@Composable
private fun MockScreenContent(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(MaterialTheme.spacing.normal)) {
        Text(
            text = "Android · Compose",
            style = MaterialTheme.typography.headlineSmallEmphasized,
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.normal))
        repeat(MOCK_SCREEN_CONTENT_ROW_COUNT) { index ->
            Text(
                text = "Flashcard #${index + 1}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = MaterialTheme.spacing.small),
            )
            HorizontalDivider()
        }
    }
}

/**
 * Shared sheet body for both showcase variants below — a settings-summary-shaped mock, equally at
 * home dismissible (with a handle) or permanently docked (without one).
 */
@Composable
private fun MockSheetContent() {
    Text(text = "Mode: Rated", style = MaterialTheme.typography.bodyLarge)
    Text(text = "18 cards", style = MaterialTheme.typography.bodyMedium)
    FlashcardsFilledButton(
        text = "Start session",
        onClick = {},
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MaterialTheme.spacing.normal),
    )
}

/**
 * Placed as an unaligned sibling of [MockScreenContent] in the full-size [Box] — not
 * `Modifier.align(Alignment.BottomCenter)`. `BottomSheet` already derives its own expanded-anchor
 * offset from the incoming container height; aligning the whole child to the bottom on top of that
 * double-offsets it, leaving the expanded sheet mostly below the viewport. See this file's own doc
 * for the full explanation — this is the one placement bug that actually broke every sheet built on
 * this component before it was found.
 */
@OptIn(ExperimentalMaterial3Api::class)
@ShowkaseComposable(name = "Bottom sheet", group = "Sheets")
@Composable
fun FlashcardsBottomSheetShowcase() {
    FlashcardsTheme {
        Surface {
            Box(modifier = Modifier.fillMaxSize()) {
                MockScreenContent(modifier = Modifier.fillMaxSize())
                FlashcardsBottomSheet(
                    state = rememberFlashcardsBottomSheetState(initiallyExpanded = true),
                    onDismissRequest = {},
                ) {
                    MockSheetContent()
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsBottomSheetPreview() {
    FlashcardsBottomSheetShowcase()
}

/**
 * The non-dismissible shape — no handle, no gestures, no back-dismiss — matching
 * `RatedStudySessionScreen`'s permanently-docked card/rating panel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@ShowkaseComposable(name = "Bottom sheet — non-dismissible", group = "Sheets")
@Composable
fun FlashcardsBottomSheetNonDismissibleShowcase() {
    FlashcardsTheme {
        Surface {
            Box(modifier = Modifier.fillMaxSize()) {
                MockScreenContent(modifier = Modifier.fillMaxSize())
                FlashcardsBottomSheet(
                    state = rememberFlashcardsBottomSheetState(dismissible = false),
                    onDismissRequest = {},
                ) {
                    MockSheetContent()
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsBottomSheetNonDismissiblePreview() {
    FlashcardsBottomSheetNonDismissibleShowcase()
}
