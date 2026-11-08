package app.giftiary.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.repository.AccountSession
import app.giftiary.domain.repository.GiftPhotoRepository
import app.giftiary.domain.repository.GiftPhotoUnavailableException
import app.giftiary.domain.repository.OwnerSessionState
import app.giftiary.domain.repository.PhotoKind
import app.giftiary.domain.repository.ProfileException
import app.giftiary.domain.repository.ProfileFailure
import app.giftiary.domain.repository.ProfileRepository
import app.giftiary.domain.usecase.DisplayNameInput
import app.giftiary.domain.usecase.normalizeDisplayName
import app.giftiary.presentation.gifts.LoadedPhoto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The name others see and the avatar only the user sees. Online, read on every visit; an avatar
 * change is saved straight away, apart from the name.
 */
@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val photos: GiftPhotoRepository,
    accountSession: AccountSession,
) : ViewModel() {

    private val _state = MutableStateFlow(
        EditProfileUiState(email = (accountSession.state.value as? OwnerSessionState.Ready)?.user?.email),
    )
    val state: StateFlow<EditProfileUiState> = _state.asStateFlow()

    private var loading: Job? = null

    init {
        load()
    }

    fun load() {
        loading?.cancel()
        _state.update { it.copy(profile = ProfileContent.Loading) }
        loading = viewModelScope.launch {
            try {
                val profile = profiles.profile()
                val name = profile.displayName
                _state.update {
                    it.copy(
                        profile = ProfileContent.Loaded(saved = name),
                        nameInput = name.orEmpty(),
                        inputInvalid = false,
                        avatarPath = profile.avatarPath,
                        avatar = null,
                    )
                }
                // Without it the monogram stands in; the avatar is still there to change or remove.
                val path = profile.avatarPath ?: return@launch
                val bytes = try {
                    profiles.avatar(path)
                } catch (_: ProfileException) {
                    return@launch
                }
                _state.update { if (it.avatarPath == path) it.copy(avatar = LoadedPhoto(bytes)) else it }
            } catch (e: ProfileException) {
                _state.update { it.copy(profile = ProfileContent.Failed(e.failure)) }
            }
        }
    }

    fun onAvatarPicked(source: String) {
        changeAvatar { photos.importPhoto(source, PhotoKind.PERSON) }
    }

    /** Where the camera app is to write a new photo; null if there is nowhere, which is reported. */
    fun newCaptureTarget(): String? = try {
        photos.newCaptureTarget()
    } catch (e: Exception) {
        _state.update { it.copy(avatarFailure = AvatarFailure.CAMERA_UNAVAILABLE) }
        null
    }

    fun onAvatarCaptured(target: String) {
        if (!canChangeAvatar()) {
            onCaptureCancelled(target)
            return
        }
        changeAvatar { photos.importCapture(target, PhotoKind.PERSON) }
    }

    fun onCaptureCancelled(target: String) {
        viewModelScope.launch { photos.discardCapture(target) }
    }

    fun onCameraUnavailable(target: String) {
        onCaptureCancelled(target)
        _state.update { it.copy(avatarFailure = AvatarFailure.CAMERA_UNAVAILABLE) }
    }

    fun onAvatarRemoved() {
        if (!canChangeAvatar() || _state.value.avatarPath == null) return
        _state.update { it.copy(avatarBusy = true, avatarFailure = null, retry = null) }
        viewModelScope.launch { apply(AvatarChange.Remove) }
    }

    /** Tries the change that failed again, with the same picture. */
    fun onAvatarRetry() {
        val change = _state.value.retry ?: return
        if (!canChangeAvatar()) return
        _state.update { it.copy(avatarBusy = true, avatarFailure = null) }
        viewModelScope.launch { apply(change) }
    }

    private fun canChangeAvatar(): Boolean = _state.value.profile is ProfileContent.Loaded && !_state.value.avatarBusy

    private fun changeAvatar(import: suspend () -> String) {
        if (!canChangeAvatar()) return
        _state.update { it.copy(avatarBusy = true, avatarFailure = null, retry = null) }
        viewModelScope.launch {
            val path = try {
                import()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(avatarBusy = false, avatarFailure = AvatarFailure.UNREADABLE) }
                return@launch
            }
            apply(AvatarChange.Set(path))
        }
    }

    // The avatar shown changes only once the server has it: a failure leaves the saved one in place.
    private suspend fun apply(change: AvatarChange) {
        try {
            when (change) {
                is AvatarChange.Set -> {
                    // Read first: once saved, the picture leaves this device.
                    val bytes = photos.loadLocalPhoto(change.path)
                    val path = profiles.setAvatar(change.path)
                    _state.update {
                        it.copy(avatarPath = path, avatar = bytes?.let(::LoadedPhoto), avatarMessage = AvatarMessage.SAVED)
                    }
                }
                AvatarChange.Remove -> {
                    profiles.removeAvatar()
                    _state.update { it.copy(avatarPath = null, avatar = null, avatarMessage = AvatarMessage.REMOVED) }
                }
            }
            _state.update { it.copy(avatarBusy = false, retry = null) }
        } catch (e: ProfileException) {
            val failure = if (e.failure == ProfileFailure.NETWORK) AvatarFailure.NETWORK else AvatarFailure.SERVER
            _state.update { it.copy(avatarBusy = false, avatarFailure = failure, retry = change) }
        } catch (_: GiftPhotoUnavailableException) {
            _state.update { it.copy(avatarBusy = false, avatarFailure = AvatarFailure.NOT_ON_DEVICE, retry = null) }
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
        _state.update { it.copy(saved = false, saveFailure = null, avatarMessage = null) }
    }
}

data class EditProfileUiState(
    val profile: ProfileContent = ProfileContent.Loading,
    val nameInput: String = "",
    /** The typed name has line breaks or other control characters. */
    val inputInvalid: Boolean = false,
    val isSaving: Boolean = false,
    /** Shown once each. */
    val saved: Boolean = false,
    val saveFailure: ProfileFailure? = null,
    /** The account's email, for the monogram while there is no avatar. */
    val email: String? = null,
    /** As the server has it. */
    val avatarPath: String? = null,
    /** The saved avatar, null while there is none or it could not be downloaded. */
    val avatar: LoadedPhoto? = null,
    val avatarBusy: Boolean = false,
    /** Stays until the next avatar action; with [retry] the same change can be tried again. */
    val avatarFailure: AvatarFailure? = null,
    val retry: AvatarChange? = null,
    /** Shown once. */
    val avatarMessage: AvatarMessage? = null,
)

sealed interface AvatarChange {
    /** [path] is the picture still on this device, kept for a retry until the server has it. */
    data class Set(val path: String) : AvatarChange
    data object Remove : AvatarChange
}

enum class AvatarFailure { UNREADABLE, CAMERA_UNAVAILABLE, NOT_ON_DEVICE, NETWORK, SERVER }

enum class AvatarMessage { SAVED, REMOVED }

sealed interface ProfileContent {
    data object Loading : ProfileContent

    /** [saved] is the name as the server has it, null when there is none. */
    data class Loaded(val saved: String?) : ProfileContent
    data class Failed(val failure: ProfileFailure) : ProfileContent
}
