package com.rossomak.flashcards.core.voice

/**
 * On-device voice obfuscation. Raw voice audio must never leave the capture stage
 * un-obfuscated — [VoiceCaptureEngine] runs every captured utterance through this before
 * emitting it, and only the obfuscated PCM is ever handed to upload code.
 */
interface VoiceObfuscator {

    /**
     * Re-draws the randomized shift parameters. [VoiceCaptureEngine] calls it each time the
     * microphone opens for listening, so successive answers cannot be correlated by a stable
     * transform. The shift holds for every utterance captured in that listening window.
     */
    fun randomizeSessionShift()

    /** Returns an obfuscated copy of [pcm]; the input array is not modified. */
    fun obfuscate(pcm: ShortArray): ShortArray
}
