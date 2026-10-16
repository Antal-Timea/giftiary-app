package app.giftiary.data.local.entity

/**
 * Whether a row has ever reached the backend.
 *
 * This is not a sync status: whether local changes are still waiting to be pushed is described
 * by the pending [SyncOperationEntity] for the row, not by this flag.
 */
enum class RemoteState {
    /** Created on this device and never acknowledged by the backend. */
    LOCAL_ONLY,

    /** Known to the backend. */
    SYNCED,
}
