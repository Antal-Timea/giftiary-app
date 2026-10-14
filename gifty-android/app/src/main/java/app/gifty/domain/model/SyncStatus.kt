package app.gifty.domain.model

import java.util.UUID

/**
 * What this device knows about its own sync work. It says nothing about the backend: an empty
 * status means no local change is waiting, not that the device matches the account's data.
 */
data class SyncStatus(
    /** Local changes still expected to reach the backend without the user's help. */
    val waitingCount: Int,
    /** What sync could not settle on its own, newest conflicts first, then blocked changes. */
    val attention: List<AttentionItem>,
)

/**
 * One thing that needs the user to know about it. [id] is the conflict's for a conflict, which
 * the user can dismiss, and the pending change's otherwise.
 */
data class AttentionItem(
    val id: UUID,
    val kind: AttentionKind,
    val item: SyncItem,
    /** Whether the item can be shown by its usual screen right now. Opening it resolves nothing. */
    val canOpen: Boolean,
) {
    val canDismiss: Boolean
        get() = kind == AttentionKind.REPLACED_BY_OTHER_DEVICE || kind == AttentionKind.DUPLICATE_GIFT_REMOVED
}

enum class AttentionKind {
    /** Changes made here could not be applied; another device's version of the item was kept. */
    REPLACED_BY_OTHER_DEVICE,

    /** The same idea was given on another device too; that gift was kept and this one removed. */
    DUPLICATE_GIFT_REMOVED,

    /** The account did not accept this change as it is. */
    REFUSED,

    /** This change cannot reach the account. */
    UNAVAILABLE,
}

/** An item as the user knows it: occasions are named by [occasionType] when they have no [name]. */
data class SyncItem(
    val type: SyncItemType,
    val id: UUID,
    val name: String?,
    val occasionType: OccasionType? = null,
)

enum class SyncItemType {
    PERSON,
    OCCASION,
    GIFT_IDEA,
    GIFT,
}
