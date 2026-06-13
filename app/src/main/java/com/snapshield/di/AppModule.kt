package com.snapshield.di

import com.snapshield.data.mock.MockSnapShieldAI
import com.snapshield.domain.service.SnapShieldAI
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSnapShieldAI(): SnapShieldAI {
        return MockSnapShieldAI()
    }
}
