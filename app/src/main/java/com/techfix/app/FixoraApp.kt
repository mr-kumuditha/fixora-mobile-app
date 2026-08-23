package com.techfix.app

import android.app.Application
import com.techfix.app.core.data.RepositoryProvider

/**
 * Firebase initializes itself via the google-services ContentProvider and
 * the Supabase client is built lazily on first use. The one thing that has
 * to happen here is handing RepositoryProvider an application context, since
 * the Room-backed offline cache and booking draft need one.
 */
class FixoraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RepositoryProvider.initialize(this)
    }
}
