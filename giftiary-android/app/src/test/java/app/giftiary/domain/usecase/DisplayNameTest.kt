package app.giftiary.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayNameTest {

    // Surrounding whitespace, a trailing line break included, is trimmed rather than refused.
    @Test
    fun `a name is stored trimmed`() {
        assertEquals(DisplayNameInput.Valid("Anna Kovács"), normalizeDisplayName("  Anna Kovács \n"))
    }

    @Test
    fun `a blank name means no name, as the server requires`() {
        listOf("", "   ", "\u2003").forEach { raw ->
            assertEquals(raw, DisplayNameInput.Valid(null), normalizeDisplayName(raw))
        }
    }

    @Test
    fun `line breaks, tabs and other control characters inside a name are refused`() {
        listOf("Anna\nKovács", "Anna\tK", "An\u0000na", "Anna\u2028K", "An\rna").forEach { raw ->
            assertEquals(raw, DisplayNameInput.HasControlCharacters, normalizeDisplayName(raw))
        }
    }

    @Test
    fun `there is no length limit, as the server has none`() {
        val long = "A".repeat(500)
        assertEquals(DisplayNameInput.Valid(long), normalizeDisplayName(long))
    }

    @Test
    fun `nothing stands in for a missing name`() {
        assertEquals(DisplayNameInput.Valid(null), normalizeDisplayName(" "))
    }
}
