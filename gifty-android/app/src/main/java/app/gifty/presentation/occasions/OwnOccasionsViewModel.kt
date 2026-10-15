package app.gifty.presentation.occasions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gifty.domain.model.Occasion
import app.gifty.domain.repository.OccasionEntry
import app.gifty.domain.repository.OccasionRepository
import app.gifty.presentation.gifts.generalOccasions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The user's own occasions, those of no person, such as Christmas. Person detail lists a person's
 * occasions; these have no other place where they can always be found and edited.
 */
@HiltViewModel
class OwnOccasionsViewModel @Inject constructor(
    repository: OccasionRepository,
) : ViewModel() {

    val content: StateFlow<OwnOccasionsContent> = repository.observeOccasions()
        .map<List<OccasionEntry>, OwnOccasionsContent> { entries ->
            OwnOccasionsContent.Loaded(generalOccasions(entries))
        }
        .catch { emit(OwnOccasionsContent.LoadFailed) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = OwnOccasionsContent.Loading,
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

sealed interface OwnOccasionsContent {
    data object Loading : OwnOccasionsContent

    data object LoadFailed : OwnOccasionsContent

    /** In calendar order, as a person's occasions are listed. */
    data class Loaded(val occasions: List<Occasion>) : OwnOccasionsContent
}
