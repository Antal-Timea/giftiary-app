package app.gifty.presentation.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gifty.domain.model.Person
import app.gifty.domain.repository.PersonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val repository: PersonRepository,
) : ViewModel() {

    private val personPendingDeletion = MutableStateFlow<Person?>(null)
    private val deleteFailed = MutableStateFlow(false)

    private val people = repository.observePeople()
        .map<List<Person>, PeopleContent> { PeopleContent.Loaded(it) }
        .catch { emit(PeopleContent.LoadFailed) }

    val uiState: StateFlow<PeopleUiState> =
        combine(people, personPendingDeletion, deleteFailed) { content, pendingDeletion, failed ->
            PeopleUiState(content, pendingDeletion, failed)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = PeopleUiState(),
        )

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
    }
}

data class PeopleUiState(
    val content: PeopleContent = PeopleContent.Loading,
    val personPendingDeletion: Person? = null,
    val deleteFailed: Boolean = false,
)

sealed interface PeopleContent {
    /** Only until Room's first emission, to avoid flashing the empty state. */
    data object Loading : PeopleContent
    data class Loaded(val people: List<Person>) : PeopleContent
    data object LoadFailed : PeopleContent
}
