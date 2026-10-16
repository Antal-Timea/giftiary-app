package app.giftiary.testing

import app.giftiary.domain.model.Gift
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.model.Person
import app.giftiary.domain.repository.GiftDetails
import app.giftiary.domain.repository.GiftEntry
import app.giftiary.domain.repository.GiftNotFoundException
import app.giftiary.domain.repository.GiftRepository
import app.giftiary.domain.repository.PersonNotFoundException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class FakeGiftRepository : GiftRepository {

    private val entries = MutableStateFlow<List<GiftEntry>>(emptyList())

    /** People gifts may refer to, by id; others behave like a deleted or foreign person. */
    val people = mutableMapOf<UUID, Person>()

    /** When set, writes throw it, standing in for a failing local database. */
    var writeFailure: Exception? = null

    var lastSaved: GiftDetails? = null
        private set

    val currentGifts: List<GiftEntry> get() = entries.value

    fun givenGifts(vararg entry: GiftEntry) {
        entries.value = entry.toList()
    }

    override fun observeGifts(): Flow<List<GiftEntry>> = entries.map { it.sortedByDescending { e -> e.gift.givenAt } }

    override fun observeGiftsForPerson(personId: UUID): Flow<List<GiftEntry>> = observeGifts().map { list ->
        list.filter { it.gift.giverPersonId == personId || it.gift.recipientPersonId == personId }
    }

    override fun observeGift(id: UUID): Flow<GiftEntry?> = entries.map { list -> list.find { it.gift.id == id } }

    override suspend fun getGift(id: UUID): GiftEntry? = entries.value.find { it.gift.id == id }

    override suspend fun addGift(details: GiftDetails): Gift {
        writeFailure?.let { throw it }
        requireKnown(details.giverPersonId)
        requireKnown(details.recipientPersonId)
        lastSaved = details
        val entry = entry(details, UUID.randomUUID())
        entries.update { it + entry }
        return entry.gift
    }

    override suspend fun updateGift(id: UUID, details: GiftDetails) {
        writeFailure?.let { throw it }
        val existing = entries.value.find { it.gift.id == id } ?: throw GiftNotFoundException(id)
        if (details.giverPersonId != existing.gift.giverPersonId) requireKnown(details.giverPersonId)
        if (details.recipientPersonId != existing.gift.recipientPersonId) requireKnown(details.recipientPersonId)
        lastSaved = details
        entries.update { list -> list.map { if (it.gift.id == id) entry(details, id) else it } }
    }

    override suspend fun deleteGift(id: UUID) {
        writeFailure?.let { throw it }
        entries.update { list -> list.filterNot { it.gift.id == id } }
    }

    private fun requireKnown(personId: UUID?) {
        if (personId != null && personId !in people) throw PersonNotFoundException(personId)
    }

    private fun entry(details: GiftDetails, id: UUID) = GiftEntry(
        gift = gift(details, id),
        giver = details.giverPersonId?.let { people[it] },
        recipient = details.recipientPersonId?.let { people[it] },
        occasion = null,
    )
}

fun giftDetails(
    direction: GiftDirection = GiftDirection.GIVEN,
    person: Person,
    name: String = "Book",
    givenAt: LocalDate = LocalDate.of(2026, 3, 12),
) = GiftDetails(
    direction = direction,
    giverPersonId = person.id.takeIf { direction == GiftDirection.RECEIVED },
    recipientPersonId = person.id.takeIf { direction == GiftDirection.GIVEN },
    occasionId = null,
    name = name,
    givenAt = givenAt,
    price = null,
    productUrl = null,
    note = null,
)

fun giftEntry(details: GiftDetails, person: Person, id: UUID = UUID.randomUUID()) = GiftEntry(
    gift = gift(details, id),
    giver = person.takeIf { details.giverPersonId == person.id },
    recipient = person.takeIf { details.recipientPersonId == person.id },
    occasion = null,
)

private fun gift(details: GiftDetails, id: UUID) = Gift(
    id = id,
    direction = details.direction,
    giverPersonId = details.giverPersonId,
    recipientPersonId = details.recipientPersonId,
    occasionId = details.occasionId,
    sourceGiftIdeaId = null,
    name = details.name,
    givenAt = details.givenAt,
    price = details.price,
    photoPath = null,
    productUrl = details.productUrl,
    note = details.note,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)
