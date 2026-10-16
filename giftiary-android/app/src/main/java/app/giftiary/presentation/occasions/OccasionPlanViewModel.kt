package app.giftiary.presentation.occasions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.OccasionPlan
import app.giftiary.domain.model.Person
import app.giftiary.domain.model.nextOccurrence
import app.giftiary.domain.model.turningAge
import app.giftiary.domain.repository.GiftIdeaEntry
import app.giftiary.domain.repository.GiftIdeaRepository
import app.giftiary.domain.repository.OccasionEntry
import app.giftiary.domain.repository.OccasionRepository
import app.giftiary.domain.usecase.occasionPlans
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * An occasion and what is being planned for it: its next date and the open gift ideas linked to
 * it, of whoever they are for. The ideas only add to the page; when they cannot be read, the
 * occasion is still shown.
 */
@HiltViewModel(assistedFactory = OccasionPlanViewModel.Factory::class)
class OccasionPlanViewModel @AssistedInject constructor(
    @Assisted private val occasionId: UUID,
    occasionRepository: OccasionRepository,
    giftIdeaRepository: GiftIdeaRepository,
    private val clock: Clock,
    private val zone: ZoneId,
) : ViewModel() {

    private val occasion = occasionRepository.observeOccasions()
        .map { entries -> entries.find { it.occasion.id == occasionId } }

    private val ideas = giftIdeaRepository.observeGiftIdeas()
        .map<List<GiftIdeaEntry>, OccasionIdeas> { entries -> OccasionIdeas.Loaded(entries.filter { it.idea.occasionId == occasionId }) }
        .catch { emit(OccasionIdeas.Unavailable) }

    val uiState: StateFlow<OccasionPlanUiState> = combine(occasion, ideas) { entry, ideas ->
        if (entry == null) OccasionPlanUiState.NotFound else loaded(entry, ideas)
    }
        .catch<OccasionPlanUiState> { emit(OccasionPlanUiState.LoadFailed) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = OccasionPlanUiState.Loading,
        )

    private fun loaded(entry: OccasionEntry, ideas: OccasionIdeas): OccasionPlanUiState.Loaded {
        val today = clock.instant().atZone(zone).toLocalDate()
        val next = entry.occasion.nextOccurrence(onOrAfter = today)
        return OccasionPlanUiState.Loaded(
            occasion = entry.occasion,
            person = entry.person,
            nextDate = next,
            daysUntil = next?.let { ChronoUnit.DAYS.between(today, it).toInt() },
            turningAge = next?.let { turningAge(entry.occasion, it) },
            plan = (ideas as? OccasionIdeas.Loaded)?.let { loaded -> occasionPlans(loaded.ideas.map { it.idea })[occasionId] },
            ideas = ideas,
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(occasionId: UUID): OccasionPlanViewModel
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

sealed interface OccasionPlanUiState {
    data object Loading : OccasionPlanUiState

    data object LoadFailed : OccasionPlanUiState

    /** Deleted, never existed, or not the current account's. */
    data object NotFound : OccasionPlanUiState

    data class Loaded(
        val occasion: Occasion,
        /** Null for an occasion of the user's own. */
        val person: Person?,
        /** Null once a one-time occasion has passed. */
        val nextDate: LocalDate?,
        val daysUntil: Int?,
        val turningAge: Int?,
        /** Null while no open idea is linked, or while the ideas cannot be read. */
        val plan: OccasionPlan?,
        val ideas: OccasionIdeas,
    ) : OccasionPlanUiState
}

sealed interface OccasionIdeas {
    data object Unavailable : OccasionIdeas

    /** The open ideas linked to the occasion, most recently changed first. */
    data class Loaded(val ideas: List<GiftIdeaEntry>) : OccasionIdeas
}
