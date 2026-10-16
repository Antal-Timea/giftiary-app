package app.giftiary.data.local.mapper

import app.giftiary.data.local.entity.OccasionEntity
import app.giftiary.data.local.entity.RemoteState
import app.giftiary.domain.model.Occasion
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
