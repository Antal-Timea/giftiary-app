package app.giftiary.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.repository.GiftEntry
import app.giftiary.domain.repository.GiftPhotoRepository
import app.giftiary.domain.repository.GiftRepository
import app.giftiary.presentation.gifts.LoadedPhoto
import app.giftiary.presentation.gifts.photosOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Home's "Recent gifts": the latest gifts given or received, with their photos as they are read. */
@HiltViewModel
class RecentGiftsViewModel @Inject constructor(
    gifts: GiftRepository,
    photos: GiftPhotoRepository,
) : ViewModel() {

    private val recent = gifts.observeGifts().map { entries ->
        entries.sortedWith(compareByDescending<GiftEntry> { it.gift.givenAt }.thenByDescending { it.gift.createdAt }).take(MAX_GIFTS)
    }

    val content: StateFlow<RecentGiftsContent> = combine(recent, photos.photosOf(recent)) { entries, loaded ->
        RecentGiftsContent.Loaded(entries, loaded)
    }
        .catch<RecentGiftsContent> { emit(RecentGiftsContent.Unavailable) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = RecentGiftsContent.Loading,
        )

    companion object {
        /** The approved Home shows two side by side; the rest is a tap away in Gifts. */
        const val MAX_GIFTS = 2
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

sealed interface RecentGiftsContent {
    data object Loading : RecentGiftsContent

    data object Unavailable : RecentGiftsContent

    /** [photos] by photo path: only those read so far. */
    data class Loaded(val gifts: List<GiftEntry>, val photos: Map<String, LoadedPhoto> = emptyMap()) : RecentGiftsContent
}
