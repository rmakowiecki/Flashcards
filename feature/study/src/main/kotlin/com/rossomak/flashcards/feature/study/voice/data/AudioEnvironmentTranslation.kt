package com.rossomak.flashcards.feature.study.voice.data

import android.annotation.SuppressLint
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.rossomak.flashcards.core.domain.model.AudioMode
import com.rossomak.flashcards.core.domain.model.FocusChange
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

// The platform's audio integers, translated to what the study session coordinators reason about.
// Pure functions, so they run on the JVM without an AudioManager.

/** The domain focus change for an audio-focus callback value, or `null` for a value the session never acts on. */
internal fun focusChangeOf(platformFocusChange: Int): FocusChange? = when (platformFocusChange) {
    AudioManager.AUDIOFOCUS_GAIN -> FocusChange.Gain
    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> FocusChange.LossCanDuck
    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> FocusChange.LossTransient
    AudioManager.AUDIOFOCUS_LOSS -> FocusChange.Loss
    else -> null
}

/**
 * The domain audio mode for the live platform mode. The session sets the communication mode itself
 * while it listens on a Bluetooth headset ([isOwnCommunicationLink]), and that must never read as
 * another app's call.
 */
@SuppressLint("InlinedApi") // the call-screening and redirect modes read as plain integers on older releases, which never report them.
internal fun audioModeOf(platformMode: Int, isOwnCommunicationLink: Boolean): AudioMode = when (platformMode) {
    AudioManager.MODE_RINGTONE -> AudioMode.Ringtone
    AudioManager.MODE_IN_CALL, AudioManager.MODE_CALL_SCREENING, AudioManager.MODE_CALL_REDIRECT -> AudioMode.InCall
    AudioManager.MODE_IN_COMMUNICATION ->
        if (isOwnCommunicationLink) AudioMode.Normal else AudioMode.InCommunication
    AudioManager.MODE_COMMUNICATION_REDIRECT -> AudioMode.InCommunication
    else -> AudioMode.Normal
}

/** A headset, wired or Bluetooth: an output the user hears the session on while the phone is away from their ear. */
@SuppressLint("InlinedApi") // the LE Audio headset type reads as a plain integer on older releases, which never report it.
internal fun isHeadsetClassOutput(deviceType: Int): Boolean = when (deviceType) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_HEARING_AID -> true
    else -> false
}

/** The call-type Bluetooth links, which the session sets up and tears down itself for each listening window. */
@SuppressLint("InlinedApi") // see isHeadsetClassOutput.
internal fun isCallLinkOutput(deviceType: Int): Boolean =
    deviceType == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || deviceType == AudioDeviceInfo.TYPE_BLE_HEADSET

/**
 * Turns the two sources of a headset disconnect (the becoming-noisy broadcast and the removal of an
 * output device) into one report: both usually fire for one disconnect, and a second one within
 * [window] of the first is dropped.
 */
internal class OutputDisconnectDeduper(
    private val elapsedNow: () -> Duration,
    private val window: Duration = DEDUPE_WINDOW,
) {
    private var lastReportedAt: Duration? = null

    fun shouldReport(): Boolean {
        val now = elapsedNow()
        val last = lastReportedAt
        if (last != null && now - last < window) return false
        lastReportedAt = now
        return true
    }

    private companion object {
        val DEDUPE_WINDOW = 1500.milliseconds
    }
}
