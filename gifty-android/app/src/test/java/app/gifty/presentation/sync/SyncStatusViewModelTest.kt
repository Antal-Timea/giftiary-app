package app.gifty.presentation.sync

import app.gifty.domain.model.AttentionItem
import app.gifty.domain.model.AttentionKind
import app.gifty.domain.model.SyncItem
import app.gifty.domain.model.SyncItemType
import app.gifty.domain.model.SyncStatus
import app.gifty.testing.FakeSyncStatusRepository
import app.gifty.testing.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.UUID

class SyncStatusViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSyncStatusRepository()

    @Test
    fun `before the local status is read nothing is claimed, then it follows the repository`() = runTest {
        val viewModel = SyncStatusViewModel(repository)
        assertEquals(SyncStatusContent.Loading, viewModel.content.value)

        collect(viewModel)
        assertEquals(SyncStatusContent.Loaded(SyncStatus(0, emptyList())), viewModel.content.value)

        val waiting = SyncStatus(waitingCount = 2, attention = listOf(item(AttentionKind.REFUSED)))
        repository.status.value = waiting
        assertEquals(SyncStatusContent.Loaded(waiting), viewModel.content.value)
    }

    @Test
    fun `a status that cannot be read is unavailable, never a failed save`() = runTest {
        repository.statusFlowFailure = IOException("disk")
        val viewModel = SyncStatusViewModel(repository)
        collect(viewModel)

        assertEquals(SyncStatusContent.Unavailable, viewModel.content.value)
    }

    @Test
    fun `only conflicts are dismissed, through the repository`() = runTest {
        val viewModel = SyncStatusViewModel(repository)
        val replaced = item(AttentionKind.REPLACED_BY_OTHER_DEVICE)
        val duplicate = item(AttentionKind.DUPLICATE_GIFT_REMOVED)

        AttentionKind.entries.forEach { viewModel.onDismiss(item(it)) }
        viewModel.onDismiss(replaced)
        viewModel.onDismiss(duplicate)

        assertEquals(4, repository.dismissed.size)
        assertTrue(repository.dismissed.containsAll(listOf(replaced.id, duplicate.id)))
    }

    @Test
    fun `a failed dismissal leaves the conflict to be dismissed again`() = runTest {
        repository.dismissFailure = IOException("disk")
        val viewModel = SyncStatusViewModel(repository)
        collect(viewModel)

        viewModel.onDismiss(item(AttentionKind.REPLACED_BY_OTHER_DEVICE))

        assertTrue(repository.dismissed.isEmpty())
        assertEquals(SyncStatusContent.Loaded(SyncStatus(0, emptyList())), viewModel.content.value)
    }

    private fun TestScope.collect(viewModel: SyncStatusViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.content.collect {} }
    }

    private fun item(kind: AttentionKind) =
        AttentionItem(UUID.randomUUID(), kind, SyncItem(SyncItemType.PERSON, UUID.randomUUID(), "Anna"), canOpen = false)
}
