// BlockScreenBridge: lets the block screen ask the accessibility service to press Back. Only the
// service can press Back in another app, and only after the block screen has gone, so the screen hands
// the request over here. Both live in the same process, so a plain in-memory hook is enough.
package com.prestoza.inboxonly.service

/** Connects [com.prestoza.inboxonly.ui.BlockScreenActivity] to [BlockerAccessibilityService]. */
class BlockScreenBridge {
    /** Set by the service while it's connected; null otherwise. */
    @Volatile
    var goBack: ((packageName: String, surfaceId: String) -> Unit)? = null

    /** Asks the service to press Back in [packageName]. Returns false if the service isn't running. */
    fun requestBack(
        packageName: String,
        surfaceId: String,
    ): Boolean {
        val handler = goBack ?: return false
        handler(packageName, surfaceId)
        return true
    }
}
