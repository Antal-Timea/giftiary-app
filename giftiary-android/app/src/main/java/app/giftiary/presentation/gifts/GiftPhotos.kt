package app.giftiary.presentation.gifts

import app.giftiary.domain.repository.GiftEntry
import app.giftiary.domain.repository.GiftPhotoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest

/**
 * The photos of a few [gifts] read all at once, by path, filled in one by one as they are read: from
 * this device, or downloaded the first time. Only for a short list whose cards are all on screen
 * together (Home's latest two); a scrolling list reads its photos row by row ([ShownPhotos]). A photo
 * read once is kept while the flow is collected; one that cannot be read now is left out, so its gift
 * keeps the branded fallback until the list changes and it is tried again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun GiftPhotoRepository.photosOf(gifts: Flow<List<GiftEntry>>): Flow<Map<String, LoadedPhoto>> {
    val read = mutableMapOf<String, LoadedPhoto>()
    return gifts.map { entries -> entries.mapNotNull { it.gift.photoPath } }
        .distinctUntilChanged()
        .transformLatest { paths ->
            fun shown() = paths.mapNotNull { path -> read[path]?.let { path to it } }.toMap()
            emit(shown())
            for (path in paths.filterNot(read::containsKey)) {
                val photo = try {
                    loadPhoto(path)?.let(::LoadedPhoto)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                if (photo != null) {
                    read[path] = photo
                    emit(shown())
                }
            }
        }
}
