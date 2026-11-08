package app.giftiary.presentation.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.repository.GiftPhotoRepository
import app.giftiary.domain.repository.GiftPhotoUnavailableException
import app.giftiary.domain.repository.PersonNotFoundException
import app.giftiary.domain.repository.PhotoKind
import app.giftiary.domain.repository.PersonRepository
import app.giftiary.domain.usecase.SavePersonResult
import app.giftiary.domain.usecase.SavePersonUseCase
import app.giftiary.presentation.gifts.LoadedPhoto
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
 * Backs both adding ([personId] is null) and editing a person. A chosen photo is stored on this
 * device at once but belongs to the person only once saved: with a new person in the same write,
 * or for an existing one through [PersonRepository.setPersonPhoto] after the details.
 */
@HiltViewModel(assistedFactory = PersonEditorViewModel.Factory::class)
class PersonEditorViewModel @AssistedInject constructor(
    @Assisted private val personId: UUID?,
    private val repository: PersonRepository,
    private val savePerson: SavePersonUseCase,
    private val photos: GiftPhotoRepository,
) : ViewModel() {

    // The stored photo's image, put back if a newly chosen one cannot be saved.
    private var savedPhoto: LoadedPhoto? = null

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

    /** [source]: the content URI the photo picker returned. */
    fun onPhotoPicked(source: String) {
        if (_uiState.value.photoBusy) return
        importPhoto { photos.importPhoto(source, PhotoKind.PERSON) }
    }

    /** Where the camera app is to write a new photo; null if there is nowhere, which is reported. */
    fun newCaptureTarget(): String? = try {
        photos.newCaptureTarget()
    } catch (e: Exception) {
        _uiState.update { it.copy(photoError = PersonPhotoError.CAMERA_UNAVAILABLE) }
        null
    }

    /** The camera app wrote a photo into [target] (from [newCaptureTarget]). */
    fun onPhotoCaptured(target: String) {
        if (_uiState.value.photoBusy) {
            onCaptureCancelled(target)
            return
        }
        importPhoto { photos.importCapture(target, PhotoKind.PERSON) }
    }

    /** The camera app returned without a photo. */
    fun onCaptureCancelled(target: String) {
        viewModelScope.launch { photos.discardCapture(target) }
    }

    /** No camera app could be opened for [target]. */
    fun onCameraUnavailable(target: String) {
        onCaptureCancelled(target)
        _uiState.update { it.copy(photoError = PersonPhotoError.CAMERA_UNAVAILABLE) }
    }

    fun onPhotoRemoved() {
        _uiState.update { if (it.photoBusy) it else it.copy(photoPath = null, photo = null, photoError = null) }
    }

    fun onPhotoErrorShown() {
        _uiState.update { it.copy(photoError = null) }
    }

    private fun importPhoto(import: suspend () -> String) {
        _uiState.update { it.copy(photoBusy = true, photoError = null) }
        viewModelScope.launch {
            try {
                val path = import()
                val bytes = photos.loadPhoto(path)
                _uiState.update { it.copy(photoBusy = false, photoPath = path, photo = bytes?.let(::LoadedPhoto)) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(photoBusy = false, photoError = PersonPhotoError.UNREADABLE) }
            }
        }
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
        // A photo still being prepared would otherwise be left out of the save without a word.
        if (state.status != EditorStatus.EDITING || state.isBusy || state.photoBusy) return

        _uiState.update { it.copy(isSaving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                val result = if (personId == null) {
                    savePerson.add(state.name, state.relationship, state.photoPath)
                } else {
                    savePerson.update(personId, state.name, state.relationship).also { result ->
                        // Its own write, after the details: if it fails the editor stays open, and
                        // saving again only has the photo left to store.
                        if (result is SavePersonResult.Saved && state.photoPath != state.savedPhotoPath) {
                            repository.setPersonPhoto(personId, state.photoPath)
                            _uiState.update { it.copy(savedPhotoPath = state.photoPath) }
                        }
                    }
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
            } catch (_: GiftPhotoUnavailableException) {
                // Nothing of the photo was stored; the person keeps the photo it had.
                _uiState.update {
                    it.copy(isSaving = false, photoPath = it.savedPhotoPath, photo = savedPhoto, photoError = PersonPhotoError.UNAVAILABLE_ON_SAVE)
                }
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
                            photoPath = person.photoPath,
                            savedPhotoPath = person.photoPath,
                        )
                    }
                }
                person?.photoPath?.let { path ->
                    savedPhoto = photos.loadPhoto(path)?.let(::LoadedPhoto)
                    // Only if the user has not picked or removed a photo meanwhile.
                    _uiState.update { if (it.photoPath == path) it.copy(photo = savedPhoto) else it }
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
    /** The photo the person is to have once saved; null for none. */
    val photoPath: String? = null,
    /** The photo the person has as stored. */
    val savedPhotoPath: String? = null,
    /** [photoPath]'s image; null while it is read, or when it is not available on this device. */
    val photo: LoadedPhoto? = null,
    /** A picked or taken photo is being prepared. */
    val photoBusy: Boolean = false,
    val photoError: PersonPhotoError? = null,
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

enum class PersonPhotoError {
    UNREADABLE,
    CAMERA_UNAVAILABLE,

    /** The chosen photo was no longer on this device when saving; the person kept their photo. */
    UNAVAILABLE_ON_SAVE,
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
