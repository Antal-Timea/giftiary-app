@file:UseSerializers(UuidSerializer::class, InstantSerializer::class)

package app.gifty.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant
import java.util.UUID

/**
 * A `public.sync_mutations` ledger row: proof that the mutation committed and produced
 * [resultingVersion] of the row. [entityType] is the table name, e.g. "people".
 */
@Serializable
data class RemoteSyncMutation(
    @SerialName("mutation_id") val mutationId: UUID,
    @SerialName("owner_user_id") val ownerUserId: UUID,
    @SerialName("entity_type") val entityType: String,
    @SerialName("entity_id") val entityId: UUID,
    @SerialName("resulting_version") val resultingVersion: Long,
    @SerialName("recorded_at") val recordedAt: Instant,
)
