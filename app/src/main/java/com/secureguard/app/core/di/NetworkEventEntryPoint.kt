package com.secureguard.app.core.di

import com.secureguard.app.core.database.dao.NetworkEventDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NetworkEventEntryPoint {
    fun networkEventDao(): NetworkEventDao
}
