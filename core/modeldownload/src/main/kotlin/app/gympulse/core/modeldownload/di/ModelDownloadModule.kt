package app.gympulse.core.modeldownload.di

import android.content.Context
import android.util.Log
import app.gympulse.core.data.ai.ModelDownloadManager
import app.gympulse.core.data.ai.ModelLocator
import app.gympulse.core.modeldownload.ModelConfig
import app.gympulse.core.modeldownload.ModelDownloadWorker
import app.gympulse.core.modeldownload.ModelDownloader
import app.gympulse.core.modeldownload.ModelStorage
import app.gympulse.core.modeldownload.WorkManagerModelDownloadManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ModelDownloadProvidesModule {
    @Provides
    @Singleton
    fun provideModelStorage(@ApplicationContext context: Context): ModelStorage =
        ModelStorage(File(context.filesDir, MODELS_DIR), ModelConfig.CURRENT)

    @Provides
    @Singleton
    fun provideModelDownloader(): ModelDownloader =
        ModelDownloader(
            // Eigener Client, nicht als OkHttpClient gebunden, damit er keine anderen Bindings stört.
            client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                // Großzügig, weil das CDN gegen Ende einer 2-GB-Datei längere Pausen machen kann.
                .readTimeout(120, TimeUnit.SECONDS)
                // Kein Gesamt-Timeout: der Download dauert je nach WLAN viele Minuten.
                .callTimeout(0, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .build(),
            log = { message -> Log.w(ModelDownloadWorker.TAG, message) },
        )

    private const val MODELS_DIR = "models"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ModelDownloadBindsModule {
    @Binds
    abstract fun bindModelLocator(impl: ModelStorage): ModelLocator

    @Binds
    abstract fun bindModelDownloadManager(impl: WorkManagerModelDownloadManager): ModelDownloadManager
}
