package app.giftiary.data.sync

/**
 * Asks for the active account's queue to be pushed soon. Only a hint: it returns at once, and the
 * queue, not the request, is what decides what gets pushed.
 */
fun interface SyncRequester {
    fun requestSync()
}
