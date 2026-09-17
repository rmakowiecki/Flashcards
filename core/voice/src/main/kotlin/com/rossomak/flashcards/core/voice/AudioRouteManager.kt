package com.rossomak.flashcards.core.voice

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

enum class CaptureRouteType { Phone, BluetoothLe, BluetoothSco, Waiting, None }

/**
 * The capture route selected for a listening session.
 *
 * [device] is the *input* [AudioDeviceInfo] to bind onto [android.media.AudioRecord] via
 * `setPreferredDevice`. It is non-null only for the Bluetooth route types; PHONE uses the default
 * mic (null device) and WAITING/NONE carry no capturable device.
 */
data class CaptureRoute(
    val type: CaptureRouteType,
    val device: AudioDeviceInfo? = null,
) {
    val isBluetooth: Boolean
        get() = type == CaptureRouteType.BluetoothLe || type == CaptureRouteType.BluetoothSco

    val isCapturable: Boolean
        get() = type == CaptureRouteType.Phone || isBluetooth
}

/**
 * Owns microphone *routing* for voice answering — a separable concern from the capture loop in
 * [VoiceCaptureEngine]. Implements the BLE-first, SCO-fallback, Bluetooth-strict policy (ADR-0027):
 *
 * - **BLE-first:** when an LE Audio headset (`TYPE_BLE_HEADSET`) is a communication device it wins —
 *   mic + hi-fi output on one session-long link, no per-turn toggling. Classic SCO
 *   (`TYPE_BLUETOOTH_SCO`) is the fallback only when LE Audio is unavailable.
 * - **Bluetooth-strict:** when a mic-capable BT device is connected but the link is not yet ready,
 *   the route is [CaptureRouteType.Waiting] and callers must NOT capture on the phone mic. The phone
 *   mic is only ever [CaptureRouteType.Phone] when no mic-capable BT device is present (A2DP-only
 *   output does not count).
 * - **Session-level:** [acquireSessionRoute] runs the handshake once per session; the route is held
 *   until [releaseSessionRoute], never toggled per card. Mid-session connect/disconnect is observed
 *   via [AudioDeviceCallback] (API 23+) and the pre-31 SCO-state receiver, re-resolved, and signalled
 *   through [routeChanges] so the engine can switch at an utterance boundary.
 *
 * `setCommunicationDevice`/`clearCommunicationDevice`/`communicationDevice` are API 31+; below that
 * (minSdk 26) only Classic SCO via `startBluetoothSco`/`stopBluetoothSco` is reachable — LE Audio is
 * not.
 *
 * Both paths also drive [AudioManager.setMode] to `MODE_IN_COMMUNICATION` while a BT route is
 * active, restoring `MODE_NORMAL` on release. Without it, OEM audio HALs commonly apply the default
 * (non-communication) audio policy's onset gating/AGC to the BT link — swallowing the first syllable
 * of every utterance, not just at session start.
 */
@Singleton
class AudioRouteManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val _route = MutableStateFlow(CaptureRoute(CaptureRouteType.None))
    val route: StateFlow<CaptureRoute> = _route.asStateFlow()

    /** Emitted when the selected route changes mid-session; the engine switches at an utterance boundary. */
    private val _routeChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val routeChanges: SharedFlow<Unit> = _routeChanges.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val resolveMutex = Mutex()

    private val audioManager: AudioManager
        get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var deviceCallback: AudioDeviceCallback? = null
    private var scoStateReceiver: BroadcastReceiver? = null
    private var communicationDeviceListener: AudioManager.OnCommunicationDeviceChangedListener? = null
    private var isSessionActive = false

    /**
     * Resolve and activate the capture route for a listening session. Suspends through the BT
     * handshake (timeout + retry). Idempotent while a session is active. Registers the dynamic
     * device callbacks on first call.
     */
    suspend fun acquireSessionRoute(): CaptureRoute {
        if (!isSessionActive) {
            isSessionActive = true
            registerDeviceCallback()
            registerScoStateReceiver()
            registerCommunicationDeviceListener()
        }
        return resolveAndActivate()
    }

    /** Suspends until the route settles to a capturable one (past any [CaptureRouteType.Waiting]). */
    suspend fun awaitRouteReady(): CaptureRoute = route.first { it.isCapturable }

    /** Tear down routing at session end: clear the communication device / stop SCO, unregister callbacks. */
    fun releaseSessionRoute() {
        isSessionActive = false
        unregisterDeviceCallback()
        unregisterScoStateReceiver()
        unregisterCommunicationDeviceListener()
        deactivateRoute()
        _route.value = CaptureRoute(CaptureRouteType.None)
    }

    private suspend fun resolveAndActivate(): CaptureRoute = resolveMutex.withLock {
        val resolved =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                resolveCommunicationRoute()
            } else {
                resolveLegacyScoRoute()
            }
        Log.i(TAG, "route resolved -> ${resolved.type} (device=${resolved.device?.type})")
        _route.value = resolved
        resolved
    }

    /** API 31+: BLE-first via `setCommunicationDevice`, SCO fallback, else phone. */
    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun resolveCommunicationRoute(): CaptureRoute {
        val communicationDevices = audioManager.availableCommunicationDevices
        val bleDevice = communicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
        val scoDevice = communicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        val target = bleDevice ?: scoDevice ?: run {
            // No mic-capable BT communication device (A2DP-only or nothing) -> phone mic allowed.
            audioManager.clearCommunicationDevice()
            audioManager.mode = AudioManager.MODE_NORMAL
            return CaptureRoute(CaptureRouteType.Phone)
        }
        // setCommunicationDevice() is documented to require MODE_IN_COMMUNICATION/MODE_IN_CALL to
        // behave correctly; without it OEM audio HALs commonly apply inconsistent onset gating/AGC
        // on the BT link — swallowed first syllable on every utterance, not just at session start.
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        val ready = requestCommunicationDevice(target)
        if (!ready) return CaptureRoute(CaptureRouteType.Waiting)
        val routeType =
            if (target.type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                CaptureRouteType.BluetoothLe
            } else {
                CaptureRouteType.BluetoothSco
            }
        return CaptureRoute(routeType, matchingInputDevice(target.type))
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun requestCommunicationDevice(target: AudioDeviceInfo): Boolean {
        repeat(HANDSHAKE_RETRIES + 1) {
            if (audioManager.setCommunicationDevice(target)) {
                // LE Audio routing is effectively immediate; SCO still needs the link to come up.
                val settled = withTimeoutOrNull(HANDSHAKE_TIMEOUT_MS) {
                    while (audioManager.communicationDevice?.id != target.id) delay(HANDSHAKE_POLL_MS)
                    true
                }
                if (settled == true) return true
            }
        }
        Log.w(TAG, "communication-device handshake failed for type=${target.type}")
        return false
    }

    /** Pre-31: Classic SCO only. Start SCO and await CONNECTED before treating BT as capturable. */
    @SuppressLint("DEPRECATION")
    private suspend fun resolveLegacyScoRoute(): CaptureRoute {
        if (!isBluetoothAudioConnected() || !audioManager.isBluetoothScoAvailableOffCall) {
            runCatching { audioManager.stopBluetoothSco() }
            audioManager.mode = AudioManager.MODE_NORMAL
            return CaptureRoute(CaptureRouteType.Phone)
        }
        // See resolveCommunicationRoute(): SCO needs MODE_IN_COMMUNICATION for the onset of capture
        // not to be gated/AGC-chewed by the platform's default (non-communication) audio policy.
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        val connected = awaitScoConnected()
        if (!connected) {
            runCatching { audioManager.stopBluetoothSco() }
            return CaptureRoute(CaptureRouteType.Waiting)
        }
        return CaptureRoute(CaptureRouteType.BluetoothSco, matchingInputDevice(AudioDeviceInfo.TYPE_BLUETOOTH_SCO))
    }

    @SuppressLint("DEPRECATION")
    private suspend fun awaitScoConnected(): Boolean {
        val connected = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                when (intent?.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)) {
                    AudioManager.SCO_AUDIO_STATE_CONNECTED -> connected.complete(true)
                    AudioManager.SCO_AUDIO_STATE_ERROR,
                    AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> connected.complete(false)
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED))
        return try {
            audioManager.startBluetoothSco()
            withTimeoutOrNull(HANDSHAKE_TIMEOUT_MS) { connected.await() } ?: false
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    // SCO only, per the class KDoc: A2DP is output-only and never mic-capable, so an A2DP-only
    // headphone must fall through to the phone mic rather than get stuck awaiting a SCO link that
    // isBluetoothScoAvailableOffCall() (a system-wide capability flag, not per-device) would
    // otherwise make this method wait on indefinitely.
    private fun isBluetoothAudioConnected(): Boolean =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }

    private fun matchingInputDevice(type: Int): AudioDeviceInfo? =
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.type == type }

    @SuppressLint("DEPRECATION") // stopBluetoothSco is the only teardown path below API 31.
    private fun deactivateRoute() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            runCatching { audioManager.stopBluetoothSco() }
        }
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    private fun registerDeviceCallback() {
        if (deviceCallback != null) return
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = onDevicesChanged()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = onDevicesChanged()
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        deviceCallback = callback
    }

    private fun unregisterDeviceCallback() {
        deviceCallback?.let { audioManager.unregisterAudioDeviceCallback(it) }
        deviceCallback = null
    }

    private fun registerScoStateReceiver() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S || scoStateReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                val state = intent?.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1) ?: return
                if (state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED || state == AudioManager.SCO_AUDIO_STATE_ERROR) {
                    onDevicesChanged()
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED))
        scoStateReceiver = receiver
    }

    private fun unregisterScoStateReceiver() {
        scoStateReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        scoStateReceiver = null
    }

    /**
     * API 31+ recovery path for a stuck [CaptureRouteType.Waiting]: [requestCommunicationDevice]'s
     * handshake can time out before `setCommunicationDevice()` actually settles, and
     * [AudioDeviceCallback] alone won't re-trigger since no device was added/removed — only the
     * active communication device changed. This listener re-resolves whenever that settles late.
     */
    private fun registerCommunicationDeviceListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || communicationDeviceListener != null) return
        val listener = AudioManager.OnCommunicationDeviceChangedListener { onDevicesChanged() }
        audioManager.addOnCommunicationDeviceChangedListener(ContextCompat.getMainExecutor(context), listener)
        communicationDeviceListener = listener
    }

    private fun unregisterCommunicationDeviceListener() {
        val listener = communicationDeviceListener ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.removeOnCommunicationDeviceChangedListener(listener)
        }
        communicationDeviceListener = null
    }

    /** A device connect/disconnect happened — re-resolve the route and signal a switch if it changed. */
    private fun onDevicesChanged() {
        if (!isSessionActive) return
        scope.launch {
            val previous = _route.value
            val resolved = resolveAndActivate()
            // Compare device id too, not just type: switching between two SCO headsets (or two BLE
            // sets) keeps the same CaptureRouteType but is still a real device change the engine
            // must rebuild its AudioRecord for.
            if (resolved.type != previous.type || resolved.device?.id != previous.device?.id) {
                Log.i(TAG, "route changed $previous -> $resolved")
                _routeChanges.emit(Unit)
            }
        }
    }

    private companion object {
        const val TAG = "AudioRouteManager"
        const val HANDSHAKE_TIMEOUT_MS = 3_000L
        const val HANDSHAKE_POLL_MS = 50L
        const val HANDSHAKE_RETRIES = 1
    }
}
