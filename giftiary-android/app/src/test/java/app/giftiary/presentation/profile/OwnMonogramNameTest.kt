package app.giftiary.presentation.profile

import app.giftiary.presentation.components.monogramInitial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Home, Profile and Edit profile draw the user's own monogram from the same name. */
class OwnMonogramNameTest {

    @Test
    fun `the display name wins over the email, so an edited name shows its own initial`() {
        assertEquals("G", monogramInitial(ownMonogramName("Gate A User", "lisa@example.com")!!))
    }

    @Test
    fun `the email stands in only while there is no name`() {
        assertEquals("lisa@example.com", ownMonogramName(null, "lisa@example.com"))
        assertEquals("lisa@example.com", ownMonogramName("  ", "lisa@example.com"))
    }

    @Test
    fun `without either there is no name, which leaves the people glyph`() {
        assertNull(ownMonogramName(null, null))
        assertNull(ownMonogramName("", " "))
    }
}
