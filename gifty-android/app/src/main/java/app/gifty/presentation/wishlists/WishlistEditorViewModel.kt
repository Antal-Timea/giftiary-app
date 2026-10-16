package app.gifty.presentation.wishlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gifty.domain.model.Person
import app.gifty.domain.repository.PersonNotFoundException
import app.gifty.domain.repository.PersonRepository
import app.gifty.domain.repository.WishlistNotFoundException
import app.gifty.domain.repository.WishlistRepository
import app.gifty.domain.usecase.SaveWishlistResult
import app.gifty.domain.usecase.SaveWishlistUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Backs creating a wishlist, for the user or one of their people, and renaming one. Who a
 * wishlist is for is chosen only when it is created; renaming shows it but cannot change it.
 */
@HiltViewModel(assistedFactory = WishlistEditorViewModel.Factory::class)
class WishlistEditorViewModel @AssistedInject constructor(
    @Assisted private val target: WishlistEditorTarget,
    private val wishlistRepository: WishlistRepository,
    private val personRepository: PersonRepository,
    private val saveWishlist: SaveWishlistUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        when (target) {
            is WishlistEditorTarget.New ->
                WishlistEditorUiState(isNew = true, status = WishlistEditorStatus.EDITING, personId = target.personId)
            is WishlistEditorTarget.Existing ->
                WishlistEditorUiState(isNew = false, status = WishlistEditorStatus.LOADING)
        },
    )
    val uiState: StateFlow<WishlistEditorUiState> = _uiState.asStateFlow()

    init {
        when (target) {
            is WishlistEditorTarget.New -> observePeople()
            is WishlistEditorTarget.Existing -> load(target.wishlistId)
        }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name, showNameRequired = false) }
    }

    /** Only while creating; null means the user's own wishlist. */
    fun onPersonChange(person: Person?) {
        if (target !is WishlistEditorTarget.New) return
        _uiState.update { it.copy(personId = person?.id, writeError = null) }
    }

    fun onWriteErrorShown() {
        _uiState.update { it.copy(writeError = null) }
    }

    fun save() {
        val state = _uiState.value
        if (state.status != WishlistEditorStatus.EDITING || state.isSaving) return
        // Until the people are known, a preselected person may be shown as the user's own list.
        if (target is WishlistEditorTarget.New && !state.peopleLoaded) return

        _uiState.update { it.copy(isSaving = true, writeError = null) }
        viewModelScope.launch {
            try {
                val result = when (target) {
                    is WishlistEditorTarget.New -> saveWishlist.add(state.personId, state.name)
                    is WishlistEditorTarget.Existing -> saveWishlist.rename(target.wishlistId, state.name)
                }
                _uiState.update {
                    when (result) {
                        is SaveWishlistResult.Saved ->
                            it.copy(status = WishlistEditorStatus.SAVED, savedWishlistId = result.wishlistId)
                        SaveWishlistResult.BlankName -> it.copy(isSaving = false, showNameRequired = true)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: PersonNotFoundException) {
                _uiState.update { it.copy(isSaving = false, writeError = WishlistWriteError.PERSON_UNAVAILABLE) }
            } catch (_: WishlistNotFoundException) {
                _uiState.update { it.copy(isSaving = false, status = WishlistEditorStatus.NOT_FOUND) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isSaving = false, writeError = WishlistWriteError.FAILED) }
            }
        }
    }

    // A person handed in to start with, or chosen and deleted meanwhile, is dropped rather than
    // kept: a wishlist is only ever created for one of the account's active people.
    private fun observePeople() {
        viewModelScope.launch {
            personRepository.observePeople()
                .catch { _uiState.update { it.copy(personId = null, peopleLoaded = true) } }
                .collect { people ->
                    _uiState.update { state ->
                        state.copy(
                            activePeople = people,
                            peopleLoaded = true,
                            personId = state.personId?.takeIf { id -> people.any { it.id == id } },
                        )
                    }
                }
        }
    }

    private fun load(id: UUID) {
        viewModelScope.launch {
            try {
                val wishlist = wishlistRepository.getWishlist(id)
                if (wishlist == null) {
                    _uiState.update { it.copy(status = WishlistEditorStatus.NOT_FOUND) }
                    return@launch
                }
                val owner = when (val personId = wishlist.personId) {
                    null -> WishlistOwner.Me
                    else -> personRepository.getPerson(personId)?.let { WishlistOwner.Person(it.name) } ?: WishlistOwner.PersonRemoved
                }
                _uiState.update { it.copy(status = WishlistEditorStatus.EDITING, name = wishlist.name, owner = owner) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(status = WishlistEditorStatus.LOAD_FAILED) }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(target: WishlistEditorTarget): WishlistEditorViewModel
    }
}

sealed interface WishlistEditorTarget {
    /** [personId] preselects who the new wishlist is for; it is used only if it is an active person. */
    data class New(val personId: UUID? = null) : WishlistEditorTarget
    data class Existing(val wishlistId: UUID) : WishlistEditorTarget

    companion object {
        /** From a route argument: a malformed person id is ignored, leaving the user's own wishlist. */
        fun newFor(personId: String?): New = New(personId?.let { runCatching { UUID.fromString(it) }.getOrNull() })
    }
}

data class WishlistEditorUiState(
    val isNew: Boolean,
    val status: WishlistEditorStatus,
    val name: String = "",
    /** While creating: who the wishlist is for, null for the user's own. */
    val personId: UUID? = null,
    val activePeople: List<Person> = emptyList(),
    val peopleLoaded: Boolean = false,
    /** While renaming: who the wishlist is for, which cannot change. */
    val owner: WishlistOwner? = null,
    val showNameRequired: Boolean = false,
    val isSaving: Boolean = false,
    val writeError: WishlistWriteError? = null,
    /** Set once saved; a new wishlist opens next. */
    val savedWishlistId: UUID? = null,
) {
    /** Null stands for the user's own wishlist, offered first. */
    val personOptions: List<Person?> get() = listOf(null) + activePeople

    val selectedPerson: Person? get() = activePeople.find { it.id == personId }
}

enum class WishlistEditorStatus {
    LOADING,
    EDITING,
    NOT_FOUND,
    LOAD_FAILED,

    /** Stored locally; the screen should close, or open the new wishlist. */
    SAVED,
}

enum class WishlistWriteError {
    FAILED,
    PERSON_UNAVAILABLE,
}
