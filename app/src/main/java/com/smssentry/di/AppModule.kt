package com.smssentry.di

import com.smssentry.data.mock.MockSMSSentryAI
import com.smssentry.domain.service.SMSSentryAI
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
    fun provideSMSSentryAI(): SMSSentryAI {
        return MockSMSSentryAI()
    }
}
