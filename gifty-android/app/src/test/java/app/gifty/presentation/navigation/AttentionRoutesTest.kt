package app.gifty.presentation.navigation

import app.gifty.domain.model.SyncItem
import app.gifty.domain.model.SyncItemType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

/** Where an item that needs attention opens; only openable items are offered, so every type has a screen. */
class AttentionRoutesTest {

    private val id = UUID.randomUUID()

    @Test
    fun `each item opens its own screen`() {
        assertEquals(PersonDetailRoute(id.toString()), routeOf(SyncItem(SyncItemType.PERSON, id, "Anna")))
        assertEquals(EditOccasionRoute(id.toString()), routeOf(SyncItem(SyncItemType.OCCASION, id, null)))
        assertEquals(GiftIdeaDetailRoute(id.toString()), routeOf(SyncItem(SyncItemType.GIFT_IDEA, id, "Scarf")))
        assertEquals(GiftDetailRoute(id.toString()), routeOf(SyncItem(SyncItemType.GIFT, id, "Book")))
        assertEquals(WishlistDetailRoute(id.toString()), routeOf(SyncItem(SyncItemType.WISHLIST, id, "Birthday")))
        assertEquals(WishDetailRoute(id.toString()), routeOf(SyncItem(SyncItemType.WISH, id, "Lamp")))
    }
}
