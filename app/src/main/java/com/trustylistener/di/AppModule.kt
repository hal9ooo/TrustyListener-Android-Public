package com.trustylistener.di

import android.content.Context
import com.trustylistener.data.local.audio.AudioRecorder
import com.trustylistener.data.local.database.AppDatabase
import com.trustylistener.data.ml.YAMNetClassifier
import com.trustylistener.data.repository.AudioRepositoryImpl
import com.trustylistener.data.repository.LogRepositoryImpl
import com.trustylistener.domain.repository.AudioRepository
import com.trustylistener.domain.repository.LogRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module for dependency injection
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideAudioRecorder(@ApplicationContext context: Context): AudioRecorder {
        return AudioRecorder(context)
    }

    @Provides
    @Singleton
    fun provideYAMNetClassifier(@ApplicationContext context: Context): YAMNetClassifier {
        return YAMNetClassifier(context)
    }

    @Provides
    @Singleton
    fun provideAudioRepository(
        @ApplicationContext context: Context,
        audioRecorder: AudioRecorder,
        classifier: YAMNetClassifier
    ): AudioRepository {
        return AudioRepositoryImpl(context, audioRecorder, classifier)
    }

    @Provides
    @Singleton
    fun provideLogRepository(database: AppDatabase): LogRepository {
        return LogRepositoryImpl(database)
    }
}
