package app.giftiary.presentation.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.repository.PersonNotFoundException
import app.giftiary.domain.repository.PersonRepository
import app.giftiary.domain.usecase.SavePersonResult
import app.giftiary.domain.usecase.SavePersonUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Backs both adding ([personId] is null) and editing a person.
 */
@HiltViewModel(assistedFactory = PersonEditorViewModel.Factory::class)
class PersonEditorViewModel @AssistedInject constructor(
    @Assisted private val personId: UUID?,
    private val repository: PersonRepository,
    private val savePerson: SavePersonUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PersonEditorUiState(
            isNewPerson = personId == null,
            status = if (personId == null) EditorStatus.EDITING else EditorStatus.LOADING,
        ),
    )
    val uiState: StateFlow<PersonEditorUiState> = _uiState.asStateFlow()

    init {
        if (personId != null) load(personId)
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name, showNameRequired = false) }
    }

    fun onRelationshipChange(relationship: String) {
        _uiState.update { it.copy(relationship = relationship) }
    }

    fun onSaveErrorShown() {
        _uiState.update { it.copy(saveFailed = false) }
    }

    fun onDeleteRequested() {
        _uiState.update { if (personId != null && it.status == EditorStatus.EDITING) it.copy(confirmingDelete = true) else it }
    }

    fun onDeleteDismissed() {
        _uiState.update { it.copy(confirmingDelete = false) }
    }

    fun onDeleteErrorShown() {
        _uiState.update { it.copy(deleteFailed = false) }
    }

    fun onDeleteConfirmed() {
        val state = _uiState.value
        if (personId == null || state.status != EditorStatus.EDITING || state.isBusy) return

        _uiState.update { it.copy(confirmingDelete = false, isDeleting = true, deleteFailed = false) }
        viewModelScope.launch {
            try {
                repository.deletePerson(personId)
                _uiState.update { it.copy(status = EditorStatus.DELETED) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(isDeleting = false, deleteFailed = true) }
            }
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.status != EditorStatus.EDITING || state.isBusy) return

        _uiState.update { it.copy(isSaving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                val result = if (personId == null) {
                    savePerson.add(state.name, state.relationship)
                } else {
                    savePerson.update(personId, state.name, state.relationship)
                }
                _uiState.update {
                    when (result) {
                        is SavePersonResult.Saved -> it.copy(status = EditorStatus.SAVED, savedPersonId = result.personId)
                        SavePersonResult.BlankName -> it.copy(isSaving = false, showNameRequired = true)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: PersonNotFoundException) {
                _uiState.update { it.copy(isSaving = false, status = EditorStatus.NOT_FOUND) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    private fun load(id: UUID) {
        viewModelScope.launch {
            try {
                val person = repository.getPerson(id)
                _uiState.update {
                    if (person == null) {
                        it.copy(status = EditorStatus.NOT_FOUND)
                    } else {
                        it.copy(
                            status = EditorStatus.EDITING,
                            name = person.name,
                            savedName = person.name,
                            relationship = person.relationship.orEmpty(),
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(status = EditorStatus.LOAD_FAILED) }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(personId: UUID?): PersonEditorViewModel
    }
}

data class PersonEditorUiState(
    val isNewPerson: Boolean,
    val status: EditorStatus,
    /** The person just saved, once [status] is SAVED. */
    val savedPersonId: UUID? = null,
    val name: String = "",
    val relationship: String = "",
    val showNameRequired: Boolean = false,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    /** The name as stored, which the delete confirmation names even while [name] is being edited. */
    val savedName: String = "",
    val confirmingDelete: Boolean = false,
    val isDeleting: Boolean = false,
    val deleteFailed: Boolean = false,
) {
    /** A save or delete is in progress; further ones are ignored. */
    val isBusy: Boolean get() = isSaving || isDeleting
}

enum class EditorStatus {
    LOADING,
    EDITING,
    NOT_FOUND,
    LOAD_FAILED,

    /** The person was stored locally; the screen should close. */
    SAVED,

    /** The person was deleted; the screen should close, along with the person's page. */
    DELETED,
}
