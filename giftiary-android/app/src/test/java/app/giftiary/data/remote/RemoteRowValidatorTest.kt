package app.giftiary.data.remote

import app.giftiary.data.remote.dto.RemoteGift
import app.giftiary.data.remote.dto.RemoteGiftIdea
import app.giftiary.data.remote.dto.RemoteOccasion
import app.giftiary.data.remote.dto.RemotePerson
import app.giftiary.data.remote.dto.RemoteWish
import app.giftiary.data.remote.dto.RemoteWishlist
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.model.GiftIdeaStatus
import app.giftiary.domain.model.OccasionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class RemoteRowValidatorTest {

    private val owner = UUID.randomUUID()
    private val anna = UUID.randomUUID()
    private val bela = UUID.randomUUID()
    private val now = Instant.parse("2026-10-12T12:00:00Z")

    private val person = RemotePerson(UUID.randomUUID(), owner, "Anna", null, null, null, 1, now, now, null, UUID.randomUUID())
    private val occasion = RemoteOccasion(
        UUID.randomUUID(), owner, anna, OccasionType.BIRTHDAY, null, 12, 5, null, true, 1, now, now, null, UUID.randomUUID(),
    )
    private val idea = RemoteGiftIdea(
        UUID.randomUUID(), owner, anna, null, "Scarf", GiftIdeaStatus.IDEA, null, null, null, null, null, null, null, 1, now, now,
        null, null, UUID.randomUUID(),
    )
    private val gift = RemoteGift(
        UUID.randomUUID(), owner, GiftDirection.GIVEN, null, anna, null, null, "Scarf", LocalDate.of(2026, 5, 12), null, null,
        null, null, null, 1, now, now, null, UUID.randomUUID(),
    )
    private val wishlist = RemoteWishlist(UUID.randomUUID(), owner, anna, "Birthday", 1, now, now, null, UUID.randomUUID())
    private val wish = RemoteWish(
        UUID.randomUUID(), owner, wishlist.id, "Lamp", null, null, null, null, null, 1, now, now, null, UUID.randomUUID(),
    )

    @Test
    fun `valid rows pass`() {
        RemoteRowValidator.validate(person)
        RemoteRowValidator.validate(occasion)
        RemoteRowValidator.validate(occasion.copy(day = 29, month = 2, year = null))
        RemoteRowValidator.validate(occasion.copy(day = 29, month = 2, year = 2028))
        RemoteRowValidator.validate(idea.copy(estimatedPriceAmountMinor = 12990, estimatedPriceCurrencyCode = "HUF"))
        RemoteRowValidator.validate(idea.copy(actualPriceAmountMinor = 0, actualPriceCurrencyCode = "EUR"))
        RemoteRowValidator.validate(gift.copy(priceAmountMinor = 1999, priceCurrencyCode = "USD"))
        RemoteRowValidator.validate(wishlist)
        RemoteRowValidator.validate(wishlist.copy(personId = null))
        RemoteRowValidator.validate(wish.copy(estimatedPriceAmountMinor = 4990, estimatedPriceCurrencyCode = "EUR"))
    }

    @Test
    fun `a version below 1 is refused for every table`() {
        listOf(0L, -1L).forEach { version ->
            refused("person $version") { RemoteRowValidator.validate(person.copy(version = version)) }
            refused("occasion $version") { RemoteRowValidator.validate(occasion.copy(version = version)) }
            refused("idea $version") { RemoteRowValidator.validate(idea.copy(version = version)) }
            refused("gift $version") { RemoteRowValidator.validate(gift.copy(version = version)) }
            refused("wishlist $version") { RemoteRowValidator.validate(wishlist.copy(version = version)) }
            refused("wish $version") { RemoteRowValidator.validate(wish.copy(version = version)) }
        }
    }

    @Test
    fun `an occasion on a day the calendar does not have is refused`() {
        refused("31 February") { RemoteRowValidator.validate(occasion.copy(day = 31, month = 2)) }
        refused("31 April") { RemoteRowValidator.validate(occasion.copy(day = 31, month = 4)) }
        refused("29 February 2027") { RemoteRowValidator.validate(occasion.copy(day = 29, month = 2, year = 2027)) }
        refused("day 0") { RemoteRowValidator.validate(occasion.copy(day = 0)) }
        refused("month 13") { RemoteRowValidator.validate(occasion.copy(month = 13)) }
    }

    @Test
    fun `money must be a non-negative amount with a three-letter upper-case currency, or nothing`() {
        val invalid = listOf(
            "amount without currency" to (1000L to null),
            "currency without amount" to (null to "HUF"),
            "negative amount" to (-1L to "HUF"),
            "lower-case currency" to (1000L to "huf"),
            "two letters" to (1000L to "HU"),
            "four letters" to (1000L to "HUFF"),
        )
        invalid.forEach { (case, money) ->
            val (amount, currency) = money
            refused("estimated: $case") {
                RemoteRowValidator.validate(idea.copy(estimatedPriceAmountMinor = amount, estimatedPriceCurrencyCode = currency))
            }
            refused("actual: $case") {
                RemoteRowValidator.validate(idea.copy(actualPriceAmountMinor = amount, actualPriceCurrencyCode = currency))
            }
            refused("gift: $case") { RemoteRowValidator.validate(gift.copy(priceAmountMinor = amount, priceCurrencyCode = currency)) }
            refused("wish: $case") {
                RemoteRowValidator.validate(wish.copy(estimatedPriceAmountMinor = amount, estimatedPriceCurrencyCode = currency))
            }
        }
    }

    @Test
    fun `an idea's occasion must be its recipient's or general`() {
        RemoteRowValidator.requireOccasionFits(idea, occasionPersonId = null)
        RemoteRowValidator.requireOccasionFits(idea, occasionPersonId = anna)

        refused("Béla's occasion") { RemoteRowValidator.requireOccasionFits(idea, occasionPersonId = bela) }
    }

    @Test
    fun `a gift's occasion must be its giver's, its recipient's or general`() {
        val fromBelaToAnna = gift.copy(direction = GiftDirection.RECEIVED, giverPersonId = bela, recipientPersonId = anna)
        RemoteRowValidator.requireOccasionFits(fromBelaToAnna, occasionPersonId = null)
        RemoteRowValidator.requireOccasionFits(fromBelaToAnna, occasionPersonId = anna)
        RemoteRowValidator.requireOccasionFits(fromBelaToAnna, occasionPersonId = bela)

        refused("a third person's occasion") { RemoteRowValidator.requireOccasionFits(fromBelaToAnna, UUID.randomUUID()) }
        refused("given gift, someone else's occasion") { RemoteRowValidator.requireOccasionFits(gift, occasionPersonId = bela) }
    }

    @Test
    fun `an occasion keeps the person it has locally`() {
        RemoteRowValidator.requireSamePerson(occasion, localPersonId = anna)
        RemoteRowValidator.requireSamePerson(occasion.copy(personId = null), localPersonId = null)

        refused("moved to Béla") { RemoteRowValidator.requireSamePerson(occasion, localPersonId = bela) }
        refused("made general") { RemoteRowValidator.requireSamePerson(occasion.copy(personId = null), localPersonId = anna) }
        refused("given a person") { RemoteRowValidator.requireSamePerson(occasion, localPersonId = null) }
    }

    @Test
    fun `a wishlist keeps its person and a wish its wishlist`() {
        RemoteRowValidator.requireSamePerson(wishlist, localPersonId = anna)
        RemoteRowValidator.requireSamePerson(wishlist.copy(personId = null), localPersonId = null)
        RemoteRowValidator.requireSameWishlist(wish, localWishlistId = wishlist.id)

        refused("wishlist moved to Béla") { RemoteRowValidator.requireSamePerson(wishlist, localPersonId = bela) }
        refused("wishlist made personless") { RemoteRowValidator.requireSamePerson(wishlist.copy(personId = null), localPersonId = anna) }
        refused("wishlist given a person") { RemoteRowValidator.requireSamePerson(wishlist, localPersonId = null) }
        refused("wish moved") { RemoteRowValidator.requireSameWishlist(wish, localWishlistId = UUID.randomUUID()) }
    }

    private fun refused(case: String, check: () -> Unit) {
        val failure = assertThrows(case, RemoteException::class.java) { check() }
        assertEquals(case, RemoteFailure.SERVER, failure.failure)
    }
}
