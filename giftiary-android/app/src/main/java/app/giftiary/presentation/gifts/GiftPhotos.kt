package app.giftiary.presentation.gifts

import app.giftiary.domain.model.Person
import app.giftiary.domain.repository.GiftEntry
import app.giftiary.domain.repository.GiftPhotoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest

/**
 * The photos of the [gifts] shown, by path, filled in one by one as they are read: from this
 * device, or downloaded the first time. A photo read once is kept while the flow is collected; one
 * that cannot be read now is left out, so its gift keeps the branded fallback until the list changes
 * and it is tried again.
 */
fun GiftPhotoRepository.photosOf(gifts: Flow<List<GiftEntry>>): Flow<Map<String, LoadedPhoto>> =
    photosAt(gifts.map { entries -> entries.mapNotNull { it.gift.photoPath } }, this::loadPhoto)

/**
 * The photos of the [people] shown that are on this device already, by path, for the small avatars
 * of lists and rows: a person whose photo is not here keeps the monogram, and no row ever starts a
 * download. The person's own page downloads it.
 */
fun GiftPhotoRepository.localPhotosOf(people: Flow<List<Person>>): Flow<Map<String, LoadedPhoto>> =
    photosAt(people.map { list -> list.mapNotNull { it.photoPath } }, this::loadLocalPhoto)

@OptIn(ExperimentalCoroutinesApi::class)
private fun photosAt(paths: Flow<List<String>>, load: suspend (String) -> ByteArray?): Flow<Map<String, LoadedPhoto>> {
    val read = mutableMapOf<String, LoadedPhoto>()
    return paths
        .distinctUntilChanged()
        .transformLatest { paths ->
            fun shown() = paths.mapNotNull { path -> read[path]?.let { path to it } }.toMap()
            emit(shown())
            for (path in paths.filterNot(read::containsKey)) {
                val photo = try {
                    load(path)?.let(::LoadedPhoto)
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
