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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The user's own avatar on the profile entry. Online only: offline the monogram stays. */
@HiltViewModel
class HomeAvatarViewModel @Inject constructor(
    private val profiles: ProfileRepository,
) : ViewModel() {

    private val saved = MutableStateFlow<SavedAvatar?>(null)

    val avatar: StateFlow<LoadedPhoto?> = saved.map { it?.photo }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var loading: Job? = null

    /** On every visit, so an avatar just changed in Edit profile shows on return. */
    fun onResume() {
        loading?.cancel()
        loading = viewModelScope.launch {
            try {
                saved.value = profiles.refreshAvatar(profiles.profile().avatarPath, saved.value)
            } catch (_: ProfileException) {
                // Keeps what was shown before.
            }
        }
    }
}
