package app.giftiary.presentation.people

import androidx.compose.ui.test.junit4.createComposeRule
import app.giftiary.domain.usecase.SavePersonUseCase
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.testing.FakePersonRepository
import app.giftiary.testing.MainDispatcherRule
import app.giftiary.testing.person
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/** Where the person editor leads once saved: a new person to their page, an edit back where it began. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersonEditorExitTest {

    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val repository = FakePersonRepository()
    private val exits = mutableListOf<String>()

    private fun show(personId: UUID?, opensCreated: Boolean): PersonEditorViewModel {
        val viewModel = PersonEditorViewModel(personId, repository, SavePersonUseCase(repository))
        compose.setContent {
            GiftiaryTheme {
                PersonEditorScreen(
                    viewModel = viewModel,
                    onClose = { exits += "close" },
                    onCreated = if (opensCreated) { id -> exits += "open $id" } else null,
                )
            }
        }
        return viewModel
    }

    @Test
    fun `a new person opens on their own page`() {
        val viewModel = show(personId = null, opensCreated = true)

        compose.runOnIdle {
            viewModel.onNameChange("Anna")
            viewModel.save()
        }
        compose.waitForIdle()

        assertEquals(listOf("open ${repository.currentPeople.single().id}"), exits)
    }

    @Test
    fun `a person added on the way to a gift returns to it`() {
        val viewModel = show(personId = null, opensCreated = false)

        compose.runOnIdle {
            viewModel.onNameChange("Anna")
            viewModel.save()
        }
        compose.waitForIdle()

        assertEquals(listOf("close"), exits)
    }

    @Test
    fun `an edited person returns where editing began`() {
        val anna = person("Anna")
        repository.givenPeople(anna)
        val viewModel = show(personId = anna.id, opensCreated = true)

        compose.runOnIdle {
            viewModel.onNameChange("Anna K.")
            viewModel.save()
        }
        compose.waitForIdle()

        assertEquals(listOf("close"), exits)
    }
}
