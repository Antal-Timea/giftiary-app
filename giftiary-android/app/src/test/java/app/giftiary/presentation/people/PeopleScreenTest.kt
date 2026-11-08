package app.giftiary.presentation.people

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.model.Person
import app.giftiary.domain.model.UpcomingOccasion
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.presentation.home.ComingUpContent
import app.giftiary.presentation.gifts.LoadedPhoto
import app.giftiary.presentation.home.ComingUpItem
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.testing.occasion
import app.giftiary.testing.person
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/** The phone People screen: its parts and states, and what its rows and actions do. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2000dp")
class PeopleScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val peter = person("Peter", relationship = "Husband")
    private val martin = person("Martin", relationship = "Son")
    private val grandma = person("Ilona")
    private val peterBirthday = occasion(peter.id, OccasionDetails(OccasionType.BIRTHDAY, null, 12, 10, 1985, true))

    private var state by mutableStateOf(PeopleUiState())
    private var comingUp: ComingUpContent by mutableStateOf(ComingUpContent.Loaded(emptyList()))
    private val opened = mutableListOf<String>()
    private var added = 0

    private fun showPeople(vararg people: Person, query: String = "", photos: Map<String, LoadedPhoto> = emptyMap()) {
        state = PeopleUiState(
            content = PeopleContent.Loaded(people.toList()),
            query = query,
            birthdays = mapOf(peter.id to peterBirthday),
            photos = photos,
        )
        compose.setContent {
            GiftiaryTheme {
                PeopleScreen(
                    state = state,
                    comingUp = comingUp,
                    onQueryChange = { state = state.copy(query = it) },
                    onAddPerson = { added++ },
                    onOpenPerson = { opened += it.name },
                    showAddButton = false,
                )
            }
        }
    }

    @Test
    fun `everyone is listed under the title and search, with relationship and birthday, and opens on tap`() {
        showPeople(peter, martin, grandma)

        compose.onNodeWithText("People").assertExists()
        compose.onNodeWithText("The people behind your gift memories.").assertExists()
        compose.onNodeWithText("Search people…").assertExists()
        compose.onNodeWithText("All people").assertExists()
        // The birth year is not repeated on the row.
        compose.onNodeWithText("Husband · Birthday Oct 12").assertExists()
        compose.onNodeWithText("Son").assertExists()

        compose.onNodeWithText("Martin").performClick()
        assertEquals(listOf("Martin"), opened)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `a row shows the person's photo when it is on this device, else the monogram`() {
        val withPhoto = peter.copy(photoPath = "peter.jpg")
        val notHere = martin.copy(photoPath = "martin.jpg")

        showPeople(withPhoto, notHere, grandma, photos = mapOf("peter.jpg" to jpeg()))

        assertEquals(1, compose.onAllNodesWithTag(PERSON_PHOTO_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.onNodeWithText("Peter").performClick()
        assertEquals(listOf("Peter"), opened)
    }

    private fun jpeg(): LoadedPhoto {
        val bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF5A4032.toInt()) }
        return LoadedPhoto(ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray())
    }

    @Test
    fun `coming up shows the people's occasions only and opens the person`() {
        val christmas = occasion(null, OccasionDetails(OccasionType.CHRISTMAS, null, 24, 12, null, true))
        val date = LocalDate.of(2026, 10, 12)
        comingUp = ComingUpContent.Loaded(
            listOf(
                ComingUpItem(UpcomingOccasion(peterBirthday, peter, date, 22, 41)),
                ComingUpItem(UpcomingOccasion(christmas, null, LocalDate.of(2026, 12, 24), 75, null)),
            ),
        )
        showPeople(peter, martin)

        compose.onNodeWithText("Coming up").assertExists()
        compose.onNodeWithText("No ideas yet").assertExists()
        compose.onNodeWithText("Christmas", substring = true).assertDoesNotExist()

        compose.onNodeWithText("Husband · Birthday Oct 12 · turns 41").performClick()
        assertEquals(listOf("Peter"), opened)
    }

    @Test
    fun `a search narrows the list and hides coming up until it is cleared`() {
        comingUp = ComingUpContent.Loaded(listOf(ComingUpItem(UpcomingOccasion(peterBirthday, peter, LocalDate.of(2026, 10, 12), 22, null))))
        showPeople(peter, martin, grandma, query = "son")

        compose.onNodeWithText("Martin").assertExists()
        compose.onNodeWithText("Peter").assertDoesNotExist()
        compose.onNodeWithText("Coming up").assertDoesNotExist()

        state = state.copy(query = "")
        compose.waitForIdle()
        compose.onNodeWithText("Coming up").assertExists()
    }

    @Test
    fun `a search that finds no one says so for that search only, and clears in place`() {
        showPeople(peter, martin, grandma, query = "Robert ")

        compose.onNodeWithText("No people found for “Robert”").assertExists()
        compose.onNodeWithText("Try another name.").assertExists()
        compose.onNodeWithText("3 people are still here — only this search came up empty.").assertExists()
        compose.onNodeWithText("All people").assertDoesNotExist()

        // The field's own clear control and the state's action share the label; the state's is the text button.
        compose.onNodeWithText("Clear search").performClick()
        assertEquals("", state.query)
        compose.onNodeWithText("Martin").assertExists()
    }

    @Test
    fun `with no people the empty state invites adding the first, in place`() {
        showPeople()

        compose.onNodeWithText("No people yet").assertExists()
        compose.onNodeWithText("Search people…").assertDoesNotExist()
        compose.onNodeWithText("Add person").performClick()
        assertEquals(1, added)
    }
}
