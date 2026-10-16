package app.gifty.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gifty.domain.model.OccasionPlan
import app.gifty.domain.model.UpcomingOccasion
import app.gifty.domain.repository.GiftIdeaRepository
import app.gifty.domain.usecase.ObserveUpcomingOccasionsUseCase
import app.gifty.domain.usecase.occasionPlans
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.UUID
import javax.inject.Inject

/**
 * The Home "Coming up" section, with what is being planned for each occasion. It is kept apart
 * from the sync status so that neither can hide the other when its local read fails.
 */
@HiltViewModel
class ComingUpViewModel @Inject constructor(
    observeUpcomingOccasions: ObserveUpcomingOccasionsUseCase,
    giftIdeaRepository: GiftIdeaRepository,
) : ViewModel() {

    // Plans only add to the rows: ideas that cannot be read leave the occasions shown without them.
    private val plans = giftIdeaRepository.observeGiftIdeas()
        .map { entries -> occasionPlans(entries.map { it.idea }) }
        .catch { emit(emptyMap()) }

    val content: StateFlow<ComingUpContent> = combine(observeUpcomingOccasions(), plans) { upcoming, plans ->
        ComingUpContent.Loaded(upcoming.take(MAX_ROWS).map { ComingUpItem(it, plans[it.occasion.id]) })
    }
        .catch<ComingUpContent> { emit(ComingUpContent.Unavailable) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ComingUpContent.Loading,
        )

    companion object {
        const val MAX_ROWS = 5
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

sealed interface ComingUpContent {
    data object Loading : ComingUpContent

    data object Unavailable : ComingUpContent

    data class Loaded(val items: List<ComingUpItem>) : ComingUpContent
}

/** [plan] is null while nothing is being planned for the occasion. A row opens its occasion's plan. */
data class ComingUpItem(val upcoming: UpcomingOccasion, val plan: OccasionPlan? = null) {
    val occasionId: UUID get() = upcoming.occasion.id
}
