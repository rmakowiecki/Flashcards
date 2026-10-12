package com.rossomak.networkgraph.tuner

import androidx.compose.ui.platform.ClipEntry

/** A clipboard entry holding [text] as plain text; each platform wraps its own clipboard type. */
internal expect fun plainTextClipEntry(text: String): ClipEntry
