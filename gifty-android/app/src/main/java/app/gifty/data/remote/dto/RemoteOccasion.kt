@file:UseSerializers(UuidSerializer::class, InstantSerializer::class)

package app.gifty.data.remote.dto

import app.gifty.domain.model.OccasionType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant
import java.util.UUID

/** A `public.occasions` row. */
@Serializable
data class RemoteOccasion(
    override val id: UUID,
    @SerialName("owner_user_id") override val ownerUserId: UUID,
    @SerialName("person_id") val personId: UUID?,
    // The backend enums use exactly the app's constant names; an unknown name fails decoding.
    val type: OccasionType,
    val title: String?,
    val day: Int,
    val month: Int,
    val year: Int?,
    @SerialName("repeats_yearly") val repeatsYearly: Boolean,
    val version: Long,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("updated_at") val updatedAt: Instant,
    @SerialName("deleted_at") val deletedAt: Instant?,
    @SerialName("last_mutation_id") val lastMutationId: UUID?,
) : RemoteRow
