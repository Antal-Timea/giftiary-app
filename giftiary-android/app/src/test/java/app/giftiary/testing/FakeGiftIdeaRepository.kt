package app.giftiary.testing

import app.giftiary.domain.model.Gift
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.model.GiftIdea
import app.giftiary.domain.model.GiftIdeaStatus
import app.giftiary.domain.model.Money
import app.giftiary.domain.model.Person
import app.giftiary.domain.repository.GiftIdeaAlreadyGivenException
import app.giftiary.domain.repository.GiftIdeaDetails
import app.giftiary.domain.repository.GiftIdeaEntry
import app.giftiary.domain.repository.GiftIdeaNotFoundException
import app.giftiary.domain.repository.GiftIdeaRepository
import app.giftiary.domain.repository.PersonNotFoundException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class FakeGiftIdeaRepository : GiftIdeaRepository {

    private val entries = MutableStateFlow<List<GiftIdeaEntry>>(emptyList())
    private val givenIds = mutableSetOf<UUID>()

    /** People ideas may be for, by id; others behave like a deleted or foreign person. */
    val people = mutableMapOf<UUID, Person>()

    /** When set, writes throw it, standing in for a failing local database. */
    var writeFailure: Exception? = null

    var lastSaved: GiftIdeaDetails? = null
        private set

    /** The arguments of each successful conversion. */
    val conversions = mutableListOf<Pair<LocalDate, Money?>>()

    val currentIdeas: List<GiftIdeaEntry> get() = entries.value

    fun givenIdeas(vararg entry: GiftIdeaEntry) {
        entries.value = entry.toList()
    }

    /** When set, [observeGiftIdeas] fails with it, standing in for a failing local read. */
    var readFailure: Exception? = null

    override fun observeGiftIdeas(): Flow<List<GiftIdeaEntry>> = flow {
        readFailure?.let { throw it }
        emitAll(entries)
    }

    override fun observeGiftIdeasForPerson(personId: UUID): Flow<List<GiftIdeaEntry>> =
        entries.map { list -> list.filter { it.idea.recipientPersonId == personId } }

    override fun observeGiftIdea(id: UUID): Flow<GiftIdeaEntry?> = entries.map { list -> list.find { it.idea.id == id } }

    override suspend fun getGiftIdea(id: UUID): GiftIdeaEntry? = entries.value.find { it.idea.id == id }

    /** Ids of the ideas with a change waiting to sync. */
    val waitingToSync = MutableStateFlow<Set<UUID>>(emptySet())

    override fun observeWaitingToSync(id: UUID): Flow<Boolean> = waitingToSync.map { id in it }

    override suspend fun addGiftIdea(details: GiftIdeaDetails): GiftIdea {
        writeFailure?.let { throw it }
        if (details.recipientPersonId !in people) throw PersonNotFoundException(details.recipientPersonId)
        lastSaved = details
        val entry = ideaEntry(details, people[details.recipientPersonId])
        entries.update { it + entry }
        return entry.idea
    }

    override suspend fun updateGiftIdea(id: UUID, details: GiftIdeaDetails) {
        writeFailure?.let { throw it }
        val existing = entries.value.find { it.idea.id == id } ?: throw GiftIdeaNotFoundException(id)
        if (details.recipientPersonId != existing.idea.recipientPersonId && details.recipientPersonId !in people) {
            throw PersonNotFoundException(details.recipientPersonId)
        }
        lastSaved = details
        val recipient = people[details.recipientPersonId] ?: existing.recipient
        entries.update { list -> list.map { if (it.idea.id == id) ideaEntry(details, recipient, id) else it } }
    }

    override suspend fun updateStatus(id: UUID, status: GiftIdeaStatus) {
        writeFailure?.let { throw it }
        if (entries.value.none { it.idea.id == id }) throw GiftIdeaNotFoundException(id)
        entries.update { list ->
            list.map { if (it.idea.id == id) it.copy(idea = it.idea.copy(status = status)) else it }
        }
    }

    override suspend fun deleteGiftIdea(id: UUID) {
        writeFailure?.let { throw it }
        entries.update { list -> list.filterNot { it.idea.id == id } }
    }

    override suspend fun markAsGiven(id: UUID, givenAt: LocalDate, price: Money?): Gift {
        writeFailure?.let { throw it }
        if (id in givenIds) throw GiftIdeaAlreadyGivenException(id)
        val entry = entries.value.find { it.idea.id == id } ?: throw GiftIdeaNotFoundException(id)
        givenIds += id
        conversions += givenAt to price
        entries.update { list -> list.filterNot { it.idea.id == id } }
        return Gift(
            id = UUID.randomUUID(),
            direction = GiftDirection.GIVEN,
            giverPersonId = null,
            recipientPersonId = entry.idea.recipientPersonId,
            occasionId = entry.idea.occasionId,
            sourceGiftIdeaId = id,
            name = entry.idea.name,
            givenAt = givenAt,
            price = price,
            photoPath = null,
            productUrl = entry.idea.productUrl,
            note = entry.idea.note,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }
}

fun ideaDetails(
    person: Person,
    name: String = "Scarf",
    status: GiftIdeaStatus = GiftIdeaStatus.IDEA,
    estimatedPrice: Money? = null,
    actualPrice: Money? = null,
) = GiftIdeaDetails(
    recipientPersonId = person.id,
    occasionId = null,
    name = name,
    status = status,
    estimatedPrice = estimatedPrice,
    actualPrice = actualPrice,
    productUrl = null,
    note = null,
)

fun ideaEntry(details: GiftIdeaDetails, recipient: Person?, id: UUID = UUID.randomUUID()) = GiftIdeaEntry(
    idea = GiftIdea(
        id = id,
        recipientPersonId = details.recipientPersonId,
        occasionId = details.occasionId,
        name = details.name,
        status = details.status,
        estimatedPrice = details.estimatedPrice,
        actualPrice = details.actualPrice,
        photoPath = null,
        productUrl = details.productUrl,
        note = details.note,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    ),
    recipient = recipient,
    occasion = null,
)
