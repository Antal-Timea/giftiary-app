package app.gifty.data.sync

/**
 * Asks for the active account's queue to be pushed and the backend's data then pulled, soon. Like
 * [SyncRequester], only a hint: it returns at once, and pulling everything again is always safe.
 */
fun interface FullSyncRequester {
    fun requestFullSync()
}
