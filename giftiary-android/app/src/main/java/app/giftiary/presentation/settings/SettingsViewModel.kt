package app.giftiary.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.repository.AccountSession
import app.giftiary.domain.repository.OwnerSessionState
import app.giftiary.domain.repository.ProfileException
import app.giftiary.domain.repository.ProfileFailure
import app.giftiary.domain.repository.ProfileRepository
import app.giftiary.domain.usecase.DisplayNameInput
import app.giftiary.domain.usecase.normalizeDisplayName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The user's own settings: the name others see, and the account. Online, read on every visit. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    accountSession: AccountSession,
) : ViewModel() {

    /** Only for the user themself; never shown to anyone else and never used as a name. */
    val email: String? = (accountSession.state.value as? OwnerSessionState.Ready)?.user?.email

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private var loading: Job? = null

    init {
        load()
    }

    fun load() {
        loading?.cancel()
        _state.update { it.copy(profile = ProfileContent.Loading) }
        loading = viewModelScope.launch {
            try {
                val name = profiles.displayName()
                _state.update { it.copy(profile = ProfileContent.Loaded(saved = name), nameInput = name.orEmpty(), inputInvalid = false) }
            } catch (e: ProfileException) {
                _state.update { it.copy(profile = ProfileContent.Failed(e.failure)) }
            }
        }
    }

    fun onNameChange(input: String) {
        _state.update { it.copy(nameInput = input, inputInvalid = false) }
    }

    fun onSave() {
        val current = _state.value
        val loaded = current.profile as? ProfileContent.Loaded ?: return
        if (current.isSaving) return
        val name = when (val normalized = normalizeDisplayName(current.nameInput)) {
            DisplayNameInput.HasControlCharacters -> {
                _state.update { it.copy(inputInvalid = true) }
                return
            }
            is DisplayNameInput.Valid -> normalized.name
        }
        if (name == loaded.saved) {
            _state.update { it.copy(nameInput = name.orEmpty()) }
            return
        }
        _state.update { it.copy(isSaving = true, saveFailure = null) }
        viewModelScope.launch {
            try {
                profiles.setDisplayName(name)
                _state.update {
                    it.copy(isSaving = false, profile = ProfileContent.Loaded(saved = name), nameInput = name.orEmpty(), saved = true)
                }
            } catch (e: ProfileException) {
                _state.update { it.copy(isSaving = false, saveFailure = e.failure) }
            }
        }
    }

    fun onMessageShown() {
        _state.update { it.copy(saved = false, saveFailure = null) }
    }
}

data class SettingsUiState(
    val profile: ProfileContent = ProfileContent.Loading,
    val nameInput: String = "",
    /** The typed name has line breaks or other control characters. */
    val inputInvalid: Boolean = false,
    val isSaving: Boolean = false,
    /** Shown once each. */
    val saved: Boolean = false,
    val saveFailure: ProfileFailure? = null,
)

sealed interface ProfileContent {
    data object Loading : ProfileContent

    /** [saved] is the name as the server has it, null when there is none. */
    data class Loaded(val saved: String?) : ProfileContent
    data class Failed(val failure: ProfileFailure) : ProfileContent
}
