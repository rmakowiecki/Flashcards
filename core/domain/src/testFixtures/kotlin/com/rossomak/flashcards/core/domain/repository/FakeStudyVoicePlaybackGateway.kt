package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.PlaybackEvent
import com.rossomak.flashcards.core.domain.model.SpokenNotice
import com.rossomak.flashcards.core.domain.model.TransportCommand
import com.rossomak.flashcards.core.domain.model.VoicePhase
import com.rossomak.flashcards.core.domain.model.VoicePlaybackState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * Records every command in [calls], in order, and moves [state] the way the real player would:
 * start plays from the start index, pause and play flip `isPlaying`, card moves change the index.
 * Like the real player, [showAnswer] reports the answer revealed. Tests drive what else the player
 * reports with [readAnswer], [finishQuestion], [finishNotice], [reachAdvancePoint] and [emit].
 */
class FakeStudyVoicePlaybackGateway : StudyVoicePlaybackGateway {

    /** One recorded command. */
    sealed interface Call {
        data class Start(val cardIds: List<String>, val startIndex: Int, val sessionTitle: String, val isVoiceAnsweringSession: Boolean) : Call
        data class UpdateQueue(val cardIds: List<String>) : Call
        data object Stop : Call
        data object Play : Call
        data object Pause : Call
        data object MoveToNextCard : Call
        data object MoveToPreviousCard : Call
        data class JumpTo(val index: Int) : Call
        data object RestartCurrentCard : Call
        data object ShowAnswer : Call
        data object AdvanceAfterVoiceAnswer : Call
        data class SetQuestionOnlyMode(val enabled: Boolean) : Call
        data class SetAdvanceGate(val closed: Boolean) : Call
        data class SetSpeechRate(val rate: Float) : Call
        data class SetVoice(val voiceId: String?) : Call
        data class SpeakNotice(val notice: SpokenNotice) : Call
    }

    override val state = MutableStateFlow(VoicePlaybackState())

    private val eventChannel = Channel<PlaybackEvent>(Channel.UNLIMITED)
    override val playbackEvents: Flow<PlaybackEvent> = eventChannel.receiveAsFlow()

    val calls = mutableListOf<Call>()

    /** The player's current list, as last started or updated. */
    var cards: List<Flashcard> = emptyList()
        private set

    /** Notices spoken and not yet finished by [finishNotice], oldest first. */
    val speakingNotices = mutableListOf<SpokenNotice>()

    val startCalls: List<Call.Start> get() = calls.filterIsInstance<Call.Start>()
    val stopCount: Int get() = calls.count { it == Call.Stop }
    val playCount: Int get() = calls.count { it == Call.Play }
    val pauseCount: Int get() = calls.count { it == Call.Pause }
    val showAnswerCount: Int get() = calls.count { it == Call.ShowAnswer }
    val restartCurrentCardCount: Int get() = calls.count { it == Call.RestartCurrentCard }
    val advanceAfterVoiceAnswerCount: Int get() = calls.count { it == Call.AdvanceAfterVoiceAnswer }
    val lastSpeechRate: Float? get() = calls.filterIsInstance<Call.SetSpeechRate>().lastOrNull()?.rate
    val lastVoiceId: String? get() = calls.filterIsInstance<Call.SetVoice>().lastOrNull()?.voiceId
    val spokenNotices: List<SpokenNotice> get() = calls.filterIsInstance<Call.SpeakNotice>().map { it.notice }

    /** The advance gate, as last set. */
    var isAdvanceGateClosed: Boolean = false
        private set

    fun emit(event: PlaybackEvent) {
        eventChannel.trySend(event)
    }

    /** Reports a transport [command] from outside the app. */
    fun emitExternal(command: TransportCommand) = emit(PlaybackEvent.ExternalCommand(command))

    /** Moves the presented card to its answer phase and reports it revealed, as reading on by itself does. */
    fun readAnswer() {
        state.update { it.copy(phase = VoicePhase.Answer) }
        cards.getOrNull(state.value.currentIndex)?.let { emit(PlaybackEvent.AnswerRevealed(it.id)) }
    }

    /** Reports that the presented card's question has been read, as question-only mode does. */
    fun finishQuestion() {
        cards.getOrNull(state.value.currentIndex)?.let { emit(PlaybackEvent.QuestionFinished(it.id)) }
    }

    /**
     * Finishes the pause after the presented card's answer, as the real player's loop does: with the
     * gate closed it stops there and reports [PlaybackEvent.AdvanceGateReached]; otherwise it moves
     * on to the next card, or reports [PlaybackEvent.EndReached] after the last one.
     */
    fun reachAdvancePoint() {
        val current = state.value
        when {
            isAdvanceGateClosed -> {
                state.update { it.copy(isPlaying = false) }
                emit(PlaybackEvent.AdvanceGateReached)
            }
            current.currentIndex < cards.lastIndex -> state.update { it.copy(currentIndex = it.currentIndex + 1, phase = VoicePhase.Question) }
            else -> {
                state.update { it.copy(isPlaying = false, phase = VoicePhase.Question) }
                emit(PlaybackEvent.EndReached)
            }
        }
    }

    /** Reports the oldest spoken notice as finished. */
    fun finishNotice() {
        emit(PlaybackEvent.NoticeFinished(speakingNotices.removeAt(0)))
    }

    override fun start(cards: List<Flashcard>, startIndex: Int, sessionTitle: String, isVoiceAnsweringSession: Boolean) {
        calls += Call.Start(cards.map { it.id }, startIndex, sessionTitle, isVoiceAnsweringSession)
        this.cards = cards
        state.update {
            it.copy(
                isActive = cards.isNotEmpty(),
                isPlaying = cards.isNotEmpty(),
                currentIndex = startIndex,
                totalCards = cards.size,
                phase = VoicePhase.Question,
            )
        }
    }

    override fun updateQueue(cards: List<Flashcard>) {
        calls += Call.UpdateQueue(cards.map { it.id })
        this.cards = cards
        state.update { it.copy(currentIndex = 0, totalCards = cards.size, isActive = it.isActive && cards.isNotEmpty()) }
    }

    override fun stop() {
        calls += Call.Stop
        cards = emptyList()
        isAdvanceGateClosed = false
        state.value = VoicePlaybackState()
    }

    override fun play() {
        calls += Call.Play
        state.update { it.copy(isPlaying = it.isActive) }
    }

    override fun pause() {
        calls += Call.Pause
        state.update { it.copy(isPlaying = false) }
    }

    override fun moveToNextCard() {
        calls += Call.MoveToNextCard
        state.update { if (it.currentIndex < cards.lastIndex) it.copy(currentIndex = it.currentIndex + 1, phase = VoicePhase.Question) else it }
    }

    override fun moveToPreviousCard() {
        calls += Call.MoveToPreviousCard
        state.update { if (it.currentIndex > 0) it.copy(currentIndex = it.currentIndex - 1, phase = VoicePhase.Question) else it }
    }

    override fun jumpTo(index: Int) {
        calls += Call.JumpTo(index)
        state.update { it.copy(currentIndex = index.coerceIn(0, maxOf(0, cards.lastIndex)), phase = VoicePhase.Question) }
    }

    override fun restartCurrentCard() {
        calls += Call.RestartCurrentCard
        state.update { it.copy(phase = VoicePhase.Question) }
    }

    override fun showAnswer() {
        calls += Call.ShowAnswer
        readAnswer()
    }

    override fun advanceAfterVoiceAnswer() {
        calls += Call.AdvanceAfterVoiceAnswer
        state.update { it.copy(currentIndex = 0, phase = VoicePhase.Question, isPlaying = cards.isNotEmpty()) }
    }

    override fun setQuestionOnlyMode(enabled: Boolean) {
        calls += Call.SetQuestionOnlyMode(enabled)
    }

    override fun setAdvanceGate(closed: Boolean) {
        calls += Call.SetAdvanceGate(closed)
        isAdvanceGateClosed = closed
    }

    override fun setSpeechRate(rate: Float) {
        calls += Call.SetSpeechRate(rate)
        state.update { it.copy(speechRate = rate) }
    }

    override fun setVoice(voiceId: String?) {
        calls += Call.SetVoice(voiceId)
    }

    override fun speakNotice(notice: SpokenNotice) {
        calls += Call.SpeakNotice(notice)
        speakingNotices += notice
    }
}
