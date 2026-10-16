package app.gifty.data.remote

import app.gifty.data.local.entity.RemoteState
import app.gifty.data.remote.RemoteTestData.alice
import app.gifty.data.remote.dto.RemoteGift
import app.gifty.data.remote.dto.RemoteGiftIdea
import app.gifty.data.remote.dto.RemoteOccasion
import app.gifty.data.remote.dto.RemotePerson
import app.gifty.data.remote.dto.RemoteSyncMutation
import app.gifty.data.remote.dto.RemoteWish
import app.gifty.data.remote.dto.RemoteWishlist
import app.gifty.domain.model.GiftDirection
import app.gifty.domain.model.GiftIdeaStatus
import app.gifty.domain.model.OccasionType
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Decoding of backend rows, and their mapping to Room rows. */
class RemoteRowsTest {

    // The SDK's default deserializer configuration.
    private val json = Json { ignoreUnknownKeys = true }
    private val id = UUID.fromString("11111111-1111-4111-8111-111111111111")

    @Test
    fun `a person keeps every value, and maps to a synced Room row at its version`() {
        val person = json.decodeFromJsonElement(RemotePerson.serializer(), RemoteTestData.person(id, alice))

        assertEquals(id, person.id)
        assertEquals(alice, person.ownerUserId)
        assertEquals(3L, person.version)
        assertNull(person.relationship)
        assertNull(person.deletedAt)
        assertEquals(UUID.fromString("c0000000-0000-4000-8000-000000000001"), person.lastMutationId)
        // Full precision is decoded; only the Room mapping drops the microseconds.
        assertEquals(Instant.parse("2026-10-01T10:00:00.123456Z"), person.createdAt)

        val entity = person.toEntity()
        assertEquals(RemoteState.SYNCED, entity.remoteState)
        assertEquals(3L, entity.version)
        assertEquals(alice, entity.ownerUserId)
        assertEquals(Instant.parse("2026-10-01T10:00:00.123Z"), entity.createdAt)
        assertEquals(Instant.parse("2026-10-02T11:30:00.654Z"), entity.updatedAt)
    }

    @Test
    fun `timestamps are absolute instants, whatever offset the server writes`() {
        val gift = json.decodeFromJsonElement(RemoteGift.serializer(), RemoteTestData.gift(id, alice))

        assertEquals(Instant.parse("2026-10-04T10:00:00Z"), gift.updatedAt)
        assertEquals(Instant.parse("2026-10-04T10:00:00Z"), gift.toEntity().deletedAt)
    }

    @Test
    fun `truncation to Room precision is deterministic`() {
        val person = json.decodeFromJsonElement(
            RemotePerson.serializer(),
            RemoteTestData.person(id, alice, mapOf("deleted_at" to "2026-10-05T08:00:00.999999+00:00")),
        )

        repeat(3) { assertEquals(Instant.parse("2026-10-05T08:00:00.999Z"), person.toEntity().deletedAt) }
    }

    @Test
    fun `a tombstoned person stays a tombstone`() {
        val person = json.decodeFromJsonElement(
            RemotePerson.serializer(),
            RemoteTestData.person(id, alice, mapOf("deleted_at" to "2026-10-05T08:00:00+00:00")),
        )

        assertEquals(Instant.parse("2026-10-05T08:00:00Z"), person.toEntity().deletedAt)
    }

    @Test
    fun `every occasion type decodes, with an unknown year and a leap day kept as they are`() {
        OccasionType.entries.forEach { type ->
            val occasion = json.decodeFromJsonElement(
                RemoteOccasion.serializer(),
                RemoteTestData.occasion(id, alice, mapOf("type" to type.name)),
            )
            assertEquals(type, occasion.type)
        }
        val entity = json.decodeFromJsonElement(RemoteOccasion.serializer(), RemoteTestData.occasion(id, alice)).toEntity()
        assertEquals(29, entity.day)
        assertEquals(2, entity.month)
        assertNull(entity.year)
        assertNull(entity.title)
        assertEquals(UUID.fromString("a0000000-0000-4000-8000-00000000000a"), entity.personId)
    }

    @Test
    fun `an archived gift idea keeps its archive marker, every status and its exact prices`() {
        GiftIdeaStatus.entries.forEach { status ->
            val idea = json.decodeFromJsonElement(
                RemoteGiftIdea.serializer(),
                RemoteTestData.giftIdea(id, alice, mapOf("status" to status.name)),
            )
            assertEquals(status, idea.status)
        }

        val entity = json.decodeFromJsonElement(RemoteGiftIdea.serializer(), RemoteTestData.giftIdea(id, alice)).toEntity()
        assertEquals(Instant.parse("2026-10-03T09:00:00Z"), entity.archivedAt)
        assertNull(entity.deletedAt)
        assertEquals(1299000L, entity.estimatedPriceAmountMinor)
        assertEquals("HUF", entity.estimatedPriceCurrencyCode)
        assertNull(entity.actualPriceAmountMinor)
        assertNull(entity.actualPriceCurrencyCode)
        assertNull(entity.occasionId)
        assertEquals(4L, entity.version)
    }

    @Test
    fun `a gift keeps its direction, date, source idea and an amount beyond double precision`() {
        GiftDirection.entries.forEach { direction ->
            val gift = json.decodeFromJsonElement(
                RemoteGift.serializer(),
                RemoteTestData.gift(id, alice, mapOf("direction" to direction.name)),
            )
            assertEquals(direction, gift.direction)
        }

        val entity = json.decodeFromJsonElement(RemoteGift.serializer(), RemoteTestData.gift(id, alice)).toEntity()
        assertEquals(LocalDate.of(2024, 2, 29), entity.givenAt)
        // 2^53 + 1: a Double would round it to 2^53.
        assertEquals(9007199254740993L, entity.priceAmountMinor)
        assertEquals("EUR", entity.priceCurrencyCode)
        assertEquals(UUID.fromString("d0000000-0000-4000-8000-00000000000d"), entity.sourceGiftIdeaId)
        assertNull(entity.giverPersonId)
        assertEquals(RemoteState.SYNCED, entity.remoteState)
    }

    @Test
    fun `a wishlist keeps its person and name, and maps to a synced Room row of its owner`() {
        val wishlist = json.decodeFromJsonElement(RemoteWishlist.serializer(), RemoteTestData.wishlist(id, alice))

        assertEquals(UUID.fromString("a0000000-0000-4000-8000-00000000000a"), wishlist.personId)
        assertEquals("Birthday", wishlist.name)
        assertEquals(UUID.fromString("c0000000-0000-4000-8000-000000000006"), wishlist.lastMutationId)
        val entity = wishlist.toEntity()
        assertEquals(alice, entity.ownerUserId)
        assertEquals(2L, entity.version)
        assertEquals(RemoteState.SYNCED, entity.remoteState)
        assertEquals(Instant.parse("2026-10-02T10:00:00.250Z"), entity.updatedAt)
        assertNull(json.decodeFromJsonElement(RemoteWishlist.serializer(), RemoteTestData.wishlist(id, alice, mapOf("person_id" to null))).personId)
    }

    @Test
    fun `a wish keeps every value, its tombstone and an amount beyond double precision, and its owner is the row's`() {
        val wish = json.decodeFromJsonElement(RemoteWish.serializer(), RemoteTestData.wish(id, alice))

        val entity = wish.toEntity()
        assertEquals(alice, entity.ownerUserId)
        assertEquals(UUID.fromString("70000000-0000-4000-8000-000000000007"), entity.wishlistId)
        assertEquals("Lamp", entity.name)
        assertEquals("Brass", entity.note)
        assertEquals(9007199254740993L, entity.estimatedPriceAmountMinor)
        assertEquals("HUF", entity.estimatedPriceCurrencyCode)
        assertEquals("https://example.com/lamp", entity.productUrl)
        assertEquals("users/$alice/wishes/$id/photo.jpg", entity.photoPath)
        assertEquals(5L, entity.version)
        assertEquals(RemoteState.SYNCED, entity.remoteState)
        assertEquals(Instant.parse("2026-10-05T10:00:00Z"), entity.deletedAt)
    }

    @Test
    fun `a ledger row decodes completely`() {
        val mutationId = UUID.fromString("c0000000-0000-4000-8000-000000000009")

        val mutation = json.decodeFromJsonElement(RemoteSyncMutation.serializer(), RemoteTestData.mutation(mutationId, alice))

        assertEquals(mutationId, mutation.mutationId)
        assertEquals(alice, mutation.ownerUserId)
        assertEquals("people", mutation.entityType)
        assertEquals(7L, mutation.resultingVersion)
        assertEquals(Instant.parse("2026-10-08T18:51:23.123456Z"), mutation.recordedAt)
    }

    @Test
    fun `columns added to the backend later are ignored`() {
        val person = json.decodeFromJsonElement(
            RemotePerson.serializer(),
            JsonObject(RemoteTestData.person(id, alice) + ("nickname" to JsonPrimitive("Annie"))),
        )

        assertEquals("Anna", person.name)
    }

    @Test
    fun `a missing column, an unknown enum value or a malformed value is rejected rather than defaulted`() {
        val invalid = listOf(
            JsonObject(RemoteTestData.person(id, alice) - "deleted_at"),
            RemoteTestData.person(id, alice, mapOf("created_at" to "2026-10-01 10:00:00")),
            RemoteTestData.person(id, alice, mapOf("id" to "not-a-uuid")),
            RemoteTestData.person(id, alice, mapOf("version" to null)),
        )
        invalid.forEach { row ->
            assertThrows("$row", SerializationException::class.java) {
                json.decodeFromJsonElement(RemotePerson.serializer(), row)
            }
        }
        assertThrows(SerializationException::class.java) {
            json.decodeFromJsonElement(
                RemoteOccasion.serializer(),
                RemoteTestData.occasion(id, alice, mapOf("type" to "GRADUATION")),
            )
        }
    }
}
