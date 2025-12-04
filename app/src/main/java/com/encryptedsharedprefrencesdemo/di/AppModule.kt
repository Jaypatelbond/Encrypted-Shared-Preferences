package com.encryptedsharedprefrencesdemo.di

import android.content.Context
import com.encryptedsharedprefrencesdemo.data.local.EncPref
import com.encryptedsharedprefrencesdemo.data.local.Preference
import com.encryptedsharedprefrencesdemo.data.local.PreferenceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class AppModule {

    /**
     * Provides an instance of [EncPref] for encrypted shared preferences.
     *
     * @param context The application context.
     * @return An instance of [EncPref].
     */
    @Singleton
    @Provides
    fun provideAppEncSharedPref(@ApplicationContext context: Context): EncPref {
        return EncPref(context = context)
    }

    /**
     * Provides an instance of [Preference] and the application context.
     *
     * @param context The application context.
     * @return An instance of [Preference].
     */
    @Singleton
    @Provides
    fun provideAppPreference(@ApplicationContext context: Context): Preference {
        return PreferenceManager(
            provideAppEncSharedPref(context = context)
        )
    }
}