package com.getup.iClouDroid

import android.app.Application
import com.getup.iClouDroid.di.AppContainer

/**
 * Application entry point; owns the single [AppContainer].
 *
 * ViewModels retrieve their dependencies via `(application as IClouDroidApp).container`, which
 * keeps them free of static singletons and makes each feature's dependencies explicit.
 */
class IClouDroidApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
