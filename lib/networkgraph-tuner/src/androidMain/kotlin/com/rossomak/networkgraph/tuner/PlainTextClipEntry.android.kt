package com.rossomak.networkgraph.tuner

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry

internal actual fun plainTextClipEntry(text: String): ClipEntry = ClipEntry(ClipData.newPlainText(CLIP_LABEL, text))

private const val CLIP_LABEL = "NetworkGraphSpec"
