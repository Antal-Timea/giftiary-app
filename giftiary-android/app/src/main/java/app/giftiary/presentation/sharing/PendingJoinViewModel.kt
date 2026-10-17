package app.giftiary.presentation.sharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Whether a join link waits: signed out it is announced, signed in it opens the join screen. */
@HiltViewModel
class PendingJoinViewModel @Inject constructor(
    pendingJoin: PendingWishlistJoin,
) : ViewModel() {

    val isPending: StateFlow<Boolean> =
        pendingJoin.isPending.stateIn(viewModelScope, SharingStarted.Eagerly, false)
}
