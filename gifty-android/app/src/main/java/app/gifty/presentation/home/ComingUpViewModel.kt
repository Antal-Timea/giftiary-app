package app.gifty.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gifty.domain.model.UpcomingOccasion
import app.gifty.domain.usecase.ObserveUpcomingOccasionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.UUID
import javax.inject.Inject

/**
 * The Home "Coming up" section. It is kept apart from the sync status so that neither can hide
 * the other when its local read fails.
 */
@HiltViewModel
class ComingUpViewModel @Inject constructor(
    observeUpcomingOccasions: ObserveUpcomingOccasionsUseCase,
) : ViewModel() {

    val content: StateFlow<ComingUpContent> = observeUpcomingOccasions()
        .map<List<UpcomingOccasion>, ComingUpContent> { upcoming ->
            ComingUpContent.Loaded(upcoming.take(MAX_ROWS).map { ComingUpItem(it, it.openTarget()) })
        }
        .catch { emit(ComingUpContent.Unavailable) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ComingUpContent.Loading,
        )

    // An occasion of the user's own has no person to show, but its editor needs only its id.
    private fun UpcomingOccasion.openTarget(): ComingUpTarget =
        person?.let { ComingUpTarget.Person(it.id) } ?: ComingUpTarget.Occasion(occasion.id)

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

data class ComingUpItem(val upcoming: UpcomingOccasion, val target: ComingUpTarget)

sealed interface ComingUpTarget {
    data class Person(val personId: UUID) : ComingUpTarget

    data class Occasion(val occasionId: UUID) : ComingUpTarget
}
