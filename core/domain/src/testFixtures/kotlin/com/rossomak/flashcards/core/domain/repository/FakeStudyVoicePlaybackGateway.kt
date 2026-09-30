package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.PlaybackEvent
import com.rossomak.flashcards.core.domain.model.SpokenNotice
import com.rossomak.flashcards.core.domain.model.TransportCommand
import com.rossomak.flashcards.core.domain.model.TransportCommandType
import com.rossomak.flashcards.core.domain.model.VoicePlaybackState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * Records every command in [calls], in order, and moves [state] the way the real player would:
 * start plays from the start index, pause and play flip `isPlaying`, presenting a part moves the
 * presented index. Like the real player, [presentAnswer] reports the answer revealed, and presenting
 * an index outside the list stops playing. Tests drive what else the player reports with
 * [finishQuestion], [finishAnswer], [finishNotice] and [emit].
 */
class FakeStudyVoicePlaybackGateway : StudyVoicePlaybackGateway {

    /** One recorded command. */
    sealed interface Call {
        data class Start(val cardIds: List<String>, val startIndex: Int, val sessionTitle: String, val isVoiceAnsweringSession: Boolean) : Call
        data class UpdateQueue(val cardIds: List<String>) : Call
        data object Stop : Call
        data object Play : Call
        data object Pause : Call
        data class PresentQuestion(val index: Int) : Call
        data class PresentAnswer(val index: Int) : Call
        data class SetSpeechRate(val rate: Float) : Call
        data class SetVoice(val voiceId: String?) : Call
        data class SpeakNotice(val notice: SpokenNotice) : Call
        data object StopFeedback : Call
        data object ResumeWithoutReading : Call
    }

    /** One [setSessionProgress] call. */
    data class SessionProgress(val completedCount: Int, val totalCount: Int)

    override val state = MutableStateFlow(VoicePlaybackState())

    private val eventChannel = Channel<PlaybackEvent>(Channel.UNLIMITED)
    override val playbackEvents: Flow<PlaybackEvent> = eventChannel.receiveAsFlow()

    val calls = mutableListOf<Call>()

    /** Runs right after each command is recorded, still inside that command, as a player reacting at once would. */
    var onCall: ((Call) -> Unit)? = null

    /** The player's current list, as last started or updated. */
    var cards: List<Flashcard> = emptyList()
        private set

    /** The index the player presents, which [finishQuestion] and [finishAnswer] report on. */
    private var presentedIndex = 0

    /** Notices spoken and not yet finished by [finishNotice], oldest first. */
    val speakingNotices = mutableListOf<SpokenNotice>()

    val startCalls: List<Call.Start> get() = calls.filterIsInstance<Call.Start>()
    val stopCount: Int get() = calls.count { it == Call.Stop }
    val playCount: Int get() = calls.count { it == Call.Play }
    val pauseCount: Int get() = calls.count { it == Call.Pause }
    val presentedQuestions: List<Int> get() = calls.filterIsInstance<Call.PresentQuestion>().map { it.index }
    val presentedAnswers: List<Int> get() = calls.filterIsInstance<Call.PresentAnswer>().map { it.index }
    val lastSpeechRate: Float? get() = calls.filterIsInstance<Call.SetSpeechRate>().lastOrNull()?.rate
    val lastVoiceId: String? get() = calls.filterIsInstance<Call.SetVoice>().lastOrNull()?.voiceId
    val spokenNotices: List<SpokenNotice> get() = calls.filterIsInstance<Call.SpeakNotice>().map { it.notice }

    // Kept apart from [calls]: they follow every state change, and would crowd the command order.
    /** Every [setAvailableCommands] call, in order. */
    val availableCommandsUpdates = mutableListOf<Set<TransportCommandType>>()

    /** Every [setSessionProgress] call, in order. */
    val sessionProgressUpdates = mutableListOf<SessionProgress>()

    private fun record(call: Call) {
        calls += call
        onCall?.invoke(call)
    }

    fun emit(event: PlaybackEvent) {
        eventChannel.trySend(event)
    }

    /** Reports a transport [command] from outside the app. */
    fun emitExternal(command: TransportCommand) = emit(PlaybackEvent.ExternalCommand(command))

    /** Reports that the presented card's question was read in full. */
    fun finishQuestion() {
        cards.getOrNull(presentedIndex)?.let { emit(PlaybackEvent.QuestionFinished(it.id)) }
    }

    /** Reports that the presented card's answer was read in full. */
    fun finishAnswer() {
        cards.getOrNull(presentedIndex)?.let { emit(PlaybackEvent.AnswerFinished(it.id)) }
    }

    /** Reports the oldest spoken notice as finished. */
    fun finishNotice() {
        emit(PlaybackEvent.NoticeFinished(speakingNotices.removeAt(0)))
    }

    override fun start(cards: List<Flashcard>, startIndex: Int, sessionTitle: String, isVoiceAnsweringSession: Boolean) {
        record(Call.Start(cards.map { it.id }, startIndex, sessionTitle, isVoiceAnsweringSession))
        this.cards = cards
        presentedIndex = startIndex
        state.update { it.copy(isActive = cards.isNotEmpty(), isPlaying = cards.isNotEmpty()) }
    }

    override fun updateQueue(cards: List<Flashcard>) {
        record(Call.UpdateQueue(cards.map { it.id }))
        this.cards = cards
        presentedIndex = 0
        state.update { it.copy(isActive = it.isActive && cards.isNotEmpty()) }
    }

    override fun stop() {
        record(Call.Stop)
        cards = emptyList()
        state.value = VoicePlaybackState()
    }

    override fun play() {
        record(Call.Play)
        state.update { it.copy(isPlaying = it.isActive) }
    }

    override fun pause() {
        record(Call.Pause)
        state.update { it.copy(isPlaying = false) }
    }

    override fun presentQuestion(index: Int) {
        record(Call.PresentQuestion(index))
        if (index in cards.indices) presentedIndex = index else state.update { it.copy(isPlaying = false) }
    }

    override fun presentAnswer(index: Int) {
        record(Call.PresentAnswer(index))
        val card = cards.getOrNull(index) ?: return
        presentedIndex = index
        emit(PlaybackEvent.AnswerRevealed(card.id))
    }

    override fun setSpeechRate(rate: Float) {
        record(Call.SetSpeechRate(rate))
    }

    override fun setVoice(voiceId: String?) {
        record(Call.SetVoice(voiceId))
    }

    override fun speakNotice(notice: SpokenNotice) {
        record(Call.SpeakNotice(notice))
        speakingNotices += notice
    }

    /** Like the real player, a stopped feedback never reports finished. */
    override fun stopFeedback() {
        record(Call.StopFeedback)
        speakingNotices.removeAll { it is SpokenNotice.Feedback }
    }

    override fun resumeWithoutReading() {
        record(Call.ResumeWithoutReading)
        state.update { it.copy(isPlaying = it.isActive) }
    }

    override fun setAvailableCommands(commands: Set<TransportCommandType>) {
        availableCommandsUpdates += commands
    }

    override fun setSessionProgress(completedCount: Int, totalCount: Int) {
        sessionProgressUpdates += SessionProgress(completedCount, totalCount)
    }
}
