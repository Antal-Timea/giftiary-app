package app.giftiary.presentation.components

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.giftiary.presentation.home.ComingUpContent
import app.giftiary.presentation.people.PeopleContent
import app.giftiary.presentation.people.PeopleScreen
import app.giftiary.presentation.people.PeopleUiState
import app.giftiary.presentation.theme.GiftiaryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The bundled gift images decorate; screen readers hear the words beside them, never the images. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp")
class DecorativeImageryTest {

    @get:Rule
    val compose = createComposeRule()

    private val described = SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)

    @Test
    fun `the corner cut-outs add nothing to what is read out`() {
        compose.setContent {
            GiftiaryTheme {
                DecoratedPage(top = GiftCutout.LARGE, bottom = GiftCutout.SMALL) { Text("Add a gift") }
            }
        }

        compose.onNodeWithText("Add a gift").assertExists()
        compose.onAllNodes(described, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `the People empty mark is silent, its title says it`() {
        compose.setContent {
            GiftiaryTheme {
                PeopleScreen(
                    state = PeopleUiState(content = PeopleContent.Loaded(emptyList())),
                    comingUp = ComingUpContent.Loaded(emptyList()),
                    onQueryChange = {},
                    onAddPerson = {},
                    onOpenPerson = {},
                )
            }
        }

        compose.onNodeWithText("No people yet").assertExists()
        compose.onAllNodes(described, useUnmergedTree = true).assertCountEquals(0)
    }
}
