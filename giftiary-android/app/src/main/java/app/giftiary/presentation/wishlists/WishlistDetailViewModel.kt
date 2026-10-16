package app.giftiary.presentation.wishlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.model.Person
import app.giftiary.domain.model.Wish
import app.giftiary.domain.model.Wishlist
import app.giftiary.domain.repository.PersonRepository
import app.giftiary.domain.repository.WishlistRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * A wishlist with its wishes, live: a rename, a new wish or a deletion elsewhere, including one
 * pulled from another device, shows at once.
 */
@HiltViewModel(assistedFactory = WishlistDetailViewModel.Factory::class)
class WishlistDetailViewModel @AssistedInject constructor(
    @Assisted private val wishlistId: UUID,
    private val repository: WishlistRepository,
    personRepository: PersonRepository,
) : ViewModel() {

    private val actions = MutableStateFlow(WishlistDetailActions())

    private val wishlist = combine(
        repository.observeWishlist(wishlistId),
        repository.observeWishes(wishlistId),
        personRepository.observePeople(),
    ) { wishlist, wishes, people ->
        if (wishlist == null) WishlistDetailContent.NotFound else loaded(wishlist, wishes, people)
    }.catch { emit(WishlistDetailContent.LoadFailed) }

    val uiState: StateFlow<WishlistDetailUiState> =
        combine(wishlist, actions) { content, actions ->
            // Once deleted, the wishlist is gone from Room too; keep reporting the deletion, not "not found".
            WishlistDetailUiState(
                content = if (actions.deleted) WishlistDetailContent.Deleted else content,
                actions = actions,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = WishlistDetailUiState(),
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
                repository.deleteWishlist(wishlistId)
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
        fun create(wishlistId: UUID): WishlistDetailViewModel
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        fun loaded(wishlist: Wishlist, wishes: List<Wish>, people: List<Person>): WishlistDetailContent.Loaded {
            val person = wishlist.personId?.let { id -> people.find { it.id == id } }
            val owner = when {
                wishlist.personId == null -> WishlistOwner.Me
                person != null -> WishlistOwner.Person(person.name)
                else -> WishlistOwner.PersonRemoved
            }
            return WishlistDetailContent.Loaded(wishlist, owner, openablePersonId = person?.id, wishes = wishes)
        }
    }
}

data class WishlistDetailUiState(
    val content: WishlistDetailContent = WishlistDetailContent.Loading,
    val actions: WishlistDetailActions = WishlistDetailActions(),
)

data class WishlistDetailActions(
    val confirmingDelete: Boolean = false,
    /** The deletion is being stored. */
    val isBusy: Boolean = false,
    val writeFailed: Boolean = false,
    val deleted: Boolean = false,
)

sealed interface WishlistDetailContent {
    /** Only until Room's first emission. */
    data object Loading : WishlistDetailContent

    /** Missing, deleted elsewhere, or not the account's. */
    data object NotFound : WishlistDetailContent
    data object LoadFailed : WishlistDetailContent

    /** Deleted from this screen; it should close. */
    data object Deleted : WishlistDetailContent

    /**
     * [openablePersonId] is the person the wishlist is for while they are an active person; a
     * deleted person has nothing to open. [wishes] are oldest first.
     */
    data class Loaded(
        val wishlist: Wishlist,
        val owner: WishlistOwner,
        val openablePersonId: UUID?,
        val wishes: List<Wish>,
    ) : WishlistDetailContent
}
