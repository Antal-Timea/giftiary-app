package app.gifty.domain.model

/** Whether the device lets Gifty show notifications right now. */
enum class NotificationAccess {
    GRANTED,

    /** Android asks for permission first (13 and later), and it has not been given. */
    PERMISSION_MISSING,

    /** Turned off in the system settings, for the app or for its reminder channel. */
    BLOCKED,
}
