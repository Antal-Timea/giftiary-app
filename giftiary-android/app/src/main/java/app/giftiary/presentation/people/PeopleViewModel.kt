package app.giftiary.presentation.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.model.Person
import app.giftiary.domain.repository.OccasionRepository
import app.giftiary.domain.repository.PersonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PeopleViewModel @Inject constructor(
    repository: PersonRepository,
    occasionRepository: OccasionRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // The person shown beside the list on a wide window; saved, so it survives process death.
    private val selectedId = savedStateHandle.getStateFlow<String?>(SELECTED_PERSON_KEY, null)
    private val query = savedStateHandle.getStateFlow(QUERY_KEY, "")

    // Birthdays only add a detail to the rows; ones that cannot be read leave the rows without it.
    private val birthdays = occasionRepository.observeOccasions()
        .map { entries ->
            entries.map { it.occasion }
                .filter { it.type == OccasionType.BIRTHDAY && it.personId != null }
                .associateBy { it.personId!! }
        }
        .catch { emit(emptyMap()) }

    private val people = repository.observePeople()
        .map<List<Person>, PeopleContent> { PeopleContent.Loaded(it) }
        .catch { emit(PeopleContent.LoadFailed) }
        // A selected person deleted here or by sync is forgotten rather than kept as a dead id.
        .onEach { content -> if (content is PeopleContent.Loaded) forgetSelectionUnlessIn(content.people) }

    val uiState: StateFlow<PeopleUiState> =
        combine(people, selectedId, query, birthdays) { content, selected, query, birthdays ->
            // Only a loaded person is selected: while loading, a restored id waits instead of showing as missing.
            val selectedPersonId = (content as? PeopleContent.Loaded)?.people
                ?.firstOrNull { it.id.toString() == selected }
                ?.id
            PeopleUiState(content, selectedPersonId, query, birthdays)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = PeopleUiState(),
        )

    fun onPersonSelected(person: Person) {
        savedStateHandle[SELECTED_PERSON_KEY] = person.id.toString()
    }

    fun onSelectionCleared() {
        savedStateHandle[SELECTED_PERSON_KEY] = null
    }

    private fun forgetSelectionUnlessIn(people: List<Person>) {
        val selected = selectedId.value ?: return
        if (people.none { it.id.toString() == selected }) onSelectionCleared()
    }

    fun onQueryChange(query: String) {
        savedStateHandle[QUERY_KEY] = query
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val SELECTED_PERSON_KEY = "selectedPersonId"
        const val QUERY_KEY = "query"
    }
}

data class PeopleUiState(
    val content: PeopleContent = PeopleContent.Loading,
    /** The person shown beside the list; only ever one of the loaded people. */
    val selectedPersonId: UUID? = null,
    val query: String = "",
    /** Each person's birthday, for the rows' second line. */
    val birthdays: Map<UUID, Occasion> = emptyMap(),
) {
    /** The loaded people the search leaves, by name or relationship; all of them without a search. */
    val matchingPeople: List<Person>
        get() {
            val people = (content as? PeopleContent.Loaded)?.people.orEmpty()
            val search = query.trim()
            if (search.isEmpty()) return people
            return people.filter { person ->
                person.name.contains(search, ignoreCase = true) || person.relationship?.contains(search, ignoreCase = true) == true
            }
        }
}

sealed interface PeopleContent {
    /** Only until Room's first emission, to avoid flashing the empty state. */
    data object Loading : PeopleContent
    data class Loaded(val people: List<Person>) : PeopleContent
    data object LoadFailed : PeopleContent
}
