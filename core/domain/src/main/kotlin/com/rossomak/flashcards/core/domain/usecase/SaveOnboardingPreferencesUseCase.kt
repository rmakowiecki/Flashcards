package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.DefaultStudyMode
import com.rossomak.flashcards.core.domain.model.UserPreference.DailyGoalMinutes
import com.rossomak.flashcards.core.domain.model.UserPreference.HasSeenOnboarding
import com.rossomak.flashcards.core.domain.usecase.base.UseCase
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Onboarding's only writing capability. Composing the two savers rather than injecting them
 * directly means mode, goal and the completion flag are the only preferences reachable from that
 * screen — sort order, length and attempts are unreachable by construction, not by convention.
 *
 * Three transactions rather than one, deliberately. The flag is written last, so the only
 * reachable partial state is "choices saved, flow replays next launch", which is benign.
 */
class SaveOnboardingPreferencesUseCase @Inject constructor(
    private val saveStudySessionPreference: SaveStudySessionPreferenceUseCase,
    private val saveUserPreference: SaveUserPreferenceUseCase,
) : UseCase<SaveOnboardingPreferencesUseCase.Params, Result<Unit>> {

    data class Params(val defaultStudyMode: StudyMode, val dailyGoalMinutes: Int)

    // Not `mapCatching`, chained: it catches `CancellationException` same as any other `Throwable` and boxes it into `Result.failure`
    // instead of letting it propagate, which would break structured concurrency for a cancelled onboarding coroutine.
    override suspend operator fun invoke(params: Params): Result<Unit> =
        try {
            saveStudySessionPreference(DefaultStudyMode(params.defaultStudyMode)).getOrThrow()
            saveUserPreference(DailyGoalMinutes(params.dailyGoalMinutes)).getOrThrow()
            saveUserPreference(HasSeenOnboarding(true)).getOrThrow()
            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
}
