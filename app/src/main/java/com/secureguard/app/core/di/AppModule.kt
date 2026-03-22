package com.secureguard.app.core.di

import android.content.Context
import androidx.room.Room
import com.secureguard.app.core.database.SecureGuardDatabase
import com.secureguard.app.core.database.dao.AppScanDao
import com.secureguard.app.core.database.dao.NetworkEventDao
import com.secureguard.app.data.repository.AppScannerRepositoryImpl
import com.secureguard.app.data.repository.NetworkEventRepositoryImpl
import com.secureguard.app.domain.repository.AppScannerRepository
import com.secureguard.app.domain.repository.NetworkEventRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SecureGuardDatabase {
        return Room.databaseBuilder(
            context,
            SecureGuardDatabase::class.java,
            "secureguard.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideAppScanDao(database: SecureGuardDatabase): AppScanDao = database.appScanDao()

    @Provides
    fun provideNetworkEventDao(database: SecureGuardDatabase): NetworkEventDao = database.networkEventDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindAppScannerRepository(
        repository: AppScannerRepositoryImpl
    ): AppScannerRepository

    @Binds
    @Singleton
    abstract fun bindNetworkEventRepository(
        repository: NetworkEventRepositoryImpl
    ): NetworkEventRepository
}
