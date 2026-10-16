package app.giftiary.data.remote.dto

import java.util.UUID

/** A row of one of the synced tables, read for its owner. */
interface RemoteRow {
    val id: UUID
    val ownerUserId: UUID
    val version: Long
    val lastMutationId: UUID?
}
