package com.smssentry.di

import android.content.ContentResolver
import android.content.Context
import com.smssentry.BuildConfig
import com.smssentry.data.repository.RealSMSSentryAI
import com.smssentry.data.repository.SmsRepository
import com.smssentry.data.util.ContactResolver
import com.smssentry.deepcheck.data.*
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
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
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

    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(impl: DefaultDispatcherProvider): DispatcherProvider

    companion object {
        @Provides
        @Singleton
        fun provideContentResolver(@ApplicationContext context: Context): ContentResolver {
            return context.contentResolver
        }

        @Provides
        @Singleton
        fun provideContactResolver(@ApplicationContext context: Context): ContactResolver {
            return ContactResolver(context)
        }

        @Provides
        @Singleton
        fun provideSmsRepository(
            contentResolver: ContentResolver,
            @ApplicationContext context: Context,
            contactResolver: ContactResolver,
        ): SmsRepository {
            return SmsRepository(contentResolver, context, contactResolver)
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
        fun providePersonalLearningDao(db: DeepCheckDatabase): com.smssentry.learning.data.PersonalLearningDao {
            return db.personalLearningDao()
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
        fun provideOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
        }

        @Provides
        @Singleton
        fun providePrivacyProxyClient(): PrivacyProxyClient {
            return PrivacyProxyClient(BuildConfig.PROXY_URL)
        }

        @Provides
        fun provideLlmInferenceEngine(modelRepository: ModelRepository): LlmInferenceEngine? {
            return if (modelRepository.isModelDownloaded()) {
                modelRepository.getEngine()
            } else {
                null
            }
        }

        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(): CoroutineScope {
            return CoroutineScope(SupervisorJob() + Dispatchers.Default)
        }
    }
}
