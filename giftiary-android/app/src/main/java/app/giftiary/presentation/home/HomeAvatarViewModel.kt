package app.giftiary.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.repository.ProfileException
import app.giftiary.domain.repository.ProfileRepository
import app.giftiary.presentation.gifts.LoadedPhoto
import app.giftiary.presentation.profile.SavedAvatar
import app.giftiary.presentation.profile.refreshAvatar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The user's own avatar on the profile entry, and the name its monogram is drawn from. Online only:
 * offline the monogram stays, drawn from the email until the name has been read.
 */
@HiltViewModel
class HomeAvatarViewModel @Inject constructor(
    private val profiles: ProfileRepository,
) : ViewModel() {

    private val saved = MutableStateFlow<SavedAvatar?>(null)
    private val name = MutableStateFlow<String?>(null)

    val avatar: StateFlow<LoadedPhoto?> = saved.map { it?.photo }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val displayName: StateFlow<String?> = name.asStateFlow()

    private var loading: Job? = null

    /** On every visit, so a name or avatar just changed in Edit profile shows on return. */
    fun onResume() {
        loading?.cancel()
        loading = viewModelScope.launch {
            try {
                val profile = profiles.profile()
                name.value = profile.displayName
                saved.value = profiles.refreshAvatar(profile.avatarPath, saved.value)
            } catch (_: ProfileException) {
                // Keeps what was shown before.
            }
        }
    }
}
