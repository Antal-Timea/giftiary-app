package app.giftiary.data.user

/**
 * Asks for accounts whose data stays on this device without their session to be checked again
 * soon, see [DeletedAccountCheck]. Only a hint: it returns at once.
 */
fun interface DeletedAccountCheckRequester {
    fun requestCheck()
}
