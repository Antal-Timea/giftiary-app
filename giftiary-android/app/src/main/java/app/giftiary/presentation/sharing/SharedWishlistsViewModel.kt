package app.giftiary.presentation.sharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.model.SharedWishlistSummary
import app.giftiary.domain.repository.WishlistSharingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** The wishlists other people shared with the user, newest server state on every visit. */
@HiltViewModel
class SharedWishlistsViewModel @Inject constructor(
    sharing: WishlistSharingRepository,
) : ViewModel() {

    private val loader = OnlineLoader { sharing.sharedWishlists() }
    val state: StateFlow<OnlineState<List<SharedWishlistSummary>>> = loader.state.asStateFlow()

    /** Called whenever the screen is shown, so a list shared or withdrawn meanwhile is seen. */
    fun refresh() = loader.load(viewModelScope)

    fun onRefreshFailureShown() {
        loader.state.update { it.copy(refreshFailure = null) }
    }
}
