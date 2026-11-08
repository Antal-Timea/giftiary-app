package app.giftiary.presentation.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.Person
import app.giftiary.domain.model.Wishlist
import app.giftiary.domain.repository.GiftEntry
import app.giftiary.domain.repository.GiftIdeaEntry
import app.giftiary.domain.repository.GiftIdeaRepository
import app.giftiary.domain.repository.GiftPhotoRepository
import app.giftiary.domain.repository.GiftRepository
import app.giftiary.domain.repository.OccasionRepository
import app.giftiary.domain.repository.PersonRepository
import app.giftiary.domain.repository.WishlistRepository
import app.giftiary.presentation.gifts.LoadedPhoto
import app.giftiary.presentation.gifts.photosOf
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Shows a person together with their occasions, the gift ideas for them, the wishlists kept for
 * them and the gifts they gave or received. All come straight from Room, so edits made in the
 * editors appear as soon as they are stored.
 */
@HiltViewModel(assistedFactory = PersonDetailViewModel.Factory::class)
class PersonDetailViewModel @AssistedInject constructor(
    @Assisted private val personId: UUID,
    private val personRepository: PersonRepository,
    occasionRepository: OccasionRepository,
    giftRepository: GiftRepository,
    giftIdeaRepository: GiftIdeaRepository,
    wishlistRepository: WishlistRepository,
    photos: GiftPhotoRepository,
) : ViewModel() {

    private val deletionState = MutableStateFlow(PersonDeletion())
    val deletion: StateFlow<PersonDeletion> = deletionState.asStateFlow()

    /** The photos of the person's gifts, by path, as they are read; the history shows fallbacks until then. */
    val giftPhotos: StateFlow<Map<String, LoadedPhoto>> = photos.photosOf(giftRepository.observeGiftsForPerson(personId))
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyMap())

    /**
     * The person's photo for the hero, downloaded the first time if need be; null while there is
     * none or it cannot be read now, so the hero keeps the monogram.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val personPhoto: StateFlow<LoadedPhoto?> = personRepository.observePerson(personId)
        .map { it?.photoPath }
        .distinctUntilChanged()
        .transformLatest { path ->
            emit(null)
            if (path != null) emit(photos.loadPhoto(path)?.let(::LoadedPhoto))
        }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

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

    fun onDeleteRequested() {
        deletionState.update { it.copy(confirming = true) }
    }

    fun onDeleteDismissed() {
        deletionState.update { it.copy(confirming = false) }
    }

    fun onDeleteConfirmed() {
        deletionState.update { it.copy(confirming = false) }
        viewModelScope.launch {
            try {
                personRepository.deletePerson(personId)
                deletionState.update { it.copy(deleted = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                deletionState.update { it.copy(failed = true) }
            }
        }
    }

    fun onDeleteErrorShown() {
        deletionState.update { it.copy(failed = false) }
    }

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

/** Deleting the person from their detail: asked first; [deleted] once it is stored, so the screen can close. */
data class PersonDeletion(val confirming: Boolean = false, val failed: Boolean = false, val deleted: Boolean = false)
