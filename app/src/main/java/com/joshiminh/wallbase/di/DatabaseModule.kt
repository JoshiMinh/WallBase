package com.joshiminh.wallbase.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.joshiminh.wallbase.data.WallBaseDatabase
import com.joshiminh.wallbase.data.dao.AlbumDao
import com.joshiminh.wallbase.data.dao.RotationScheduleDao
import com.joshiminh.wallbase.data.dao.SourceDao
import com.joshiminh.wallbase.data.dao.WallpaperDao
import com.joshiminh.wallbase.data.repository.settingsDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WallBaseDatabase {
        return WallBaseDatabase.getInstance(context)
    }

    @Provides
    fun provideSourceDao(database: WallBaseDatabase): SourceDao = database.sourceDao()

    @Provides
    fun provideWallpaperDao(database: WallBaseDatabase): WallpaperDao = database.wallpaperDao()

    @Provides
    fun provideAlbumDao(database: WallBaseDatabase): AlbumDao = database.albumDao()

    @Provides
    fun provideRotationScheduleDao(database: WallBaseDatabase): RotationScheduleDao = 
        database.rotationScheduleDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.settingsDataStore
    }

    @Provides
    @Singleton
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
