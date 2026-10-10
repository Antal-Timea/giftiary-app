package app.gifty.data.remote

/** Why a remote call failed, as far as sync needs to tell the cases apart. */
enum class RemoteFailure {
    /** No session, or the server rejected the captured token (e.g. it expired). */
    UNAUTHENTICATED,

    /** The session belongs to another account than the one the work is for. */
    ACCOUNT_MISMATCH,

    /** The account may not do this. */
    FORBIDDEN,

    /**
     * No usable response; the request may or may not have reached the server. For a write this
     * proves nothing either way: it may still commit, and only the mutation ledger can tell.
     */
    NETWORK,

    /**
     * A unique key already exists (PostgreSQL 23505): the row id, or the mutation id, was used
     * before. Whether that earlier use was this very mutation is for the ledger to tell.
     */
    DUPLICATE,

    /**
     * A referenced row does not exist (PostgreSQL 23503), e.g. a gift's person not synced yet.
     * Not a problem of the values themselves: the same write can succeed once the parent exists.
     */
    MISSING_REFERENCE,

    /** The server refused the values (a PostgreSQL integrity or data error); sending them again fails again. */
    CONSTRAINT_VIOLATION,

    /** The server failed, or answered something this client cannot accept. */
    SERVER,
}

class RemoteException(val failure: RemoteFailure, cause: Throwable? = null) : Exception(failure.name, cause)
