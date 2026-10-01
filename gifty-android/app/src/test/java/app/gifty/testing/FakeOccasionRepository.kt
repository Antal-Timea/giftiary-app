package app.gifty.testing

import app.gifty.domain.model.Occasion
import app.gifty.domain.model.OccasionType
import app.gifty.domain.repository.OccasionDetails
import app.gifty.domain.repository.OccasionNotFoundException
import app.gifty.domain.repository.OccasionRepository
import app.gifty.domain.repository.PersonNotFoundException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

    val currentOccasions: List<Occasion> get() = occasions.value

    fun givenOccasions(vararg occasion: Occasion) {
        occasions.value = occasion.toList()
        knownPersonIds += occasion.mapNotNull { it.personId }
    }

    override fun observeOccasionsForPerson(personId: UUID): Flow<List<Occasion>> =
        occasions.map { list -> list.filter { it.personId == personId }.sortedWith(compareBy({ it.month }, { it.day })) }

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
