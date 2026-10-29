// InboxOnlyApp: the Application subclass. Android creates exactly one per process, before any
// activity, service or receiver runs, which makes it the natural home for the AppContainer.
package com.prestoza.inboxonly

import android.app.Application
import android.content.Context
import kotlinx.coroutines.launch

/** Process-wide entry point; owns the [AppContainer]. */
class InboxOnlyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Any process start (tile, accessibility service, receiver) re-checks Focus and its alarm. This
        // also repairs things after a force-stop, which silently deletes all of an app's alarms.
        container.applicationScope.launch { container.focusCoordinator.refresh() }
    }
}

/** Shortcut for Android components: `context.appContainer`. */
val Context.appContainer: AppContainer
    get() = (applicationContext as InboxOnlyApp).container
