package com.rossomak.flashcards.core.domain.model

import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.AudioModeChanged
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.FocusChanged
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.MicSilenced
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.MicUnsilenced
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.OutputDisconnected
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import org.junit.Test

class InterruptionEpisodeTest {

    private val timeSource = TestTimeSource()

    private fun InterruptionEpisode.receive(signal: AudioEnvironmentSignal, after: Duration = Duration.ZERO): InterruptionStep {
        timeSource += after
        return onSignal(signal, timeSource.markNow())
    }

    private fun InterruptionEpisode.after(signal: AudioEnvironmentSignal, after: Duration = Duration.ZERO): InterruptionEpisode =
        receive(signal, after).episode

    @Test
    fun `a duck loss starts a blip`() {
        val step = InterruptionEpisode().receive(FocusChanged(FocusChange.LossCanDuck))

        step.change shouldBe EpisodeChange.Started(InterruptionTier.Blip)
        step.episode.tier shouldBe InterruptionTier.Blip
        step.episode.isHolding shouldBe false
    }

    @Test
    fun `a transient loss starts an interruption`() {
        val step = InterruptionEpisode().receive(FocusChanged(FocusChange.LossTransient))

        step.change shouldBe EpisodeChange.Started(InterruptionTier.Interruption)
        step.episode.isHolding shouldBe true
    }

    @Test
    fun `a permanent loss starts a takeover`() {
        val step = InterruptionEpisode().receive(FocusChanged(FocusChange.Loss))

        step.change shouldBe EpisodeChange.Started(InterruptionTier.Takeover)
    }

    @Test
    fun `a transient loss while a call is already in progress starts a call`() {
        val inCall = InterruptionEpisode().after(AudioModeChanged(AudioMode.InCall))

        val step = inCall.receive(FocusChanged(FocusChange.LossTransient))

        step.change shouldBe EpisodeChange.Started(InterruptionTier.Call)
    }

    @Test
    fun `a duck loss while a call rings is an interruption, not a blip`() {
        val ringing = InterruptionEpisode().after(AudioModeChanged(AudioMode.Ringtone))

        val step = ringing.receive(FocusChanged(FocusChange.LossCanDuck))

        step.change shouldBe EpisodeChange.Started(InterruptionTier.Interruption)
    }

    @Test
    fun `a mode change with no episode only records the mode`() {
        val step = InterruptionEpisode().receive(AudioModeChanged(AudioMode.InCall))

        step.change shouldBe null
        step.episode.mode shouldBe AudioMode.InCall
        step.episode.isActive shouldBe false
    }

    @Test
    fun `a transient loss escalates a blip`() {
        val blip = InterruptionEpisode().after(FocusChanged(FocusChange.LossCanDuck))

        val step = blip.receive(FocusChanged(FocusChange.LossTransient))

        step.change shouldBe EpisodeChange.Escalated(InterruptionTier.Blip, InterruptionTier.Interruption)
    }

    @Test
    fun `picking up a call escalates an interruption to a call`() {
        val ringing = InterruptionEpisode().after(FocusChanged(FocusChange.LossTransient)).after(AudioModeChanged(AudioMode.Ringtone))

        val step = ringing.receive(AudioModeChanged(AudioMode.InCall))

        step.change shouldBe EpisodeChange.Escalated(InterruptionTier.Interruption, InterruptionTier.Call)
    }

    @Test
    fun `ringing alone keeps the interruption an interruption`() {
        val step = InterruptionEpisode().after(FocusChanged(FocusChange.LossTransient)).receive(AudioModeChanged(AudioMode.Ringtone))

        step.change shouldBe null
        step.episode.tier shouldBe InterruptionTier.Interruption
    }

    @Test
    fun `a call is sticky, so the episode ends as a call after the call ended`() {
        val ended = InterruptionEpisode()
            .after(FocusChanged(FocusChange.LossTransient))
            .after(AudioModeChanged(AudioMode.InCall), after = 2.seconds)
            .after(AudioModeChanged(AudioMode.Normal), after = 3.seconds)

        val step = ended.receive(FocusChanged(FocusChange.Gain), after = 1.seconds)

        step.change shouldBe EpisodeChange.Ended(EpisodeEnd.CallEnded)
    }

    @Test
    fun `a non-own communication mode is a call`() {
        val step = InterruptionEpisode()
            .after(FocusChanged(FocusChange.LossTransient))
            .receive(AudioModeChanged(AudioMode.InCommunication))

        step.change shouldBe EpisodeChange.Escalated(InterruptionTier.Interruption, InterruptionTier.Call)
    }

    @Test
    fun `a declined ring ends as an interruption that resumes on its own`() {
        val ended = InterruptionEpisode()
            .after(AudioModeChanged(AudioMode.Ringtone))
            .after(FocusChanged(FocusChange.LossTransient))
            .after(AudioModeChanged(AudioMode.Normal), after = 4.seconds)

        val step = ended.receive(FocusChanged(FocusChange.Gain))

        step.change shouldBe EpisodeChange.Ended(EpisodeEnd.AutoResume)
    }

    @Test
    fun `a gain inside the window ends an interruption with an auto-resume`() {
        val interruption = InterruptionEpisode().after(FocusChanged(FocusChange.LossTransient))

        val step = interruption.receive(FocusChanged(FocusChange.Gain), after = 59.seconds)

        step.change shouldBe EpisodeChange.Ended(EpisodeEnd.AutoResume)
        step.episode.isActive shouldBe false
    }

    @Test
    fun `a gain exactly at the window edge still auto-resumes`() {
        val interruption = InterruptionEpisode().after(FocusChanged(FocusChange.LossTransient))

        val step = interruption.receive(FocusChanged(FocusChange.Gain), after = 60.seconds)

        step.change shouldBe EpisodeChange.Ended(EpisodeEnd.AutoResume)
    }

    @Test
    fun `a gain after the window ends an interruption as expired`() {
        val interruption = InterruptionEpisode().after(FocusChanged(FocusChange.LossTransient))

        val step = interruption.receive(FocusChanged(FocusChange.Gain), after = 61.seconds)

        step.change shouldBe EpisodeChange.Ended(EpisodeEnd.Expired)
    }

    @Test
    fun `a cancelled resume ends an interruption as cancelled inside the window`() {
        val interruption = InterruptionEpisode().after(FocusChanged(FocusChange.LossTransient)).cancelResume()

        val step = interruption.receive(FocusChanged(FocusChange.Gain), after = 5.seconds)

        step.change shouldBe EpisodeChange.Ended(EpisodeEnd.Cancelled)
    }

    @Test
    fun `a blip ends as a passed blip`() {
        val blip = InterruptionEpisode().after(FocusChanged(FocusChange.LossCanDuck))

        val step = blip.receive(FocusChanged(FocusChange.Gain), after = 1.seconds)

        step.change shouldBe EpisodeChange.Ended(EpisodeEnd.BlipPassed)
    }

    @Test
    fun `an escalated blip follows the window from its first loss`() {
        val blip = InterruptionEpisode().after(FocusChanged(FocusChange.LossCanDuck))
        val escalated = blip.escalateBlip()

        val step = escalated.episode.receive(FocusChanged(FocusChange.Gain), after = 61.seconds)

        escalated.change shouldBe EpisodeChange.Escalated(InterruptionTier.Blip, InterruptionTier.Interruption)
        step.change shouldBe EpisodeChange.Ended(EpisodeEnd.Expired)
    }

    @Test
    fun `escalating an interruption changes nothing`() {
        val interruption = InterruptionEpisode().after(FocusChanged(FocusChange.LossTransient))

        val step = interruption.escalateBlip()

        step.change shouldBe null
        step.episode shouldBe interruption
    }

    @Test
    fun `a gain with no episode is ignored`() {
        val step = InterruptionEpisode().receive(FocusChanged(FocusChange.Gain))

        step.change shouldBe null
        step.episode.isActive shouldBe false
    }

    @Test
    fun `a takeover has no gain`() {
        val takeover = InterruptionEpisode().after(FocusChanged(FocusChange.Loss))

        val step = takeover.receive(FocusChanged(FocusChange.Gain), after = 5.seconds)

        step.change shouldBe null
        step.episode.tier shouldBe InterruptionTier.Takeover
    }

    @Test
    fun `clearing a takeover ends the episode and keeps the latest mode`() {
        val takeover = InterruptionEpisode().after(AudioModeChanged(AudioMode.Ringtone)).after(FocusChanged(FocusChange.Loss))

        val cleared = takeover.cleared()

        cleared.isActive shouldBe false
        cleared.mode shouldBe AudioMode.Ringtone
    }

    @Test
    fun `a silenced microphone alone starts an interruption`() {
        val step = InterruptionEpisode().receive(MicSilenced)

        step.change shouldBe EpisodeChange.Started(InterruptionTier.Interruption)
    }

    @Test
    fun `a gain while the microphone is still silenced does not end the episode`() {
        val silenced = InterruptionEpisode().after(FocusChanged(FocusChange.LossTransient)).after(MicSilenced)

        val step = silenced.receive(FocusChanged(FocusChange.Gain), after = 5.seconds)

        step.change shouldBe null
        step.episode.isActive shouldBe true
    }

    @Test
    fun `the microphone coming back after the gain ends the episode`() {
        val waiting = InterruptionEpisode()
            .after(FocusChanged(FocusChange.LossTransient))
            .after(MicSilenced)
            .after(FocusChanged(FocusChange.Gain), after = 5.seconds)

        val step = waiting.receive(MicUnsilenced, after = 5.seconds)

        step.change shouldBe EpisodeChange.Ended(EpisodeEnd.AutoResume)
    }

    @Test
    fun `an unsilenced microphone with no episode is ignored`() {
        val step = InterruptionEpisode().receive(MicUnsilenced)

        step.change shouldBe null
    }

    @Test
    fun `an output disconnect changes nothing in the episode`() {
        val interruption = InterruptionEpisode().after(FocusChanged(FocusChange.LossTransient))

        val step = interruption.receive(OutputDisconnected)

        step.change shouldBe null
        step.episode shouldBe interruption
    }

    @Test
    fun `any mode but normal blocks a play`() {
        InterruptionEpisode().isCallBlocking shouldBe false
        InterruptionEpisode(mode = AudioMode.Ringtone).isCallBlocking shouldBe true
        InterruptionEpisode(mode = AudioMode.InCall).isCallBlocking shouldBe true
        InterruptionEpisode(mode = AudioMode.InCommunication).isCallBlocking shouldBe true
    }

    @Test
    fun `a blip marked long is a long blip only while it is a blip`() {
        val blip = InterruptionEpisode().after(FocusChanged(FocusChange.LossCanDuck)).markBlipLong()

        blip.isLongBlip shouldBe true
        blip.escalateBlip().episode.isLongBlip shouldBe false
    }
}
