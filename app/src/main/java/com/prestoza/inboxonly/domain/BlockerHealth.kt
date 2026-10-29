// BlockerHealth: whether the accessibility service can actually block. "Enabled" in Settings isn't
// enough: after an app update Android can leave the service switched on but not running, and nothing
// gets blocked until the user switches it off and on again.
package com.prestoza.inboxonly.domain

/** The accessibility service's state, as far as blocking is concerned. */
enum class BlockerHealth {
    /** Not switched on in Settings → Accessibility. */
    NOT_ENABLED,

    /** Switched on, but Android isn't running it (seen after app updates). */
    STOPPED,

    /** Switched on and running. */
    RUNNING,
}
