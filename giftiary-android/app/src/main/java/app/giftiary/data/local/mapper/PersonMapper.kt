package app.giftiary.data.local.mapper

import app.giftiary.data.local.entity.PersonEntity
import app.giftiary.data.local.entity.RemoteState
import app.giftiary.domain.model.Person
import java.util.UUID

fun PersonEntity.toDomain(): Person = Person(
    id = id,
    name = name,
    relationship = relationship,
    photoPath = photoPath,
    linkedUserId = linkedUserId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** Maps a person created on this device, which the backend has not seen yet. */
fun Person.toNewEntity(ownerUserId: UUID): PersonEntity = PersonEntity(
    id = id,
    ownerUserId = ownerUserId,
    name = name,
    relationship = relationship,
    photoPath = photoPath,
    linkedUserId = linkedUserId,
    version = 0,
    remoteState = RemoteState.LOCAL_ONLY,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = null,
)

