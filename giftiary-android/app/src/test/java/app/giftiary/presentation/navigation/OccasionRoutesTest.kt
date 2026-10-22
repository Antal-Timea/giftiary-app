package app.giftiary.presentation.navigation

import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.presentation.giftideas.GiftIdeaEditorTarget
import app.giftiary.testing.occasion
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

/** Where occasions lead; the screens call these, so the mapping is checked without Compose. */
class OccasionRoutesTest {

    private val annaId = UUID.randomUUID()
    private val birthday = occasion(annaId, OccasionDetails(OccasionType.BIRTHDAY, null, 12, 5, null, true))
    private val christmas = occasion(null, OccasionDetails(OccasionType.CHRISTMAS, null, 24, 12, null, true))

    @Test
    fun `an idea added from a plan starts with the occasion, and with its person when it has one`() {
        val fromBirthday = addIdeaRoute(birthday)
        val fromChristmas = addIdeaRoute(christmas)

        assertEquals(GiftIdeaEditorTarget.New(annaId, birthday.id), GiftIdeaEditorTarget.newFor(fromBirthday.personId, fromBirthday.occasionId))
        assertEquals(GiftIdeaEditorTarget.New(null, christmas.id), GiftIdeaEditorTarget.newFor(fromChristmas.personId, fromChristmas.occasionId))
    }
}
