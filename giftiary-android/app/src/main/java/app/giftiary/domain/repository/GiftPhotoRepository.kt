package app.giftiary.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Gift, person and wish photos. A photo is stored on this device first and attached to a gift,
 * person or wish by saving it with its path ([GiftDetails.photoPath], [PersonRepository.addPerson],
 * [PersonRepository.setPersonPhoto], [WishDetails.photoPath]); it then uploads in the background
 * with that row.
 */
interface GiftPhotoRepository {

    /**
     * Stores the image the user picked ([source]: a content URI) as a new photo of the signed-in
     * account, shaped for [kind] and without metadata, and returns its path. Nothing refers to it
     * until a gift, gift idea, person or wish is saved with it.
     *
     * @throws GiftPhotoUnreadableException if the image cannot be read.
     */
    suspend fun importPhoto(source: String, kind: PhotoKind = PhotoKind.GIFT): String

    /**
     * A content URI the system camera app can write a new photo into. Pass it to [importCapture]
     * once the camera returned a photo, or to [discardCapture] if it did not.
     */
    fun newCaptureTarget(): String

    /**
     * Like [importPhoto] for a photo the camera wrote into [target]; the capture file is deleted
     * whether or not it could be read.
     *
     * @throws GiftPhotoUnreadableException if the image cannot be read.
     */
    suspend fun importCapture(target: String, kind: PhotoKind = PhotoKind.GIFT): String

    /** Deletes the capture file of a camera that returned no photo. */
    suspend fun discardCapture(target: String)

    /**
     * The photo's JPEG bytes: from this device, or downloaded once and kept. Null if it is not
     * available now, e.g. offline on a device that has not seen it yet.
     */
    suspend fun loadPhoto(path: String): ByteArray?

    /**
     * The photo of a wish on a list someone shared with the user: downloaded each time it is asked
     * for and never stored, as the shared list itself is read from the server. Null if it cannot be
     * read now, e.g. offline, or once the wish is no longer on a list the user is a member of.
     */
    suspend fun loadSharedPhoto(path: String): ByteArray?

    /** The photo's JPEG bytes only if they are on this device already; never downloads. */
    suspend fun loadLocalPhoto(path: String): ByteArray?

    /** Whether the photo is still on this device only. */
    fun observeUploadPending(path: String): Flow<Boolean>
}

/** What an imported photo is for, which decides its shape. */
enum class PhotoKind {
    /** The whole picture, as taken. */
    GIFT,

    /** A square avatar cropped from the centre. */
    PERSON,
}

class GiftPhotoUnreadableException : Exception("The image could not be read")

/** The photo is not one this account imported on this device; it cannot be attached. */
class GiftPhotoUnavailableException : Exception("The photo is not available on this device")
