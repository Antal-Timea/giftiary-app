package app.gifty.presentation.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gifty.domain.model.Occasion
import app.gifty.domain.model.Person
import app.gifty.domain.repository.OccasionRepository
import app.gifty.domain.repository.PersonRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

/**
 * Shows a person together with their occasions. Both come straight from Room, so edits made in
 * the person or occasion editors appear as soon as they are stored.
 */
@HiltViewModel(assistedFactory = PersonDetailViewModel.Factory::class)
class PersonDetailViewModel @AssistedInject constructor(
    @Assisted personId: UUID,
    personRepository: PersonRepository,
    occasionRepository: OccasionRepository,
) : ViewModel() {

    val uiState: StateFlow<PersonDetailUiState> =
        combine(
            personRepository.observePerson(personId),
            occasionRepository.observeOccasionsForPerson(personId),
        ) { person, occasions ->
            if (person == null) PersonDetailUiState.NotFound else PersonDetailUiState.Loaded(person, occasions)
        }
            .catch { emit(PersonDetailUiState.LoadFailed) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = PersonDetailUiState.Loading,
            )

    @AssistedFactory
    interface Factory {
        fun create(personId: UUID): PersonDetailViewModel
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

sealed interface PersonDetailUiState {
    /** Only until Room's first emission. */
    data object Loading : PersonDetailUiState
    data object NotFound : PersonDetailUiState
    data object LoadFailed : PersonDetailUiState
    data class Loaded(val person: Person, val occasions: List<Occasion>) : PersonDetailUiState
}
