// AlarmReceiver: runs when the Focus alarm fires. A BroadcastReceiver is a short-lived Android
// callback; goAsync() lets us finish the (quick) DataStore work off the main thread before Android
// considers the broadcast handled.
package com.prestoza.inboxonly.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.prestoza.inboxonly.appContainer
import kotlinx.coroutines.launch

/** Re-evaluates Focus when a timer, break or schedule edge is reached. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pending = goAsync()
        val container = context.appContainer
        container.applicationScope.launch {
            try {
                container.focusCoordinator.refresh()
            } finally {
                pending.finish()
            }
        }
    }
}
