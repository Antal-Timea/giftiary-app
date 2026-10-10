@file:UseSerializers(UuidSerializer::class, InstantSerializer::class)

package app.gifty.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant
import java.util.UUID

/**
 * A `public.people` row. Like every remote row, each column must be present in the response, and
 * may only be null where the column is nullable: nothing is silently defaulted.
 */
@Serializable
data class RemotePerson(
    override val id: UUID,
    @SerialName("owner_user_id") override val ownerUserId: UUID,
    val name: String,
    val relationship: String?,
    @SerialName("photo_path") val photoPath: String?,
    @SerialName("linked_user_id") val linkedUserId: UUID?,
    override val version: Long,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("updated_at") val updatedAt: Instant,
    @SerialName("deleted_at") val deletedAt: Instant?,
    // Null only for rows last written before mutation ids existed.
    @SerialName("last_mutation_id") override val lastMutationId: UUID?,
) : RemoteRow
