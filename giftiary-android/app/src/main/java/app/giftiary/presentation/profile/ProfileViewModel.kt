package app.giftiary.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.repository.AccountSession
import app.giftiary.domain.repository.GiftRepository
import app.giftiary.domain.repository.OwnerSessionState
import app.giftiary.domain.repository.PersonRepository
import app.giftiary.domain.repository.ProfileException
import app.giftiary.domain.repository.ProfileRepository
import app.giftiary.domain.repository.WishlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The user's own profile: the account's email, the name others see when the server has one, and
 * how much this device keeps. Nothing is filled in that the account does not have.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    accountSession: AccountSession,
    people: PersonRepository,
    gifts: GiftRepository,
    wishlists: WishlistRepository,
) : ViewModel() {

    private val email: String? = (accountSession.state.value as? OwnerSessionState.Ready)?.user?.email

    // The name lives only on the server; without an answer the profile shows the email alone.
    private val displayName = MutableStateFlow<String?>(null)

    private val stats = combine(
        people.observePeople().map { it.size },
        gifts.observeGifts().map { it.size },
        wishlists.observeWishlists().map { it.size },
    ) { peopleCount, giftCount, listCount -> ProfileStats(peopleCount, giftCount, listCount) }

    val state: StateFlow<ProfileUiState> = combine(
        displayName,
        stats.map<ProfileStats, ProfileStats?> { it }.catch { emit(null) },
    ) { name, counts -> ProfileUiState(email = email, displayName = name, stats = counts) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ProfileUiState(email = email),
        )

    private var loading: Job? = null

    /** On every visit, so a name just changed in Edit profile shows on return. */
    fun onResume() {
        loading?.cancel()
        loading = viewModelScope.launch {
            try {
                displayName.value = profiles.displayName()
            } catch (_: ProfileException) {
                // Keeps what was read before; Edit profile is where a failure is explained.
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

data class ProfileUiState(
    val email: String? = null,
    val displayName: String? = null,
    /** Null until counted. */
    val stats: ProfileStats? = null,
)

/** What this device keeps for the account: active people, gifts and wishlists. */
data class ProfileStats(val people: Int, val gifts: Int, val wishlists: Int)
