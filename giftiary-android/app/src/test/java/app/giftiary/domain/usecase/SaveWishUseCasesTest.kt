package app.giftiary.domain.usecase

import app.giftiary.domain.model.Money
import app.giftiary.testing.FakeWishlistRepository
import app.giftiary.testing.wish
import app.giftiary.testing.wishlist
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SaveWishUseCasesTest {

    private val repository = FakeWishlistRepository()
    private val saveWishlist = SaveWishlistUseCase(repository)
    private val saveWish = SaveWishUseCase(repository)

    @Test
    fun `a wishlist name is trimmed, and a blank one is refused without writing`() = runTest {
        val saved = saveWishlist.add(personId = null, name = "  Birthday ")
        val blank = saveWishlist.add(personId = null, name = "   ")

        val created = repository.currentWishlists.single()
        assertEquals(SaveWishlistResult.Saved(created.id), saved)
        assertEquals("Birthday", created.name)
        assertNull(created.personId)
        assertEquals(SaveWishlistResult.BlankName, blank)
    }

    @Test
    fun `renaming trims too and keeps who the wishlist is for`() = runTest {
        val annaId = java.util.UUID.randomUUID()
        val list = wishlist("Birthday", personId = annaId)
        repository.givenWishlists(list)

        assertEquals(SaveWishlistResult.Saved(list.id), saveWishlist.rename(list.id, " Name day "))
        assertEquals(SaveWishlistResult.BlankName, saveWishlist.rename(list.id, ""))

        assertEquals(list.copy(name = "Name day"), repository.currentWishlists.single())
    }

    @Test
    fun `a wish is stored trimmed, with blank optional text stored as nothing`() = runTest {
        val list = wishlist("Birthday").also { repository.givenWishlists(it) }

        val result = saveWish.add(list.id, WishInput(name = " Lamp ", note = "  ", estimatedPrice = Money(12990, "HUF"), productUrl = " ", photoPath = null))

        assertEquals(SaveWishResult.SAVED, result)
        val stored = repository.currentWishes.single()
        assertEquals("Lamp", stored.name)
        assertNull(stored.note)
        assertNull(stored.productUrl)
        assertEquals(Money(12990, "HUF"), stored.estimatedPrice)
    }

    @Test
    fun `a wish without a name is refused and nothing changes`() = runTest {
        val list = wishlist("Birthday").also { repository.givenWishlists(it) }
        val lamp = wish(list.id, "Lamp", note = "Brass").also { repository.givenWishes(it) }

        assertEquals(SaveWishResult.BLANK_NAME, saveWish.add(list.id, WishInput(" ", "", null, "", null)))
        assertEquals(SaveWishResult.BLANK_NAME, saveWish.update(lamp.id, WishInput("", "Other", null, "", null)))

        assertEquals(listOf(lamp), repository.currentWishes)
    }

    @Test
    fun `an edited wish keeps trimmed text, and the photo it is saved with is the one it names`() = runTest {
        val list = wishlist("Birthday").also { repository.givenWishlists(it) }
        val photo = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/00000000-0000-4000-8000-000000000001.jpg"
        val lamp = wish(list.id, "Lamp", photoPath = photo).also { repository.givenWishes(it) }

        saveWish.update(lamp.id, WishInput("Brass lamp", " Small ", null, " https://example.com/lamp ", photo))

        val stored = repository.currentWishes.single()
        assertEquals("Small", stored.note)
        assertEquals("https://example.com/lamp", stored.productUrl)
        assertEquals(photo, stored.photoPath)

        saveWish.update(lamp.id, WishInput("Brass lamp", "", null, "", null))
        assertNull("saved without a photo, it names none", repository.currentWishes.single().photoPath)
    }

    @Test
    fun `a new wish names the photo it is saved with`() = runTest {
        val list = wishlist("Birthday").also { repository.givenWishlists(it) }
        val photo = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/00000000-0000-4000-8000-000000000002.jpg"

        saveWish.add(list.id, WishInput("Lamp", "", null, "", photo))

        assertEquals(photo, repository.currentWishes.single().photoPath)
    }
}
