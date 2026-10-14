package app.gifty.data.sync

import app.gifty.data.remote.dto.RemoteRow
import app.gifty.data.remote.dto.RemoteSyncMutation
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.UUID

/** The proof rules, case by case. Ledger entries and rows are the only evidence. */
class CommitEvidenceTest {

    private val mutation = UUID.fromString("c1000000-0000-4000-8000-000000000001")
    private val other = UUID.fromString("c2000000-0000-4000-8000-000000000002")

    // ---- proof of a commit ----

    @Test
    fun `a ledger entry proves the commit at its version, whatever the row looks like now`() {
        Uncertainty.entries.forEach { uncertainty ->
            assertEquals(Verdict.Committed(4), judge(uncertainty, mutation, 3, ledger(4), row = null))
            assertEquals(Verdict.Committed(4), judge(uncertainty, mutation, 3, ledger(4), row(version = 9, lastMutation = other)))
            assertEquals(Verdict.Committed(1), judge(uncertainty, mutation, expectedVersion = null, ledger(1), row = null))
        }
    }

    @Test
    fun `a row last written by the mutation proves the commit at the row's version`() {
        Uncertainty.entries.forEach { uncertainty ->
            assertEquals(Verdict.Committed(4), judge(uncertainty, mutation, 3, ledger = null, row(4, mutation)))
            assertEquals(Verdict.Committed(1), judge(uncertainty, mutation, null, ledger = null, row(1, mutation)))
        }
    }

    // ---- inserts without proof ----

    @Test
    fun `an insert whose id exists through another mutation can never commit`() {
        Uncertainty.entries.forEach { uncertainty ->
            assertEquals(
                Verdict.CannotCommit(BlockReason.REMOTE_ID_TAKEN),
                judge(uncertainty, mutation, null, ledger = null, row(1, other)),
            )
        }
    }

    @Test
    fun `an insert refused as a duplicate while its id is free hit another unique key`() {
        assertEquals(Verdict.CannotCommit(BlockReason.REJECTED), judge(Uncertainty.DUPLICATE, mutation, null, null, null))
    }

    @Test
    fun `an insert without an answer and no trace remotely may be sent again`() {
        assertEquals(Verdict.MaySend, judge(Uncertainty.NO_ANSWER, mutation, null, null, null))
    }

    // ---- updates without proof ----

    @Test
    fun `an update of a row that is no longer visible can never commit`() {
        Uncertainty.entries.forEach { uncertainty ->
            assertEquals(Verdict.CannotCommit(BlockReason.REMOTE_MISSING), judge(uncertainty, mutation, 3, null, null))
        }
    }

    @Test
    fun `an update of a row that moved past the expected version is a conflict at the row's version`() {
        Uncertainty.entries.forEach { uncertainty ->
            assertEquals(Verdict.RemoteNewer(4), judge(uncertainty, mutation, 3, null, row(4, other)))
            assertEquals(Verdict.RemoteNewer(9), judge(uncertainty, mutation, 3, null, row(9, other)))
        }
    }

    @Test
    fun `an update of a row still at the expected version depends on what went wrong`() {
        val unchanged = row(3, other)
        assertEquals(Verdict.MaySend, judge(Uncertainty.NO_ANSWER, mutation, 3, null, unchanged))
        assertEquals(Verdict.CannotCommit(BlockReason.REJECTED), judge(Uncertainty.DUPLICATE, mutation, 3, null, unchanged))
        // The condition should have matched, so the answer itself is not trustworthy.
        assertEquals(Verdict.Unknown, judge(Uncertainty.NO_MATCH, mutation, 3, null, unchanged))
    }

    @Test
    fun `a remote row older than the version this device already had settles nothing`() {
        Uncertainty.entries.forEach { uncertainty ->
            assertEquals(Verdict.Unknown, judge(uncertainty, mutation, 3, null, row(2, other)))
        }
    }

    private fun ledger(version: Long) = RemoteSyncMutation(
        mutationId = mutation, ownerUserId = UUID.randomUUID(), entityType = "people", entityId = UUID.randomUUID(),
        resultingVersion = version, recordedAt = Instant.EPOCH,
    )

    private fun row(version: Long, lastMutation: UUID?) = object : RemoteRow {
        override val id: UUID = UUID.randomUUID()
        override val ownerUserId: UUID = UUID.randomUUID()
        override val version: Long = version
        override val lastMutationId: UUID? = lastMutation
    }
}
