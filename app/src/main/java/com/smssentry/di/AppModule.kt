package com.smssentry.di

import android.app.Application
import android.content.Context
import com.smssentry.data.mock.MockSMSSentryAI
import com.smssentry.data.repository.SmsRepository
import com.smssentry.domain.service.SMSSentryAI
import com.smssentry.ml.SmsClassifierModel
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
    fun provideContext(application: Application): Context = application

    @Provides
    @Singleton
    fun provideSmsClassifierModel(context: Context): SmsClassifierModel {
        return SmsClassifierModel(context).also { it.initialize() }
    }

    @Provides
    @Singleton
    fun provideSmsRepository(context: Context): SmsRepository {
        return SmsRepository(context)
    }

    @Provides
    @Singleton
    fun provideSMSSentryAI(): SMSSentryAI {
        return MockSMSSentryAI()
    }
}