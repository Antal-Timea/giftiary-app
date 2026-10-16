package app.giftiary.testing

import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.model.Person
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.domain.repository.OccasionEntry
import app.giftiary.domain.repository.OccasionNotFoundException
import app.giftiary.domain.repository.OccasionRepository
import app.giftiary.domain.repository.OccasionSnapshot
import app.giftiary.domain.repository.PersonNotFoundException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.util.UUID

class FakeOccasionRepository : OccasionRepository {

    private val occasions = MutableStateFlow<List<Occasion>>(emptyList())

    /** People that occasions may be added for; others behave like a deleted or foreign person. */
    val knownPersonIds = mutableSetOf<UUID>()

    /** When set, writes throw it, standing in for a failing local database. */
    var writeFailure: Exception? = null

    /** The active people [observeOccasions] reports; occasions of anyone else are left out like a deleted person's. */
    val people = MutableStateFlow<Map<UUID, Person>>(emptyMap())

    /** When set, [observeOccasions] and [getOccasionSnapshot] fail with it, standing in for a failing local read. */
    var readFailure: Exception? = null

    /** The account [getOccasionSnapshot] reads for; null stands for no account. */
    var snapshotOwner: UUID? = UUID.randomUUID()

    val currentOccasions: List<Occasion> get() = occasions.value

    fun givenOccasions(vararg occasion: Occasion) {
        occasions.value = occasion.toList()
        knownPersonIds += occasion.mapNotNull { it.personId }
    }

    override fun observeOccasionsForPerson(personId: UUID): Flow<List<Occasion>> =
        occasions.map { list -> list.filter { it.personId == personId }.sortedWith(compareBy({ it.month }, { it.day })) }

    override fun observeOccasions(): Flow<List<OccasionEntry>> = flow {
        readFailure?.let { throw it }
        emitAll(
            combine(occasions, people) { list, people ->
                list.mapNotNull { occasion ->
                    when (val personId = occasion.personId) {
                        null -> OccasionEntry(occasion, null)
                        else -> people[personId]?.let { OccasionEntry(occasion, it) }
                    }
                }
            },
        )
    }

    override suspend fun getOccasionSnapshot(): OccasionSnapshot? {
        readFailure?.let { throw it }
        val owner = snapshotOwner ?: return null
        return OccasionSnapshot(owner, observeOccasions().first())
    }

    override suspend fun getOccasion(id: UUID): Occasion? = occasions.value.find { it.id == id }

    override suspend fun addOccasion(personId: UUID?, details: OccasionDetails): Occasion {
        writeFailure?.let { throw it }
        if (personId != null && personId !in knownPersonIds) throw PersonNotFoundException(personId)
        val occasion = occasion(personId = personId, details = details)
        occasions.update { it + occasion }
        return occasion
    }

    override suspend fun updateOccasion(id: UUID, details: OccasionDetails) {
        writeFailure?.let { throw it }
        val existing = occasions.value.find { it.id == id } ?: throw OccasionNotFoundException(id)
        occasions.update { list -> list.map { if (it.id == id) occasion(existing.personId, details, id) else it } }
    }

    override suspend fun deleteOccasion(id: UUID) {
        writeFailure?.let { throw it }
        occasions.update { list -> list.filterNot { it.id == id } }
    }
}

fun occasion(
    personId: UUID?,
    details: OccasionDetails = OccasionDetails(OccasionType.BIRTHDAY, null, 12, 3, null, true),
    id: UUID = UUID.randomUUID(),
) = Occasion(
    id = id,
    personId = personId,
    type = details.type,
    title = details.title,
    day = details.day,
    month = details.month,
    year = details.year,
    repeatsYearly = details.repeatsYearly,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)
