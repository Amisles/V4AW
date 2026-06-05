package org.amisles.v4aw.data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import org.amisles.v4aw.download.DownloadChunkDao
import org.amisles.v4aw.download.DownloadDao
import org.amisles.v4aw.data.local.database.AppDatabase
import org.amisles.v4aw.data.local.dao.HistoryDao
import org.amisles.v4aw.data.local.dao.SiteRuleDao
import org.amisles.v4aw.data.repository.HistoryRepositoryImpl
import org.amisles.v4aw.data.repository.SiteRuleRepositoryImpl
import org.amisles.v4aw.data.repository.VideoRepositoryImpl
import org.amisles.v4aw.domain.repository.HistoryRepository
import org.amisles.v4aw.domain.repository.SiteRuleRepository
import org.amisles.v4aw.domain.repository.VideoRepository
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideHistoryDao(database: AppDatabase): HistoryDao {
        return database.historyDao()
    }

    @Provides
    @Singleton
    fun provideDownloadDao(database: AppDatabase): DownloadDao {
        return database.downloadDao()
    }

    @Provides
    @Singleton
    fun provideDownloadChunkDao(database: AppDatabase): DownloadChunkDao {
        return database.downloadChunkDao()
    }

    @Provides
    @Singleton
    fun provideSiteRuleDao(database: AppDatabase): SiteRuleDao {
        return database.siteRuleDao()
    }

    @Provides
    @Singleton
    fun provideVideoRepository(repository: VideoRepositoryImpl): VideoRepository {
        return repository
    }

    @Provides
    @Singleton
    fun provideHistoryRepository(repository: HistoryRepositoryImpl): HistoryRepository {
        return repository
    }

    @Provides
    @Singleton
    fun provideSiteRuleRepository(repository: SiteRuleRepositoryImpl): SiteRuleRepository {
        return repository
    }
}
