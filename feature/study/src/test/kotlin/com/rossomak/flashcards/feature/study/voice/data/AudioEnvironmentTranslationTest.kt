package com.rossomak.flashcards.feature.study.voice.data

import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.rossomak.flashcards.core.domain.model.AudioMode
import com.rossomak.flashcards.core.domain.model.FocusChange
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.Test

class AudioEnvironmentTranslationTest {

    @Test
    fun `each audio focus callback maps to its focus change`() {
        focusChangeOf(AudioManager.AUDIOFOCUS_GAIN) shouldBe FocusChange.Gain
        focusChangeOf(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) shouldBe FocusChange.LossCanDuck
        focusChangeOf(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) shouldBe FocusChange.LossTransient
        focusChangeOf(AudioManager.AUDIOFOCUS_LOSS) shouldBe FocusChange.Loss
    }

    @Test
    fun `a focus value the session never acts on maps to nothing`() {
        focusChangeOf(AudioManager.AUDIOFOCUS_NONE) shouldBe null
    }

    @Test
    fun `the phone call modes map to a call and a ring to a ring`() {
        audioModeOf(AudioManager.MODE_RINGTONE, isOwnCommunicationLink = false) shouldBe AudioMode.Ringtone
        audioModeOf(AudioManager.MODE_IN_CALL, isOwnCommunicationLink = false) shouldBe AudioMode.InCall
        audioModeOf(AudioManager.MODE_CALL_SCREENING, isOwnCommunicationLink = false) shouldBe AudioMode.InCall
        audioModeOf(AudioManager.MODE_CALL_REDIRECT, isOwnCommunicationLink = false) shouldBe AudioMode.InCall
    }

    @Test
    fun `the communication modes map to communication`() {
        audioModeOf(AudioManager.MODE_IN_COMMUNICATION, isOwnCommunicationLink = false) shouldBe AudioMode.InCommunication
        audioModeOf(AudioManager.MODE_COMMUNICATION_REDIRECT, isOwnCommunicationLink = false) shouldBe AudioMode.InCommunication
    }

    @Test
    fun `the communication mode the session sets itself on a Bluetooth link is normal`() {
        audioModeOf(AudioManager.MODE_IN_COMMUNICATION, isOwnCommunicationLink = true) shouldBe AudioMode.Normal
    }

    @Test
    fun `the own link never hides a real call`() {
        audioModeOf(AudioManager.MODE_IN_CALL, isOwnCommunicationLink = true) shouldBe AudioMode.InCall
        audioModeOf(AudioManager.MODE_RINGTONE, isOwnCommunicationLink = true) shouldBe AudioMode.Ringtone
    }

    @Test
    fun `the normal mode maps to normal`() {
        audioModeOf(AudioManager.MODE_NORMAL, isOwnCommunicationLink = false) shouldBe AudioMode.Normal
    }

    @Test
    fun `headsets are headset-class outputs and the phone speaker is not`() {
        isHeadsetClassOutput(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) shouldBe true
        isHeadsetClassOutput(AudioDeviceInfo.TYPE_BLUETOOTH_SCO) shouldBe true
        isHeadsetClassOutput(AudioDeviceInfo.TYPE_WIRED_HEADSET) shouldBe true
        isHeadsetClassOutput(AudioDeviceInfo.TYPE_USB_HEADSET) shouldBe true
        isHeadsetClassOutput(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) shouldBe false
    }

    @Test
    fun `a duplicate disconnect inside the window is reported once`() {
        var elapsed = 10.seconds
        val deduper = OutputDisconnectDeduper(elapsedNow = { elapsed })

        val first = deduper.shouldReport()
        elapsed += 200.milliseconds
        val duplicate = deduper.shouldReport()

        first shouldBe true
        duplicate shouldBe false
    }

    @Test
    fun `a later disconnect is reported again`() {
        var elapsed = 10.seconds
        val deduper = OutputDisconnectDeduper(elapsedNow = { elapsed })
        deduper.shouldReport()

        elapsed += 10.seconds
        val later = deduper.shouldReport()

        later shouldBe true
    }
}
