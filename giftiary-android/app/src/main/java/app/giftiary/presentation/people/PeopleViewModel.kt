package app.giftiary.presentation.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.model.Person
import app.giftiary.domain.repository.PersonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val repository: PersonRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // The person shown beside the list on a wide window; saved, so it survives process death.
    private val selectedId = savedStateHandle.getStateFlow<String?>(SELECTED_PERSON_KEY, null)
    private val personPendingDeletion = MutableStateFlow<Person?>(null)
    private val deleteFailed = MutableStateFlow(false)

    private val people = repository.observePeople()
        .map<List<Person>, PeopleContent> { PeopleContent.Loaded(it) }
        .catch { emit(PeopleContent.LoadFailed) }
        // A selected person deleted here or by sync is forgotten rather than kept as a dead id.
        .onEach { content -> if (content is PeopleContent.Loaded) forgetSelectionUnlessIn(content.people) }

    val uiState: StateFlow<PeopleUiState> =
        combine(people, selectedId, personPendingDeletion, deleteFailed) { content, selected, pendingDeletion, failed ->
            // Only a loaded person is selected: while loading, a restored id waits instead of showing as missing.
            val selectedPersonId = (content as? PeopleContent.Loaded)?.people
                ?.firstOrNull { it.id.toString() == selected }
                ?.id
            PeopleUiState(content, pendingDeletion, failed, selectedPersonId)
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

    fun onDeleteRequested(person: Person) {
        personPendingDeletion.value = person
    }

    fun onDeleteDismissed() {
        personPendingDeletion.value = null
    }

    fun onDeleteConfirmed() {
        val person = personPendingDeletion.value ?: return
        personPendingDeletion.value = null
        viewModelScope.launch {
            try {
                repository.deletePerson(person.id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                deleteFailed.value = true
            }
        }
    }

    fun onDeleteErrorShown() {
        deleteFailed.value = false
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val SELECTED_PERSON_KEY = "selectedPersonId"
    }
}

data class PeopleUiState(
    val content: PeopleContent = PeopleContent.Loading,
    val personPendingDeletion: Person? = null,
    val deleteFailed: Boolean = false,
    /** The person shown beside the list; only ever one of the loaded people. */
    val selectedPersonId: UUID? = null,
)

sealed interface PeopleContent {
    /** Only until Room's first emission, to avoid flashing the empty state. */
    data object Loading : PeopleContent
    data class Loaded(val people: List<Person>) : PeopleContent
    data object LoadFailed : PeopleContent
}
