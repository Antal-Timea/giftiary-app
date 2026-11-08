package app.giftiary.domain.repository

import app.giftiary.domain.model.Person
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * People are stored on the device first; every write succeeds locally regardless of network
 * availability. Inputs are expected to be validated already.
 */
interface PersonRepository {

    /** Active (not deleted) people, re-emitted whenever they change. */
    fun observePeople(): Flow<List<Person>>

    /** Emits null while the person does not exist or after it has been deleted. */
    fun observePerson(id: UUID): Flow<Person?>

    /** Returns null if the person does not exist or has been deleted. */
    suspend fun getPerson(id: UUID): Person?

    /**
     * [photoPath] names a photo imported for this account on this device; it is uploaded before
     * the person is synced.
     *
     * @throws GiftPhotoUnavailableException if the photo is not available on this device.
     */
    suspend fun addPerson(name: String, relationship: String?, photoPath: String? = null): Person

    /** @throws PersonNotFoundException if the person does not exist or has been deleted. */
    suspend fun updatePerson(id: UUID, name: String, relationship: String?)

    /**
     * Replaces or removes (null) the person's photo. The old photo's object is deleted only once
     * the person no longer naming it has been synced.
     *
     * @throws PersonNotFoundException if the person does not exist or has been deleted.
     * @throws GiftPhotoUnavailableException if the photo is not available on this device.
     */
    suspend fun setPersonPhoto(id: UUID, photoPath: String?)

    /** Also deletes the person's occasions and photo. Deleting a person that is already gone is a no-op. */
    suspend fun deletePerson(id: UUID)
}

class PersonNotFoundException(id: UUID) : Exception("Person $id does not exist")
