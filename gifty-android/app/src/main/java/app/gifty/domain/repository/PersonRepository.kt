package app.gifty.domain.repository

import app.gifty.domain.model.Person
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

    suspend fun addPerson(name: String, relationship: String?): Person

    /** @throws PersonNotFoundException if the person does not exist or has been deleted. */
    suspend fun updatePerson(id: UUID, name: String, relationship: String?)

    /** Also deletes the person's occasions. Deleting a person that is already gone is a no-op. */
    suspend fun deletePerson(id: UUID)
}

class PersonNotFoundException(id: UUID) : Exception("Person $id does not exist")
