package app.giftiary.testing

import app.giftiary.domain.model.Person
import app.giftiary.domain.repository.PersonNotFoundException
import app.giftiary.domain.repository.PersonRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.util.UUID

class FakePersonRepository : PersonRepository {

    private val people = MutableStateFlow<List<Person>>(emptyList())

    /** When set, writes throw it, standing in for a failing local database. */
    var writeFailure: Exception? = null

    /** When set, [addPerson] suspends until the test completes it. */
    var addGate: CompletableDeferred<Unit>? = null

    /** When set, observing people fails with it. */
    var peopleFlowFailure: Exception? = null

    val currentPeople: List<Person> get() = people.value

    fun givenPeople(vararg person: Person) {
        people.value = person.toList()
    }

    override fun observePeople(): Flow<List<Person>> = people.map { list ->
        peopleFlowFailure?.let { throw it }
        list
    }

    override fun observePerson(id: UUID): Flow<Person?> = people.map { list -> list.find { it.id == id } }

    override suspend fun getPerson(id: UUID): Person? = people.value.find { it.id == id }

    override suspend fun addPerson(name: String, relationship: String?): Person {
        addGate?.await()
        writeFailure?.let { throw it }
        val person = person(name = name, relationship = relationship)
        people.update { it + person }
        return person
    }

    override suspend fun updatePerson(id: UUID, name: String, relationship: String?) {
        writeFailure?.let { throw it }
        if (people.value.none { it.id == id }) throw PersonNotFoundException(id)
        people.update { list ->
            list.map { if (it.id == id) it.copy(name = name, relationship = relationship) else it }
        }
    }

    override suspend fun deletePerson(id: UUID) {
        writeFailure?.let { throw it }
        people.update { list -> list.filterNot { it.id == id } }
    }
}

fun person(
    name: String,
    relationship: String? = null,
    id: UUID = UUID.randomUUID(),
) = Person(
    id = id,
    name = name,
    relationship = relationship,
    photoPath = null,
    linkedUserId = null,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)
