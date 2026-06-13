package com.smssentry.di

import android.content.Context
import com.smssentry.data.mock.MockSMSSentryAI
import com.smssentry.deepcheck.ModelDownloadManager
import com.smssentry.deepcheck.ModelManager
import com.smssentry.deepcheck.data.AllowlistDao
import com.smssentry.deepcheck.data.DeepCheckDatabase
import com.smssentry.deepcheck.data.HistoryDao
import com.smssentry.deepcheck.data.OfficialSitesRepository
import com.smssentry.deepcheck.data.ReputationDb
import com.smssentry.deepcheck.proxy.PrivacyProxyClient
import com.smssentry.domain.service.SMSSentryAI
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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

    @Provides
    @Singleton
    fun provideDeepCheckDatabase(@ApplicationContext context: Context): DeepCheckDatabase {
        return DeepCheckDatabase.getInstance(context)
    }

    @Provides
    fun provideAllowlistDao(db: DeepCheckDatabase): AllowlistDao {
        return db.allowlistDao()
    }

    @Provides
    fun provideHistoryDao(db: DeepCheckDatabase): HistoryDao {
        return db.historyDao()
    }

    @Provides
    @Singleton
    fun provideReputationDb(@ApplicationContext context: Context): ReputationDb {
        return try {
            ReputationDb(context)
        } catch (e: Exception) {
            ReputationDb(context)
        }
    }

    @Provides
    @Singleton
    fun provideOfficialSitesRepository(@ApplicationContext context: Context): OfficialSitesRepository {
        return OfficialSitesRepository(context)
    }

    @Provides
    @Singleton
    fun providePrivacyProxyClient(): PrivacyProxyClient {
        return PrivacyProxyClient(null)
    }

    @Provides
    @Singleton
    fun provideModelManager(@ApplicationContext context: Context): ModelManager {
        return ModelManager(context)
    }

    @Provides
    @Singleton
    fun provideModelDownloadManager(@ApplicationContext context: Context): ModelDownloadManager {
        return ModelDownloadManager(context)
    }
}
