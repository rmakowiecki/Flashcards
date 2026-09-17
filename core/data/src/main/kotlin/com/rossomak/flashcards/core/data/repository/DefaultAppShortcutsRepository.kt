package com.rossomak.flashcards.core.data.repository

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toColorInt
import com.caverock.androidsvg.SVG
import com.rossomak.flashcards.core.common.logw
import com.rossomak.flashcards.core.domain.model.ShortcutTarget
import com.rossomak.flashcards.core.domain.repository.AppShortcutsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wires [AppShortcutsRepository] to `ShortcutManagerCompat`. Icon rasterization reuses the same
 * androidsvg pipeline `core:ui`'s on-screen category glyph rendering uses — parse via
 * `SVG.getFromString`, render via `SVG.renderToPicture` (see `FlashcardsVectorIconTile`'s
 * `SvgGlyph`) — rather than a separate image pipeline for shortcuts. This module can't depend on
 * `core:ui` (would invert the Clean Architecture dependency direction: data must not depend on
 * presentation), so the two call sites share the rendering library, not the rendering code.
 */
@Singleton
class DefaultAppShortcutsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AppShortcutsRepository {

    override suspend fun pinShortcut(target: ShortcutTarget): Boolean = withContext(Dispatchers.IO) {
        val supported = ShortcutManagerCompat.isRequestPinShortcutSupported(context)
        if (supported) {
            ShortcutManagerCompat.requestPinShortcut(context, target.toShortcutInfo(), null)
        }
        supported
    }

    override suspend fun syncDynamicShortcuts(favorites: List<ShortcutTarget>) = withContext(Dispatchers.IO) {
        val maxCount = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
        val shortcuts = truncateToDynamicLimit(favorites, maxCount).map { it.toShortcutInfo() }
        val synced = ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        if (!synced) {
            logw { "setDynamicShortcuts failed for ${shortcuts.size} shortcuts; next app-start sync supersedes this" }
        }
    }

    private fun ShortcutTarget.toShortcutInfo(): ShortcutInfoCompat {
        val intent = Intent(AppShortcutsRepository.ACTION_OPEN_ROUTE)
            .setClassName(context.packageName, TARGET_ACTIVITY_CLASS_NAME)
            .putExtra(AppShortcutsRepository.EXTRA_ROUTE, route)
        return ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(name)
            .setLongLabel(name)
            .setIcon(rasterizeIcon(this))
            .setIntent(intent)
            .build()
    }

    /** Null [ShortcutTarget.iconSvg]/[ShortcutTarget.color], or any rasterization failure, falls back to the app icon. */
    private fun rasterizeIcon(target: ShortcutTarget): IconCompat {
        val iconSvg = target.iconSvg
        val color = target.color
        val bitmap = if (iconSvg != null && color != null) {
            runCatching { renderShortcutBitmap(iconSvg, color) }.getOrNull()
        } else {
            null
        }
        return IconCompat.createWithBitmap(bitmap ?: fallbackBitmap())
    }

    private fun renderShortcutBitmap(iconSvg: String, color: String): Bitmap {
        val picture = SVG.getFromString(iconSvg).renderToPicture()
        val tintColor = color.toColorInt()
        val bitmap = createBitmap(ICON_SIZE_PX, ICON_SIZE_PX)
        val canvas = Canvas(bitmap)
        // Self-masked circle on a legacy (non-adaptive) bitmap: createWithAdaptiveBitmap forces the
        // background to be opaque (alpha renders as solid black, not wallpaper), which broke this
        // approach. Legacy Icon/Bitmap rendering has no such constraint — plain bitmaps have always
        // supported real per-pixel alpha — so draw our own circle here, sized to the adaptive-icon
        // "safe zone" ratio, and leave the corners genuinely transparent.
        val center = ICON_SIZE_PX / 2f
        val bgRadius = ICON_SIZE_PX * BG_CIRCLE_RATIO / 2f
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        bgPaint.color = tonalBackgroundColor(tintColor)
        canvas.drawCircle(center, center, bgRadius, bgPaint)
        val inset = ICON_SIZE_PX * (1f - ICON_SAFE_ZONE_RATIO) / 2f
        val contentArea = RectF(inset, inset, ICON_SIZE_PX - inset, ICON_SIZE_PX - inset)
        val tintPaint = Paint().apply { colorFilter = PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN) }
        canvas.saveLayer(RectF(0f, 0f, ICON_SIZE_PX.toFloat(), ICON_SIZE_PX.toFloat()), tintPaint)
        canvas.drawPicture(picture, contentArea)
        canvas.restore()
        return bitmap
    }

    /** Keeps the glyph's hue/saturation; shifts lightness away from the glyph's own so a light glyph gets a darker tonal bg and vice versa — a fixed direction (always lighten/darken) fails at either end of the palette. */
    private fun tonalBackgroundColor(glyphColor: Int): Int {
        val hsl = FloatArray(HSL_COMPONENT_COUNT)
        ColorUtils.colorToHSL(glyphColor, hsl)
        val glyphLightness = hsl[2]
        val shifted =
            if (glyphLightness > MID_LIGHTNESS) {
                glyphLightness - BG_LIGHTNESS_DELTA
            } else {
                glyphLightness + BG_LIGHTNESS_DELTA
            }
        hsl[2] = shifted.coerceIn(BG_LIGHTNESS_MIN, BG_LIGHTNESS_MAX)
        return ColorUtils.HSLToColor(hsl)
    }

    private fun fallbackBitmap(): Bitmap =
        context.packageManager.getApplicationIcon(context.applicationInfo).toBitmap(ICON_SIZE_PX, ICON_SIZE_PX)

    companion object {
        private const val TARGET_ACTIVITY_CLASS_NAME = "com.rossomak.flashcards.MainActivity"

        /** Fixed launcher-icon bitmap size — independent of any on-screen UI tile's dp size. */
        private const val ICON_SIZE_PX = 512

        /** Self-masked background circle's diameter, as a ratio of the full canvas. */
        private const val BG_CIRCLE_RATIO = 0.85f

        /** Glyph content-area ratio, relative to the full canvas — sized to sit well inside [BG_CIRCLE_RATIO]. */
        private const val ICON_SAFE_ZONE_RATIO = 0.4f

        /** Size of the HSL float array (hue, saturation, lightness) used by [ColorUtils]. */
        private const val HSL_COMPONENT_COUNT = 3

        /** Lightness threshold separating "light glyph" from "dark glyph". */
        private const val MID_LIGHTNESS = 0.5f

        /** How far the tonal background's HSL lightness is pushed away from the glyph's own. */
        private const val BG_LIGHTNESS_DELTA = 0.35f
        private const val BG_LIGHTNESS_MIN = 0.1f
        private const val BG_LIGHTNESS_MAX = 0.9f
    }
}

/**
 * `favorites.take(maxCount)` pulled into its own function so [DefaultAppShortcutsRepositoryTest]
 * can pin the order-preserving, no-resort contract with a plain-JVM test — the rest of this file
 * needs a real Android runtime (`Intent`, `ShortcutManagerCompat`, `android.graphics.Canvas`)
 * that this project's plain-JVM unit tests don't provide (see TESTING.md).
 */
internal fun truncateToDynamicLimit(favorites: List<ShortcutTarget>, maxCount: Int): List<ShortcutTarget> =
    favorites.take(maxCount)
