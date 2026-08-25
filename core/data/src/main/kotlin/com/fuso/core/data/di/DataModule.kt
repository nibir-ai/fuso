package com.fuso.core.data.di

import android.content.Context
import androidx.room.Room
import com.fuso.core.data.repository.DeviceCalendarRepository
import com.fuso.core.data.repository.DeviceCalendarRepositoryImpl
import com.fuso.core.data.repository.EntryRepository
import com.fuso.core.data.repository.RoomEntryRepository
import com.fuso.core.database.FusoDatabase
import com.fuso.core.database.dao.BlockDao
import com.fuso.core.database.dao.EntryDao
import com.fuso.core.database.dao.UsageDao
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindEntryRepository(impl: RoomEntryRepository): EntryRepository

    @Binds
    @Singleton
    abstract fun bindDeviceCalendarRepository(impl: DeviceCalendarRepositoryImpl): DeviceCalendarRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FusoDatabase =
        Room.databaseBuilder(context, FusoDatabase::class.java, FusoDatabase.NAME)
            .addMigrations(FusoDatabase.MIGRATION_1_2, FusoDatabase.MIGRATION_2_3, FusoDatabase.MIGRATION_3_4)
            .build()

    @Provides
    fun provideEntryDao(database: FusoDatabase): EntryDao = database.entryDao()

    @Provides
    fun provideBlockDao(database: FusoDatabase): BlockDao = database.blockDao()

    @Provides
    fun provideUsageDao(database: FusoDatabase): UsageDao = database.usageDao()

    @Provides
    fun provideOutboxDao(database: FusoDatabase): com.fuso.core.database.dao.OutboxDao = database.outboxDao()
}
