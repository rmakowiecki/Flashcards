package com.rossomak.flashcards.core.ui.composables.dialogs

import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.rossomak.flashcards.core.designsystem.theme.AppSpacing
import com.rossomak.flashcards.core.designsystem.theme.cornerRadius
import com.rossomak.flashcards.core.designsystem.theme.spacing
import java.util.function.Consumer

/**
 * The dialog scaffold every `Flashcards*Dialog` is built on — a thin skin over M3's [AlertDialog].
 *
 * Deliberately `internal`. Feature call sites cannot reach it, so they cannot assemble a dialog
 * that bypasses an L2 behavioral contract. A genuinely new shape (a three-action fork, say) is
 * added as a third L2 type rather than by hand-rolling this scaffold at a call site. L1 owns the
 * skin only — dismissal semantics, action rows and commit rules all belong to the L2 types
 * ([FlashcardsSingleActionDialog], [FlashcardsDecisionDialog]).
 *
 * Building on [AlertDialog] rather than a raw `Dialog` + `Surface` means M3 already provides the
 * behavior this scaffold would otherwise reimplement: min/max dialog width, the pinned action row,
 * dialog semantics for accessibility, and — the reason [icon] carries no companion alignment
 * parameter — a header that **centers itself when an icon is present** and start-aligns when it
 * isn't. Every dialog in the design language follows that pairing, so there is nothing to
 * configure.
 *
 * M3 does **not** scroll the `text` slot on its own, so this scaffold wraps [content] in
 * [Modifier.verticalScroll] itself — the one place content that grows past the viewport (a long
 * [supportingText], a dialog with many rows, extended context) is kept from pushing the pinned
 * action row off-screen.
 *
 * The one place M3's default differs from the design language is [supportingText], which M3 always
 * start-aligns; it is aligned here to match the title instead.
 *
 * > **Never put a `LazyColumn` inside [content].** It lands inside M3's scrolling `text` region,
 * > is measured with an infinite maximum height, and will throw. Content long enough to need
 * > laziness belongs in a bottom sheet or a full screen, not a dialog.
 */
@Composable
internal fun FlashcardsDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    supportingText: String? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val supportingTextAlign = if (icon == null) TextAlign.Start else TextAlign.Center

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon?.let { { Icon(imageVector = it, contentDescription = null) } },
        title = { Text(text = title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            DialogBlurBehindEffect()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = supportingTextAlign,
                    )
                }
                content()
            }
        },
        shape = RoundedCornerShape(MaterialTheme.cornerRadius.large),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        iconContentColor = MaterialTheme.colorScheme.primary,
        // M3 defaults the text slot to onSurfaceVariant, which is right for supporting prose but
        // washes out the selectable rows that make up most dialog content. Those rows opt into
        // the muted color themselves where they want it.
        textContentColor = MaterialTheme.colorScheme.onSurface,
        properties = properties,
    )
}

/** Blur-behind radius applied to a dialog's window when cross-window blur is available. */
private val DIALOG_BLUR_RADIUS = 16.dp

/**
 * Scrim dim used instead of blur when cross-window blur isn't available at runtime. This is not
 * a rare edge case: [WindowManager.isCrossWindowBlurEnabled] is false on any device whose
 * compositor never declared blur support (the OEM-set system property
 * `ro.surface_flinger.supports_background_blur`), independent of API level or battery saver —
 * confirmed on a Realme (ColorOS) device at API 34, which reports the property unset. On such
 * devices this is the permanent look, not a transient one, so it's boosted from the platform's
 * default dim rather than left matching the blurred case.
 */
private const val BLUR_UNAVAILABLE_DIM_AMOUNT = 0.75f

/**
 * Blurs whatever is behind the dialog's own window, on top of the unchanged scrim dim — never
 * instead of it. Two gates guard this, both live for the dialog's lifetime:
 *
 * - **API 31+** ([Build.VERSION.SDK_INT]): cross-window blur is a platform capability introduced
 *   in Android 12; below that this is a no-op.
 * - **Runtime availability** ([WindowManager.isCrossWindowBlurEnabled]): even on 31+, the system
 *   can disable cross-window blur (battery saver, low-RAM devices, a developer option, or simply
 *   no compositor support declared by the OEM — see [BLUR_UNAVAILABLE_DIM_AMOUNT]), and it can
 *   flip mid-session — [WindowManager.addCrossWindowBlurEnabledListener] is how a dialog already
 *   on screen reacts to that.
 *
 * When blur can't render, the scrim dim is bumped to [BLUR_UNAVAILABLE_DIM_AMOUNT] so the dialog
 * still reads as deliberate rather than a silently missing effect; the platform's own default dim
 * (read once, before the first mutation) is restored whenever blur *is* available.
 *
 * This is not [androidx.compose.ui.draw.blur] — a Compose dialog is its own window, so blurring
 * what's *behind* it means reaching that window (via [DialogWindowProvider], the same seam M3's own
 * [AlertDialog] is built on) and setting blur-behind flags on its layout params, not blurring the
 * dialog's own content.
 */
@Composable
private fun DialogBlurBehindEffect() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    val view = LocalView.current
    val context = LocalContext.current
    val blurRadiusPx = with(LocalDensity.current) { DIALOG_BLUR_RADIUS.roundToPx() }

    DisposableEffect(view, blurRadiusPx) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val windowManager = context.getSystemService(WindowManager::class.java)
        if (window == null || windowManager == null) return@DisposableEffect onDispose {}

        val defaultDimAmount = window.attributes.dimAmount
        val listener = Consumer<Boolean> { enabled ->
            window.applyBlurBehind(enabled, blurRadiusPx, defaultDimAmount)
        }
        with(windowManager) {
            window.applyBlurBehind(isCrossWindowBlurEnabled, blurRadiusPx, defaultDimAmount)
            addCrossWindowBlurEnabledListener(context.mainExecutor, listener)
        }

        onDispose {
            windowManager.removeCrossWindowBlurEnabledListener(listener)
            window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.setDimAmount(defaultDimAmount)
        }
    }
}

/**
 * `Window.setBackgroundBlurRadius` blurs *within* this window's own bounds — the wrong API here,
 * since [FLAG_BLUR_BEHIND][WindowManager.LayoutParams.FLAG_BLUR_BEHIND] asks the compositor to blur
 * whatever's *behind* the window instead. That radius is a [WindowManager.LayoutParams] field
 * ([WindowManager.LayoutParams.blurBehindRadius]), set through [attributes] and re-applied via
 * [setAttributes] — there is no direct `Window` setter for it.
 */
@RequiresApi(Build.VERSION_CODES.S)
private fun Window.applyBlurBehind(enabled: Boolean, blurRadiusPx: Int, defaultDimAmount: Float) {
    if (enabled) {
        addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        setAttributes(attributes.apply { blurBehindRadius = blurRadiusPx })
        setDimAmount(defaultDimAmount)
    } else {
        clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        setDimAmount(BLUR_UNAVAILABLE_DIM_AMOUNT)
    }
}

/** Geometry of the dialog scaffold that its content composables have to line up against. */
internal object FlashcardsDialogDefaults {

    /**
     * How far content may extend past M3's text inset on each side, via [dialogContentBleed].
     *
     * Also the padding a bleeding row puts back inside itself, which is what keeps its children
     * aligned with the title and supporting text.
     */
    val contentBleed: Dp = AppSpacing.xsmall
}

/**
 * Lets content in the dialog's [content] slot draw and measure [FlashcardsDialogDefaults.contentBleed]
 * wider than the slot allows on each side, while still reporting the slot-sized width upwards.
 *
 * M3 hardcodes the `text` slot's horizontal inset (`AlertDialogDefaults`' private `TextPadding`,
 * 24dp) with no parameter to widen it, so a row that wants its press indication to extend past
 * the prose margin — the way a list row does against a screen's margin — has no way to ask for the
 * width. It has to take it. The row hands the width straight back as inner padding, so only the
 * band grows; the row's own children stay on the prose margin.
 *
 * Applied by the row *container* ([FlashcardsMultiSelectGroup]) rather than by individual rows, so
 * this escape hatch exists in one place per stack rather than once per row.
 */
internal fun Modifier.dialogContentBleed(): Modifier = layout { measurable, constraints ->
    val bleedPx = FlashcardsDialogDefaults.contentBleed.roundToPx()
    val placeable = measurable.measure(constraints.offset(horizontal = bleedPx * 2))
    layout(placeable.width - bleedPx * 2, placeable.height) {
        placeable.place(x = -bleedPx, y = 0)
    }
}
