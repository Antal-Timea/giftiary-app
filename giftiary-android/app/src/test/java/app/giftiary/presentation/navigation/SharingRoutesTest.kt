package app.giftiary.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

/** Sharing routes carry ids only; the join screen takes its token from memory, not from a route. */
class SharingRoutesTest {

    @Test
    fun `a joined list opens as a shared list`() {
        val id = UUID.randomUUID()

        assertEquals(SharedWishlistRoute(id.toString()), joinedRoute(id))
    }
}
