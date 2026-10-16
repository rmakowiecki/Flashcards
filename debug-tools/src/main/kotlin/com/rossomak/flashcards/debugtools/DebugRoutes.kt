package com.rossomak.flashcards.debugtools

import kotlinx.serialization.Serializable

@Serializable object DebugGraph

/** The hub itself — the tab's start destination, listing every debug tool. */
@Serializable object DebugRoot

/** The voice capture/grading harness, reached from the hub rather than from the tab directly. */
@Serializable object DebugVoiceRoot

/** The voice capture indicator preview, animated from a synthetic level stream — no audio involved. */
@Serializable object DebugVoiceIndicatorRoot

/** Backdrop blur harness with a live radius slider. */
@Serializable object DebugBackdropBlurRoot
