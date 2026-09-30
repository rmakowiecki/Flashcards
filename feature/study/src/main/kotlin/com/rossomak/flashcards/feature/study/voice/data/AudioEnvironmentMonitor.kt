package com.rossomak.flashcards.feature.study.voice.data

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.rossomak.flashcards.core.common.logd
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.AudioModeChanged
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.FocusChanged
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.MicSilenced
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.MicUnsilenced
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.OutputDisconnected
import com.rossomak.flashcards.core.domain.model.AudioMode
import com.rossomak.flashcards.core.domain.model.FocusChange
import com.rossomak.flashcards.core.voice.AudioRouteManager
import com.rossomak.flashcards.core.voice.VoiceCaptureEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * The platform half of audio interruptions, hosted by [StudySessionVoiceService]. It requests and
 * holds the session's audio focus, and translates what the platform reports into
 * [AudioEnvironmentSignal]s: focus changes, the live audio mode, a microphone taken by another app
 * and a headset disconnect. It decides nothing and never pauses or resumes anything; the study
 * session coordinators do.
 *
 * - **Focus** is requested on play, held while paused (abandoning it would let a defensively paused
 *   app grab the slot) and requested again after a permanent loss, which drops the request from the
 *   system's stack. It asks to be paused when ducked, so the platform always reports the duck
 *   callback and the coordinators choose what a blip does.
 * - **The live mode** is read on every signal and every mode-change callback, never taken from a
 *   callback's payload: a callback for the session's own mode change can arrive after the session
 *   released its Bluetooth link. The communication mode the session sets on that link is reported
 *   as [AudioMode.Normal].
 * - **Mode changes without a focus change** come from the mode listener (API 31+). Older releases
 *   report none, so the mode is only read when a focus change or a mode callback arrives: a call
 *   picked up while focus is already lost stays unseen until focus returns.
 * - **A silenced microphone** (API 29+) is watched through the session's own recording. When the
 *   recording ends while silenced, the state is kept until the next recording starts or until no
 *   app records any more, because a recording that no longer exists cannot report that it is
 *   audible again.
 * - **A headset disconnect** is reported once for the becoming-noisy broadcast and the removal of a
 *   headset-class output together. Removing the call-type link right after the session released it
 *   itself is not a disconnect.
 */
@Suppress("TooManyFunctions") // one registration and one translation per platform source.
class AudioEnvironmentMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val audioRouteManager: AudioRouteManager,
    private val voiceCaptureEngine: VoiceCaptureEngine,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val signalChannel = Channel<AudioEnvironmentSignal>(Channel.UNLIMITED)

    /** Unconflated, single collector. The live mode always comes before the focus change that arrives with it. */
    val signals: Flow<AudioEnvironmentSignal> = signalChannel.receiveAsFlow()

    private var focusRequest: AudioFocusRequest? = null
    private var lastReportedMode = AudioMode.Normal
    private var isMicSilenced = false
    private var isStarted = false

    private val outputDisconnectDeduper = OutputDisconnectDeduper(elapsedNow = { SystemClock.elapsedRealtime().milliseconds })

    private val focusListener = AudioManager.OnAudioFocusChangeListener { platformFocusChange ->
        val change = focusChangeOf(platformFocusChange) ?: return@OnAudioFocusChangeListener
        // The system dropped the request from its stack; the next play asks again.
        if (change == FocusChange.Loss) focusRequest = null
        reportLiveMode()
        logd { "Audio focus $change" }
        signalChannel.trySend(FocusChanged(change))
    }

    private var modeListener: AudioManager.OnModeChangedListener? = null

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) = reportOutputDisconnected()
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            val isHeadsetRemoved = removedDevices.orEmpty().any { device ->
                device.isSink && isHeadsetClassOutput(device.type) && !(isCallLinkOutput(device.type) && isNearOwnLinkRelease())
            }
            if (isHeadsetRemoved) reportOutputDisconnected()
        }
    }

    private val recordingCallback: AudioManager.AudioRecordingCallback? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : AudioManager.AudioRecordingCallback() {
                override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) = onRecordingConfigs(configs)
            }
        } else {
            null
        }

    /** Starts watching the platform for the service's lifetime. Idempotent. */
    fun start() {
        if (isStarted) return
        isStarted = true
        context.registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        audioManager.registerAudioDeviceCallback(deviceCallback, handler)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) registerModeListener()
        registerRecordingCallback()
        scope.launch { voiceCaptureEngine.audioSessionId.collect(::onAudioSessionId) }
    }

    /** Stops watching and gives up focus. Safe to call more than once. */
    fun release() {
        if (isStarted) {
            isStarted = false
            runCatching { context.unregisterReceiver(noisyReceiver) }
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) unregisterModeListener()
            unregisterRecordingCallback()
        }
        abandonFocus()
        scope.cancel()
    }

    /** Requests focus once and keeps it; a no-op while a request is held, which guards against per-utterance churn. */
    fun requestFocus() {
        if (focusRequest != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setWillPauseWhenDucked(true)
            .setAcceptsDelayedFocusGain(true) // queue instead of fail when another app holds focus
            .setOnAudioFocusChangeListener(focusListener, handler)
            .build()
        focusRequest = request
        audioManager.requestAudioFocus(request)
    }

    fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    /** Reads the live mode and reports it when it changed since the last report. */
    private fun reportLiveMode() {
        val mode = audioModeOf(audioManager.mode, audioRouteManager.isBluetoothLinkActive)
        if (mode == lastReportedMode) return
        lastReportedMode = mode
        logd { "Audio mode $mode" }
        signalChannel.trySend(AudioModeChanged(mode))
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerModeListener() {
        val listener = AudioManager.OnModeChangedListener { reportLiveMode() }
        modeListener = listener
        audioManager.addOnModeChangedListener(ContextCompat.getMainExecutor(context), listener)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun unregisterModeListener() {
        modeListener?.let { audioManager.removeOnModeChangedListener(it) }
        modeListener = null
    }

    private fun reportOutputDisconnected() {
        if (!outputDisconnectDeduper.shouldReport()) return
        logd { "Output disconnected" }
        signalChannel.trySend(OutputDisconnected)
    }

    private fun isNearOwnLinkRelease(): Boolean =
        SystemClock.elapsedRealtime() - audioRouteManager.lastBluetoothLinkReleaseElapsedMillis < OWN_LINK_RELEASE_GRACE.inWholeMilliseconds

    @SuppressLint("NewApi") // callers check the SDK level; the callback is null below API 29.
    private fun registerRecordingCallback() {
        recordingCallback?.let { audioManager.registerAudioRecordingCallback(it, handler) }
    }

    @SuppressLint("NewApi")
    private fun unregisterRecordingCallback() {
        recordingCallback?.let { audioManager.unregisterAudioRecordingCallback(it) }
    }

    @SuppressLint("NewApi")
    private fun onRecordingConfigs(configs: List<AudioRecordingConfiguration>) {
        val sessionId = voiceCaptureEngine.audioSessionId.value
        if (sessionId == null) {
            // The session's recording ended while silenced and cannot report being audible again; the microphone is
            // free once no other app records either.
            if (isMicSilenced && configs.isEmpty()) setMicSilenced(false)
            return
        }
        val ownConfig = configs.firstOrNull { config -> config.clientAudioSessionId == sessionId } ?: return
        setMicSilenced(ownConfig.isClientSilenced)
    }

    // A new recording starts audible: whatever silenced the last one is over, or it silences this one too.
    private fun onAudioSessionId(sessionId: Int?) {
        if (sessionId != null) setMicSilenced(false)
    }

    private fun setMicSilenced(silenced: Boolean) {
        if (silenced == isMicSilenced) return
        isMicSilenced = silenced
        logd { "Microphone silenced=$silenced" }
        signalChannel.trySend(if (silenced) MicSilenced else MicUnsilenced)
    }

    private companion object {
        // The device list can drop the call-type link just after the session released it; that is not the user's headset going away.
        val OWN_LINK_RELEASE_GRACE = 3.seconds
    }
}
