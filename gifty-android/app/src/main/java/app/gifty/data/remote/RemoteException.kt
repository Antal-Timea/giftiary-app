package app.gifty.data.remote

/** Why a remote call failed, as far as sync needs to tell the cases apart. */
enum class RemoteFailure {
    /** No session, or the server rejected the captured token (e.g. it expired). */
    UNAUTHENTICATED,

    /** The session belongs to another account than the one the work is for. */
    ACCOUNT_MISMATCH,

    /** The account may not do this. */
    FORBIDDEN,

    /** No usable response; the request may or may not have reached the server. */
    NETWORK,

    /** The server failed, or answered something this client cannot accept. */
    SERVER,
}

class RemoteException(val failure: RemoteFailure, cause: Throwable? = null) : Exception(failure.name, cause)
