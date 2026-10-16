package app.giftiary.domain.repository

/** A data change was attempted while no account's data is open, e.g. during sign-out. */
class NoActiveOwnerException : IllegalStateException("No signed-in account's data is open")
