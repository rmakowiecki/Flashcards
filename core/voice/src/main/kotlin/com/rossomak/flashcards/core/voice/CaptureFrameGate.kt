package com.rossomak.flashcards.core.voice

/**
 * Decides, frame by frame, whether the capture gate drops the frame just read. Reading goes on
 * while the gate is closed, so the microphone buffer never backs up. The pre-roll holds the audio
 * that came right before the frame being read, and a gap of dropped frames breaks that, so it is
 * cleared on the first frame after the gate opens.
 */
internal class CaptureFrameGate {

    private var wasClosed = false

    /** Returns true when the frame must be dropped. [preRoll] is emptied on the first open frame after a closed gate. */
    fun shouldDrop(isClosed: Boolean, preRoll: ArrayDeque<ShortArray>): Boolean {
        if (wasClosed && !isClosed) preRoll.clear()
        wasClosed = isClosed
        return isClosed
    }

    fun reset() {
        wasClosed = false
    }
}
