package com.encryptedsharedprefrencesdemo

import android.app.Application
import com.encryptedsharedprefrencesdemo.data.local.EncPref
import com.encryptedsharedprefrencesdemo.data.local.Preference
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class EncPrefApplication : Application() {

    @Inject
    lateinit var encPref: EncPref

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            timber.log.Timber.plant(timber.log.Timber.DebugTree())
        }
    }

}