package app.giftiary.presentation.wishlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.model.Wish
import app.giftiary.domain.repository.WishlistRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** A wish, live, with the name of the wishlist it is on. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = WishDetailViewModel.Factory::class)
class WishDetailViewModel @AssistedInject constructor(
    @Assisted private val wishId: UUID,
    private val repository: WishlistRepository,
) : ViewModel() {

    private val actions = MutableStateFlow(WishDetailActions())

    private val wish = repository.observeWish(wishId)
        .flatMapLatest { wish ->
            if (wish == null) {
                flowOf(WishDetailContent.NotFound)
            } else {
                repository.observeWishlist(wish.wishlistId).map { wishlist -> WishDetailContent.Loaded(wish, wishlist?.name) }
            }
        }
        .catch { emit(WishDetailContent.LoadFailed) }

    val uiState: StateFlow<WishDetailUiState> =
        combine(wish, actions) { content, actions ->
            // Once deleted, the wish is gone from Room too; keep reporting the deletion, not "not found".
            WishDetailUiState(content = if (actions.deleted) WishDetailContent.Deleted else content, actions = actions)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = WishDetailUiState(),
        )

    fun onDeleteRequested() {
        actions.update { it.copy(confirmingDelete = true) }
    }

    fun onDeleteDismissed() {
        actions.update { it.copy(confirmingDelete = false) }
    }

    fun onDeleteConfirmed() {
        if (actions.value.isBusy) return
        actions.update { it.copy(confirmingDelete = false, isBusy = true, writeFailed = false) }
        viewModelScope.launch {
            try {
                repository.deleteWish(wishId)
                actions.update { it.copy(isBusy = false, deleted = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                actions.update { it.copy(isBusy = false, writeFailed = true) }
            }
        }
    }

    fun onWriteErrorShown() {
        actions.update { it.copy(writeFailed = false) }
    }

    @AssistedFactory
    interface Factory {
        fun create(wishId: UUID): WishDetailViewModel
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

data class WishDetailUiState(
    val content: WishDetailContent = WishDetailContent.Loading,
    val actions: WishDetailActions = WishDetailActions(),
)

data class WishDetailActions(
    val confirmingDelete: Boolean = false,
    /** The deletion is being stored. */
    val isBusy: Boolean = false,
    val writeFailed: Boolean = false,
    val deleted: Boolean = false,
)

sealed interface WishDetailContent {
    /** Only until Room's first emission. */
    data object Loading : WishDetailContent

    /** Missing, deleted elsewhere, or not the account's. */
    data object NotFound : WishDetailContent
    data object LoadFailed : WishDetailContent

    /** Deleted from this screen; it should close. */
    data object Deleted : WishDetailContent

    /** [wishlistName] is null for the moment the wishlist is deleted before its wishes are. */
    data class Loaded(val wish: Wish, val wishlistName: String?) : WishDetailContent
}
