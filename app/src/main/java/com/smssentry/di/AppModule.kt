package com.smssentry.di

import android.content.ContentResolver
import android.content.Context
import com.smssentry.BuildConfig
import com.smssentry.data.repository.RealSMSSentryAI
import com.smssentry.data.repository.SmsRepository
import com.smssentry.deepcheck.ModelDownloadManager
import com.smssentry.deepcheck.ModelManager
import com.smssentry.deepcheck.data.AllowlistDao
import com.smssentry.deepcheck.data.DeepCheckDatabase
import com.smssentry.deepcheck.data.HistoryDao
import com.smssentry.deepcheck.data.OfficialSitesRepository
import com.smssentry.deepcheck.data.ReputationDb
import com.smssentry.deepcheck.model.LlmInferenceEngine
import com.smssentry.deepcheck.proxy.PrivacyProxyClient
import com.smssentry.domain.service.SMSSentryAI
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindSMSSentryAI(realAI: RealSMSSentryAI): SMSSentryAI

    companion object {
        @Provides
        @Singleton
        fun provideContentResolver(@ApplicationContext context: Context): ContentResolver {
            return context.contentResolver
        }

        @Provides
        @Singleton
        fun provideSmsRepository(contentResolver: ContentResolver, @ApplicationContext context: Context): SmsRepository {
            return SmsRepository(contentResolver, context)
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
            return ReputationDb(context)
        }

        @Provides
        @Singleton
        fun provideOfficialSitesRepository(@ApplicationContext context: Context): OfficialSitesRepository {
            return OfficialSitesRepository(context)
        }

        @Provides
        @Singleton
        fun providePrivacyProxyClient(): PrivacyProxyClient {
            return PrivacyProxyClient(BuildConfig.PROXY_URL)
        }

        @Provides
        @Singleton
        fun provideModelManager(@ApplicationContext context: Context): ModelManager {
            return ModelManager(context)
        }

        @Provides
        fun provideLlmInferenceEngine(modelManager: ModelManager): LlmInferenceEngine? {
            return modelManager.getLlmEngine()
        }

        @Provides
        @Singleton
        fun provideModelDownloadManager(@ApplicationContext context: Context): ModelDownloadManager {
            return ModelDownloadManager(context)
        }

        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(): CoroutineScope {
            return CoroutineScope(SupervisorJob() + Dispatchers.Default)
        }
    }
}
