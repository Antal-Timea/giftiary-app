package app.giftiary.presentation.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.Person
import app.giftiary.domain.model.Wishlist
import app.giftiary.domain.repository.GiftEntry
import app.giftiary.domain.repository.GiftIdeaEntry
import app.giftiary.domain.repository.GiftIdeaRepository
import app.giftiary.domain.repository.GiftRepository
import app.giftiary.domain.repository.OccasionRepository
import app.giftiary.domain.repository.PersonRepository
import app.giftiary.domain.repository.WishlistRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

/**
 * Shows a person together with their occasions, the gift ideas for them, the wishlists kept for
 * them and the gifts they gave or received. All come straight from Room, so edits made in the
 * editors appear as soon as they are stored.
 */
@HiltViewModel(assistedFactory = PersonDetailViewModel.Factory::class)
class PersonDetailViewModel @AssistedInject constructor(
    @Assisted personId: UUID,
    personRepository: PersonRepository,
    occasionRepository: OccasionRepository,
    giftRepository: GiftRepository,
    giftIdeaRepository: GiftIdeaRepository,
    wishlistRepository: WishlistRepository,
) : ViewModel() {

    private val wishlists = combine(wishlistRepository.observeWishlists(), wishlistRepository.observeWishCounts()) { lists, counts ->
        lists.filter { it.personId == personId }.map { PersonWishlist(it, wishCount = counts[it.id] ?: 0) }
    }

    val uiState: StateFlow<PersonDetailUiState> =
        combine(
            personRepository.observePerson(personId),
            occasionRepository.observeOccasionsForPerson(personId),
            giftIdeaRepository.observeGiftIdeasForPerson(personId),
            giftRepository.observeGiftsForPerson(personId),
            wishlists,
        ) { person, occasions, ideas, gifts, wishlists ->
            if (person == null) PersonDetailUiState.NotFound else PersonDetailUiState.Loaded(person, occasions, ideas, gifts, wishlists)
        }
            .catch { emit(PersonDetailUiState.LoadFailed) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = PersonDetailUiState.Loading,
            )

    @AssistedFactory
    interface Factory {
        fun create(personId: UUID): PersonDetailViewModel
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

sealed interface PersonDetailUiState {
    /** Only until Room's first emission. */
    data object Loading : PersonDetailUiState
    data object NotFound : PersonDetailUiState
    data object LoadFailed : PersonDetailUiState
    data class Loaded(
        val person: Person,
        val occasions: List<Occasion>,
        val giftIdeas: List<GiftIdeaEntry>,
        val gifts: List<GiftEntry>,
        /** The person's active wishlists, by name. */
        val wishlists: List<PersonWishlist>,
    ) : PersonDetailUiState
}

data class PersonWishlist(val wishlist: Wishlist, val wishCount: Int)
