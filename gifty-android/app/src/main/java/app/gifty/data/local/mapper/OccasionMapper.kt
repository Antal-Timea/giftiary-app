package app.gifty.data.local.mapper

import app.gifty.data.local.entity.OccasionEntity
import app.gifty.data.local.entity.RemoteState
import app.gifty.domain.model.Occasion
import java.util.UUID

fun OccasionEntity.toDomain(): Occasion = Occasion(
    id = id,
    personId = personId,
    type = type,
    title = title,
    day = day,
    month = month,
    year = year,
    repeatsYearly = repeatsYearly,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** Maps an occasion created on this device, which the backend has not seen yet. */
fun Occasion.toNewEntity(ownerUserId: UUID): OccasionEntity = OccasionEntity(
    id = id,
    ownerUserId = ownerUserId,
    personId = personId,
    type = type,
    title = title,
    day = day,
    month = month,
    year = year,
    repeatsYearly = repeatsYearly,
    version = 0,
    remoteState = RemoteState.LOCAL_ONLY,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = null,
)
