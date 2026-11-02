package app.giftiary.presentation.wishlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.model.Person
import app.giftiary.domain.model.Wishlist
import app.giftiary.domain.repository.PersonRepository
import app.giftiary.domain.repository.WishlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.UUID
import javax.inject.Inject

/**
 * Every wishlist of the account: the user's own first, then those kept for their people. A
 * wishlist whose person was deleted stays here, so it can always be found and deleted.
 */
@HiltViewModel
class WishlistsViewModel @Inject constructor(
    wishlistRepository: WishlistRepository,
    personRepository: PersonRepository,
) : ViewModel() {

    val content: StateFlow<WishlistsContent> =
        combine(
            wishlistRepository.observeWishlists(),
            wishlistRepository.observeWishCounts(),
            personRepository.observePeople(),
        ) { wishlists, wishCounts, people ->
            wishlistsContent(wishlists, wishCounts, people)
        }
            .catch { emit(WishlistsContent.LoadFailed) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = WishlistsContent.Loading,
            )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        fun wishlistsContent(wishlists: List<Wishlist>, wishCounts: Map<UUID, Int>, people: List<Person>): WishlistsContent {
            val names = people.associate { it.id to it.name }
            val (own, forPeople) = wishlists.partition { it.personId == null }
            return WishlistsContent.Loaded(
                own = own,
                forPeople = forPeople.map { WishlistOfPerson(it, it.personId?.let(names::get)) },
                wishCounts = wishCounts,
            )
        }
    }
}

sealed interface WishlistsContent {
    /** Only until Room's first emission. */
    data object Loading : WishlistsContent

    data object LoadFailed : WishlistsContent

    /** Both groups keep the repository's order, by name. A wishlist without wishes has no [wishCounts] entry. */
    data class Loaded(
        val own: List<Wishlist>,
        val forPeople: List<WishlistOfPerson>,
        val wishCounts: Map<UUID, Int> = emptyMap(),
    ) : WishlistsContent {
        val isEmpty: Boolean get() = own.isEmpty() && forPeople.isEmpty()

        fun wishCount(wishlist: Wishlist): Int = wishCounts[wishlist.id] ?: 0
    }
}

/** [personName] is null when the person was deleted; the wishlist itself stays. */
data class WishlistOfPerson(val wishlist: Wishlist, val personName: String?)
