// InboxOnlyApp: the Application subclass. Android creates exactly one per process, before any
// activity, service or receiver runs, which makes it the natural home for the AppContainer.
package com.prestoza.inboxonly

import android.app.Application
import android.content.Context

/** Process-wide entry point; owns the [AppContainer]. */
class InboxOnlyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Shortcut for Android components: `context.appContainer`. */
val Context.appContainer: AppContainer
    get() = (applicationContext as InboxOnlyApp).container
